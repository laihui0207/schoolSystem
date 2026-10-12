package com.school.module.backup.service;

import com.school.module.backup.service.strategy.BackupSqlFormat;
import com.school.module.backup.service.strategy.SqlJdbcBackupStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPInputStream;

import static com.school.module.backup.service.strategy.BackupSqlFormat.HEADER_LINE;
import static com.school.module.backup.service.strategy.BackupSqlFormat.SEQUENCE_MARKER;
import static com.school.module.backup.service.strategy.BackupSqlFormat.TABLES_PREFIX;
import static com.school.module.backup.service.strategy.BackupSqlFormat.TABLE_MARKER;

/**
 * 在线恢复：把本应用生成的 .sql.gz 重新装载进当前库
 * <p>
 * ponytail: 只 TRUNCATE「备份文件里有 ∩ 当前库里有 ∩ 不在排除名单」的表——备份文件里没有的表不动
 * （可能是之后迁移新增的，清空会把字典/配置种子一起带走），当前库里没有的表整块跳过（否则 INSERT 报错整笔回滚）。
 * ponytail: sys_backup / sys_operation_log 永不恢复：恢复本身要写审计日志和备份记录，
 * 被恢复的旧数据覆盖后「谁在什么时候恢复的」就查不到了。
 *
 * @author School System
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BackupRestoreExecutor {

    /** 永不 TRUNCATE / 装载的表 */
    public static final Set<String> EXCLUDED_TABLES = Set.of("flyway_schema_history", "sys_backup", "sys_operation_log");

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;

    /**
     * 恢复结果
     */
    public record Outcome(List<String> restoredTables, List<String> skippedTables, int executedStatements) {
    }

    /**
     * 校验是否本应用生成的 sql 备份：gzip 魔数 1F 8B + 解压后首行为 HEADER_LINE
     *
     * @return null 合法；否则返回可读的拒绝原因
     */
    public static String validateSqlBackup(Path file) {
        try (InputStream raw = Files.newInputStream(file)) {
            int b1 = raw.read();
            int b2 = raw.read();
            if (b1 != 0x1f || b2 != 0x8b) {
                return "不是 gzip 文件";
            }
        } catch (IOException e) {
            return "文件读取失败: " + e.getMessage();
        }
        try (BufferedReader reader = open(file)) {
            String first = reader.readLine();
            if (first == null || !first.trim().equals(HEADER_LINE)) {
                return "缺少首行标记「" + HEADER_LINE + "」，不是本系统生成的备份";
            }
            return null;
        } catch (IOException e) {
            return "gzip 解压失败: " + e.getMessage();
        }
    }

    /**
     * 执行恢复（单事务：TRUNCATE 目标表 → 逐条执行文件里属于目标表的语句）
     */
    public Outcome restore(Path file) throws IOException {
        String invalid = validateSqlBackup(file);
        if (invalid != null) {
            throw new IllegalStateException(invalid);
        }
        List<String> backedUp = readTableList(file);
        Set<String> existing = new HashSet<>(jdbcTemplate.queryForList(SqlJdbcBackupStrategy.TABLES_SQL, String.class));

        List<String> toRestore = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (String table : backedUp) {
            if (existing.contains(table) && !EXCLUDED_TABLES.contains(table)) {
                toRestore.add(table);
            } else {
                skipped.add(table);
            }
        }
        if (toRestore.isEmpty()) {
            throw new IllegalStateException("备份文件里没有任何可恢复的业务表");
        }
        Set<String> allowed = Set.copyOf(toRestore);

        int[] executed = {0};
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            tx.executeWithoutResult(status -> {
                jdbcTemplate.execute("TRUNCATE TABLE " + BackupSqlFormat.joinIdents(toRestore));
                executed[0]++;
                try (BufferedReader reader = open(file)) {
                    executed[0] += executeStatements(reader, allowed);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        log.info("备份恢复完成: {} 张表, {} 条语句, 跳过 {}", toRestore.size(), executed[0], skipped);
        return new Outcome(toRestore, skipped, executed[0]);
    }

    /**
     * 按行执行：注释行切换「当前表」，其它行是语句，只执行属于目标表的
     */
    private int executeStatements(BufferedReader reader, Set<String> allowed) throws IOException {
        int count = 0;
        String current = null;
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            if (line.startsWith(TABLE_MARKER)) {
                current = line.substring(TABLE_MARKER.length()).trim();
                continue;
            }
            if (line.startsWith(SEQUENCE_MARKER)) {
                current = line.substring(SEQUENCE_MARKER.length()).trim();
                continue;
            }
            if (line.startsWith("--")) {
                continue;
            }
            if (current == null || !allowed.contains(current)) {
                continue;
            }
            jdbcTemplate.execute(line);
            count++;
        }
        return count;
    }

    /**
     * 读文件头的 "-- tables: a,b,c" 行
     */
    static List<String> readTableList(Path file) throws IOException {
        try (BufferedReader reader = open(file)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith(TABLES_PREFIX)) {
                    String csv = line.substring(TABLES_PREFIX.length()).trim();
                    return csv.isEmpty() ? List.of() : Arrays.stream(csv.split(",")).map(String::trim).toList();
                }
                if (!line.startsWith("--")) {
                    break;
                }
            }
        }
        throw new IllegalStateException("备份文件缺少表清单行「" + TABLES_PREFIX.trim() + "」");
    }

    private static BufferedReader open(Path file) throws IOException {
        return new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(file), 1 << 16), StandardCharsets.UTF_8), 1 << 16);
    }

}
