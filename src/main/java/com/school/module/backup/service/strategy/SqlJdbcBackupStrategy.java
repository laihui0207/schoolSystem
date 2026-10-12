package com.school.module.backup.service.strategy;

import com.school.module.backup.config.BackupDataSourceInfo;
import com.school.module.backup.config.BackupProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.zip.GZIPOutputStream;

import static com.school.module.backup.service.strategy.BackupSqlFormat.END_MARKER;
import static com.school.module.backup.service.strategy.BackupSqlFormat.HEADER_LINE;
import static com.school.module.backup.service.strategy.BackupSqlFormat.SEQUENCE_MARKER;
import static com.school.module.backup.service.strategy.BackupSqlFormat.TABLES_PREFIX;
import static com.school.module.backup.service.strategy.BackupSqlFormat.TABLE_MARKER;
import static com.school.module.backup.service.strategy.BackupSqlFormat.literal;
import static com.school.module.backup.service.strategy.BackupSqlFormat.quoteIdent;

/**
 * 纯 JDBC 兜底策略：不依赖任何外部二进制，读 information_schema 列出 public 表，逐表 SELECT * 生成 INSERT 写 .sql.gz
 * <p>
 * ponytail: 整个导出包在一个 REPEATABLE READ 只读事务里——PG 下这是跨表一致快照，且关闭自动提交后
 * fetchSize 才会真正生效（autocommit 下 PG 驱动会一次性拉全表，720k 行的 score 表会把堆打爆）。
 * ponytail: 走 JdbcTemplate 而非 MyBatis，天然绕过租户拦截器——备份必须是全库，与当前登录人无关。
 *
 * @author School System
 */
@Component
@RequiredArgsConstructor
public class SqlJdbcBackupStrategy implements BackupStrategy {

    /** 每条 INSERT 携带的行数 */
    public static final int BATCH_SIZE = 1000;

    /** 永不导出：迁移历史由 Flyway 自己管理 */
    public static final Set<String> SKIPPED_TABLES = Set.of("flyway_schema_history");

    public static final String TABLES_SQL =
            "SELECT table_name FROM information_schema.tables "
                    + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' ORDER BY table_name";

    public static final String SERIAL_COLUMNS_SQL =
            "SELECT table_name, column_name FROM information_schema.columns "
                    + "WHERE table_schema = 'public' AND column_default LIKE 'nextval(%' ORDER BY table_name, column_name";

    private final JdbcTemplate jdbcTemplate;
    private final PlatformTransactionManager transactionManager;
    private final BackupDataSourceInfo dataSourceInfo;

    @Override
    public String mode() {
        return BackupProperties.MODE_SQL;
    }

    @Override
    public String fileExtension() {
        return BackupSqlFormat.FILE_EXTENSION;
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public String dump(Path target) throws IOException {
        List<String> tables = listTables();
        List<String[]> serialColumns = jdbcTemplate.query(SERIAL_COLUMNS_SQL,
                (rs, i) -> new String[]{rs.getString(1), rs.getString(2)});

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        long[] totalRows = {0};
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                new GZIPOutputStream(Files.newOutputStream(target)), StandardCharsets.UTF_8), 1 << 16)) {
            writeHeader(writer, tables);
            tx.executeWithoutResult(status -> {
                try {
                    for (String table : tables) {
                        totalRows[0] += dumpTable(table, writer);
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
            writeSequences(writer, serialColumns, tables);
            writer.write(END_MARKER);
            writer.write('\n');
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
        return tables.size() + " 张表, " + totalRows[0] + " 行";
    }

    /**
     * public 下的基础表（去掉 Flyway 历史表），字母序
     */
    public List<String> listTables() {
        return jdbcTemplate.queryForList(TABLES_SQL, String.class).stream()
                .filter(t -> !SKIPPED_TABLES.contains(t))
                .toList();
    }

    private void writeHeader(Writer writer, List<String> tables) throws IOException {
        writer.write(HEADER_LINE + "\n");
        writer.write("-- format: " + BackupSqlFormat.FORMAT_VERSION + "\n");
        writer.write("-- database: " + dataSourceInfo.getDatabase() + "\n");
        writer.write("-- generated_at: " + LocalDateTime.now() + "\n");
        writer.write(TABLES_PREFIX + String.join(",", tables) + "\n");
    }

    private long dumpTable(String table, Writer writer) throws IOException {
        writer.write(TABLE_MARKER + table + "\n");
        String sql = "SELECT * FROM " + quoteIdent(table);
        // ponytail: TYPE_FORWARD_ONLY + fetchSize 让 PG 驱动用游标分批取，配合外层事务实现流式导出
        PreparedStatementCreator psc = con -> {
            PreparedStatement ps = con.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            ps.setFetchSize(BATCH_SIZE);
            return ps;
        };
        ResultSetExtractor<Long> extractor = rs -> {
            try {
                return writeRows(table, rs, writer);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        Long rows = jdbcTemplate.query(psc, extractor);
        return rows == null ? 0 : rows;
    }

    /**
     * 把结果集按 BATCH_SIZE 行一条 INSERT 写出；每条语句恰好一行
     */
    static long writeRows(String table, ResultSet rs, Writer writer) throws SQLException, IOException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();
        StringBuilder prefix = new StringBuilder("INSERT INTO ").append(quoteIdent(table)).append(" (");
        for (int i = 1; i <= columnCount; i++) {
            if (i > 1) {
                prefix.append(',');
            }
            prefix.append(quoteIdent(meta.getColumnLabel(i)));
        }
        prefix.append(") VALUES ");

        StringBuilder sb = new StringBuilder();
        int inBatch = 0;
        long total = 0;
        while (rs.next()) {
            if (inBatch == 0) {
                sb.append(prefix);
            } else {
                sb.append(',');
            }
            sb.append('(');
            for (int i = 1; i <= columnCount; i++) {
                if (i > 1) {
                    sb.append(',');
                }
                sb.append(literal(rs.getObject(i)));
            }
            sb.append(')');
            inBatch++;
            total++;
            if (inBatch == BATCH_SIZE) {
                flushStatement(sb, writer);
                inBatch = 0;
            }
        }
        if (inBatch > 0) {
            flushStatement(sb, writer);
        }
        return total;
    }

    private static void flushStatement(StringBuilder sb, Writer writer) throws IOException {
        sb.append(";\n");
        writer.write(sb.toString());
        sb.setLength(0);
    }

    private void writeSequences(Writer writer, List<String[]> serialColumns, List<String> tables) throws IOException {
        Set<String> exported = Set.copyOf(tables);
        for (String[] tc : serialColumns) {
            if (!exported.contains(tc[0])) {
                continue;
            }
            writer.write(SEQUENCE_MARKER + tc[0] + "\n");
            writer.write(BackupSqlFormat.setvalStatement(tc[0], tc[1]));
            writer.write('\n');
        }
    }

}
