package com.school.module.backup.service;

import com.school.common.exception.BusinessException;
import com.school.module.backup.config.BackupDataSourceInfo;
import com.school.module.backup.config.BackupProperties;
import com.school.module.backup.dto.BackupRestoreRequest;
import com.school.module.backup.dto.BackupRestoreResult;
import com.school.module.backup.entity.BackupRecord;
import com.school.module.backup.mapper.BackupRecordMapper;
import com.school.module.backup.service.strategy.BackupSqlFormat;
import com.school.module.backup.service.strategy.BackupStrategyResolver;
import com.school.module.backup.service.strategy.PgDumpBackupStrategy;
import com.school.module.backup.service.strategy.SqlJdbcBackupStrategy;
import com.school.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * BackupService 单元测试：sql 模式的生成格式（转义 / 分批 / 序列）、策略探测回退、失败落库、恢复流程、输入校验
 * <p>
 * JdbcTemplate / Mapper / 事务管理器全部 Mockito 桩；文件写进 @TempDir。
 * 事务管理器桩的 getTransaction 返回 null，TransactionTemplate 对此容忍（commit(null) 是空操作）。
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("数据备份服务测试")
class BackupServiceTest {

    @TempDir
    Path tempDir;

    @Mock
    private BackupRecordMapper backupRecordMapper;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    @Mock
    private ResultSet resultSet;

    @Mock
    private ResultSetMetaData metaData;

    private BackupProperties properties;
    private BackupDataSourceInfo dataSourceInfo;
    private BackupServiceImpl backupService;

    /** 模拟自增主键 */
    private final AtomicLong idSequence = new AtomicLong(100);
    /** 捕获 insert 进来的实体，供断言 */
    private final List<BackupRecord> inserted = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TestAuth.asSuperAdmin();

        properties = new BackupProperties();
        properties.setDir(tempDir.resolve("backups").toString());
        properties.setMode(BackupProperties.MODE_SQL);
        properties.setKeepCount(30);
        // ponytail: 指向一个不存在的文件，auto 模式下 pg_dump 探测必然失败，用来验证回退到 sql
        properties.setPgDumpPath(tempDir.resolve("no-such-pg_dump").toString());

        dataSourceInfo = new BackupDataSourceInfo("jdbc:postgresql://db.example:5433/school_system?ssl=false", "school_admin", "secret");

        SqlJdbcBackupStrategy sqlStrategy = new SqlJdbcBackupStrategy(jdbcTemplate, transactionManager, dataSourceInfo);
        PgDumpBackupStrategy pgDumpStrategy = new PgDumpBackupStrategy(properties, dataSourceInfo);
        BackupStrategyResolver resolver = new BackupStrategyResolver(List.of(pgDumpStrategy, sqlStrategy));
        BackupRestoreExecutor restoreExecutor = new BackupRestoreExecutor(jdbcTemplate, transactionManager);
        backupService = new BackupServiceImpl(backupRecordMapper, properties, dataSourceInfo, resolver, restoreExecutor);

        doAnswer(inv -> {
            BackupRecord r = inv.getArgument(0);
            r.setId(idSequence.incrementAndGet());
            inserted.add(r);
            return 1;
        }).when(backupRecordMapper).insert(any(BackupRecord.class));
        when(backupRecordMapper.updateById(any(BackupRecord.class))).thenReturn(1);
        when(backupRecordMapper.selectList(any())).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    // ------------------------------------------------------------------
    // 数据源解析
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该解析出 host/port/db_当 JDBC URL 带参数")
    void shouldParseDataSource_WhenJdbcUrlHasParams_ThenHostPortDb() {
        assertThat(dataSourceInfo.getHost()).isEqualTo("db.example");
        assertThat(dataSourceInfo.getPort()).isEqualTo(5433);
        assertThat(dataSourceInfo.getDatabase()).isEqualTo("school_system");
        assertThat(dataSourceInfo.getUsername()).isEqualTo("school_admin");
    }

    @Test
    @DisplayName("应该用默认端口_当 URL 不带端口")
    void shouldUseDefaultPort_WhenUrlHasNoPort_Then5432() {
        BackupDataSourceInfo info = new BackupDataSourceInfo("jdbc:postgresql://localhost/db1", "u", "p");
        assertThat(info.getPort()).isEqualTo(5432);
        assertThat(info.getDatabase()).isEqualTo("db1");
    }

    // ------------------------------------------------------------------
    // SQL 字面量转义
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该正确转义字面量_当值含引号换行反斜杠")
    void shouldEscapeLiteral_WhenSpecialChars_ThenSingleLineEString() {
        assertThat(BackupSqlFormat.literal(null)).isEqualTo("NULL");
        assertThat(BackupSqlFormat.literal(true)).isEqualTo("TRUE");
        assertThat(BackupSqlFormat.literal(42)).isEqualTo("42");
        assertThat(BackupSqlFormat.literal(new BigDecimal("1E+3"))).isEqualTo("1000");
        assertThat(BackupSqlFormat.literal("it's")).isEqualTo("E'it''s'");
        assertThat(BackupSqlFormat.literal("a\\b")).isEqualTo("E'a\\\\b'");
        assertThat(BackupSqlFormat.literal("line1\nline2\r\tx")).isEqualTo("E'line1\\nline2\\r\\tx'");
        assertThat(BackupSqlFormat.literal(new byte[]{0x0a, (byte) 0xff})).isEqualTo("decode('0aff','hex')");
        assertThat(BackupSqlFormat.literal(Timestamp.valueOf("2024-01-02 03:04:05"))).isEqualTo("E'2024-01-02 03:04:05.0'");
        assertThat(BackupSqlFormat.literal("x\ny")).doesNotContain("\n");
    }

    @Test
    @DisplayName("应该加倍双引号_当标识符含双引号")
    void shouldQuoteIdent_WhenNameHasQuote_ThenDoubled() {
        assertThat(BackupSqlFormat.quoteIdent("class")).isEqualTo("\"class\"");
        assertThat(BackupSqlFormat.quoteIdent("we\"ird")).isEqualTo("\"we\"\"ird\"");
    }

    // ------------------------------------------------------------------
    // sql 模式备份：格式 / 分批 / 跳过 flyway / 序列
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该生成分批 INSERT 并跳过 flyway 表_当 sql 模式备份 1500 行")
    void shouldWriteBatchedInserts_WhenSqlModeWith1500Rows_ThenTwoStatements() throws Exception {
        stubTables(List.of("flyway_schema_history", "score", "school"));
        stubSerialColumns(List.of(new String[]{"school", "id"}, new String[]{"flyway_schema_history", "installed_rank"}));
        stubRows("school", 1500);
        stubRows("score", 0);

        BackupRecord record = backupService.runBackup("admin");

        assertThat(record.getStatus()).isEqualTo(BackupRecord.STATUS_SUCCESS);
        assertThat(record.getBackupType()).isEqualTo(BackupRecord.TYPE_MANUAL);
        assertThat(record.getBackupMode()).isEqualTo("sql");
        assertThat(record.getFileName()).startsWith("backup_").endsWith(".sql.gz").contains("_" + record.getId() + ".");
        assertThat(record.getFileSize()).isPositive();
        assertThat(record.getFinishedAt()).isNotNull();
        assertThat(record.getMessage()).contains("2 张表").contains("1500 行");

        List<String> lines = readGzLines(Path.of(record.getFilePath()));
        assertThat(lines.get(0)).isEqualTo(BackupSqlFormat.HEADER_LINE);
        assertThat(lines).contains("-- tables: score,school");
        assertThat(lines).doesNotContain("-- table: flyway_schema_history");
        assertThat(lines).contains("-- table: score", "-- table: school", "-- sequence: school", "-- end");
        assertThat(lines).noneMatch(l -> l.contains("flyway_schema_history"));

        List<String> inserts = lines.stream().filter(l -> l.startsWith("INSERT INTO \"school\"")).toList();
        assertThat(inserts).hasSize(2);
        assertThat(countRows(inserts.get(0))).isEqualTo(1000);
        assertThat(countRows(inserts.get(1))).isEqualTo(500);
        assertThat(inserts.get(0)).startsWith("INSERT INTO \"school\" (\"id\",\"name\",\"remark\") VALUES (")
                .endsWith(");")
                .contains("(1,E'school 1',E'it''s\\nmulti')");
        assertThat(lines).contains(BackupSqlFormat.setvalStatement("school", "id"));

        verify(backupRecordMapper).insert(any(BackupRecord.class));
        verify(backupRecordMapper).updateById(any(BackupRecord.class));
        verify(transactionManager).commit(isNull());
    }

    @Test
    @DisplayName("应该记为定时类型_当触发人为 job")
    void shouldMarkScheduled_WhenTriggeredByJob_ThenType2() throws Exception {
        stubTables(List.of("school"));
        stubSerialColumns(List.of());
        stubRows("school", 1);

        BackupRecord record = backupService.runBackup(BackupService.TRIGGER_JOB);

        assertThat(record.getBackupType()).isEqualTo(BackupRecord.TYPE_SCHEDULED);
        assertThat(record.getTriggeredBy()).isEqualTo("job");
        assertThat(record.getStatus()).isEqualTo(BackupRecord.STATUS_SUCCESS);
    }

    @Test
    @DisplayName("应该回退到 sql_当 auto 模式且 pg_dump 不存在")
    void shouldFallbackToSql_WhenAutoAndPgDumpMissing_ThenSqlMode() throws Exception {
        properties.setMode(BackupProperties.MODE_AUTO);
        stubTables(List.of("school"));
        stubSerialColumns(List.of());
        stubRows("school", 3);

        BackupRecord record = backupService.runBackup("admin");

        assertThat(record.getStatus()).isEqualTo(BackupRecord.STATUS_SUCCESS);
        assertThat(record.getBackupMode()).isEqualTo("sql");
        assertThat(Files.exists(Path.of(record.getFilePath()))).isTrue();
    }

    @Test
    @DisplayName("应该落 status=2 并删半成品_当显式 pg_dump 模式而可执行文件不存在")
    void shouldRecordFailure_WhenPgDumpModeUnavailable_ThenStatusFailed() {
        properties.setMode(BackupProperties.MODE_PG_DUMP);

        BackupRecord record = backupService.runBackup("admin");

        assertThat(record.getStatus()).isEqualTo(BackupRecord.STATUS_FAILED);
        assertThat(record.getMessage()).isNotBlank();
        assertThat(record.getFinishedAt()).isNotNull();
        assertThat(record.getFileName()).endsWith(".dump");
        assertThat(Files.exists(Path.of(record.getFilePath()))).isFalse();
        verify(backupRecordMapper).updateById(any(BackupRecord.class));
    }

    @Test
    @DisplayName("应该落 status=2_当读表清单抛异常")
    void shouldRecordFailure_WhenJdbcThrows_ThenStatusFailedWithMessage() {
        when(jdbcTemplate.queryForList(SqlJdbcBackupStrategy.TABLES_SQL, String.class))
                .thenThrow(new RuntimeException("connection refused"));

        BackupRecord record = backupService.runBackup("admin");

        assertThat(record.getStatus()).isEqualTo(BackupRecord.STATUS_FAILED);
        assertThat(record.getMessage()).contains("connection refused");
    }

    @Test
    @DisplayName("应该删除最旧的备份_当成功数量超过 keep-count")
    void shouldPruneOldest_WhenExceedKeepCount_ThenDeleteFileAndRecord() throws Exception {
        properties.setKeepCount(2);
        stubTables(List.of("school"));
        stubSerialColumns(List.of());
        stubRows("school", 1);
        Path dir = Files.createDirectories(Path.of(properties.getDir()));
        Path oldFile = Files.writeString(dir.resolve("backup_old_1.sql.gz"), "x");
        BackupRecord newest = BackupRecord.builder().id(300L).status(1).filePath(dir.resolve("n.sql.gz").toString()).build();
        BackupRecord middle = BackupRecord.builder().id(200L).status(1).filePath(dir.resolve("m.sql.gz").toString()).build();
        BackupRecord oldest = BackupRecord.builder().id(1L).status(1).fileName("backup_old_1.sql.gz").filePath(oldFile.toString()).build();
        when(backupRecordMapper.selectList(any())).thenReturn(List.of(newest, middle, oldest));

        backupService.runBackup("admin");

        assertThat(Files.exists(oldFile)).isFalse();
        verify(backupRecordMapper).deleteById(1L);
        verify(backupRecordMapper, never()).deleteById(200L);
        verify(backupRecordMapper, never()).deleteById(300L);
    }

    // ------------------------------------------------------------------
    // 恢复
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该先自动备份再 TRUNCATE 并只执行目标表语句_当恢复合法的 sql 备份")
    void shouldRestore_WhenValidSqlBackup_ThenPreBackupTruncateAndReplay() throws Exception {
        Path dir = Files.createDirectories(Path.of(properties.getDir()));
        Path file = dir.resolve("backup_restore_me.sql.gz");
        writeGz(file, List.of(
                BackupSqlFormat.HEADER_LINE,
                "-- format: 1",
                "-- tables: gone_table,school,sys_backup,sys_operation_log",
                "-- table: gone_table",
                "INSERT INTO \"gone_table\" (\"id\") VALUES (1);",
                "-- table: school",
                "INSERT INTO \"school\" (\"id\",\"name\") VALUES (1,E'A'),(2,E'B');",
                "INSERT INTO \"school\" (\"id\",\"name\") VALUES (3,E'C');",
                "-- table: sys_backup",
                "INSERT INTO \"sys_backup\" (\"id\") VALUES (9);",
                "-- table: sys_operation_log",
                "INSERT INTO \"sys_operation_log\" (\"id\") VALUES (9);",
                "-- sequence: school",
                BackupSqlFormat.setvalStatement("school", "id"),
                "-- end"));
        BackupRecord target = BackupRecord.builder().id(7L).status(1).fileName(file.getFileName().toString())
                .filePath(file.toString()).backupMode("sql").build();
        when(backupRecordMapper.selectById(7L)).thenReturn(target);
        // 恢复前自动备份（sql 模式）需要的桩
        stubTables(List.of("school", "sys_backup", "sys_operation_log"));
        stubSerialColumns(List.of());
        stubRows("school", 1);
        stubRows("sys_backup", 0);
        stubRows("sys_operation_log", 0);

        BackupRestoreResult result = backupService.restore(7L, new BackupRestoreRequest("RESTORE"));

        assertThat(result.getBackupId()).isEqualTo(7L);
        assertThat(result.getPreRestoreBackupId()).isNotNull();
        assertThat(result.getRestoredTables()).containsExactly("school");
        assertThat(result.getSkippedTables()).containsExactly("gone_table", "sys_backup", "sys_operation_log");
        // TRUNCATE + 2 条 INSERT + 1 条 setval
        assertThat(result.getExecutedStatements()).isEqualTo(4);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(4)).execute(sql.capture());
        assertThat(sql.getAllValues().get(0)).isEqualTo("TRUNCATE TABLE \"school\"");
        assertThat(sql.getAllValues()).noneMatch(s -> s.contains("gone_table") || s.contains("sys_backup") || s.contains("sys_operation_log"));
        assertThat(sql.getAllValues().get(3)).startsWith("SELECT setval(");

        // 保险备份记录带标记
        assertThat(inserted).anySatisfy(r -> {
            assertThat(r.getMessage()).startsWith("恢复前自动备份");
            assertThat(r.getStatus()).isEqualTo(BackupRecord.STATUS_SUCCESS);
            assertThat(r.getBackupMode()).isEqualTo("sql");
        });
    }

    @Test
    @DisplayName("应该返回 pg_restore 提示_当恢复 .dump 产物")
    void shouldRejectRestore_WhenDumpFile_ThenPgRestoreHint() throws Exception {
        Path dir = Files.createDirectories(Path.of(properties.getDir()));
        Path file = Files.writeString(dir.resolve("backup_x.dump"), "binary");
        when(backupRecordMapper.selectById(8L)).thenReturn(BackupRecord.builder().id(8L).status(1)
                .fileName("backup_x.dump").filePath(file.toString()).backupMode("pg_dump").build());

        assertThatThrownBy(() -> backupService.restore(8L, new BackupRestoreRequest("RESTORE")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pg_restore -d school_system");
        verify(backupRecordMapper, never()).insert(any(BackupRecord.class));
        verify(jdbcTemplate, never()).execute(anyString());
    }

    @Test
    @DisplayName("应该拒绝恢复_当 confirm 不是 RESTORE")
    void shouldRejectRestore_WhenConfirmMissing_ThenIllegalArgument() {
        assertThatThrownBy(() -> backupService.restore(1L, new BackupRestoreRequest("yes")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> backupService.restore(1L, null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(backupRecordMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("应该拒绝恢复_当备份记录不是成功状态")
    void shouldRejectRestore_WhenBackupNotSuccess_ThenBusinessException() {
        when(backupRecordMapper.selectById(9L)).thenReturn(BackupRecord.builder().id(9L).status(2).build());

        assertThatThrownBy(() -> backupService.restore(9L, new BackupRestoreRequest("RESTORE")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("成功");
    }

    @Test
    @DisplayName("应该拒绝访问_当文件路径不在备份目录内")
    void shouldRejectFile_WhenPathOutsideBackupDir_ThenForbidden() throws Exception {
        Path outside = Files.writeString(tempDir.resolve("outside.sql.gz"), "x");
        when(backupRecordMapper.selectById(10L)).thenReturn(BackupRecord.builder().id(10L).status(1)
                .fileName("outside.sql.gz").filePath(outside.toString()).build());

        assertThatThrownBy(() -> backupService.restore(10L, new BackupRestoreRequest("RESTORE")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("备份目录");
    }

    // ------------------------------------------------------------------
    // 上传校验 / 删除
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该识别非法上传_当不是 gzip 或缺首行标记")
    void shouldValidateUpload_WhenNotGzipOrNoHeader_ThenReason() throws Exception {
        Path notGz = Files.writeString(tempDir.resolve("a.sql.gz"), "plain text");
        assertThat(BackupRestoreExecutor.validateSqlBackup(notGz)).contains("gzip");

        Path wrongHeader = tempDir.resolve("b.sql.gz");
        writeGz(wrongHeader, List.of("-- pg_dump output", "SELECT 1;"));
        assertThat(BackupRestoreExecutor.validateSqlBackup(wrongHeader)).contains("首行标记");

        Path good = tempDir.resolve("c.sql.gz");
        writeGz(good, List.of(BackupSqlFormat.HEADER_LINE, "-- tables: school"));
        assertThat(BackupRestoreExecutor.validateSqlBackup(good)).isNull();
    }

    @Test
    @DisplayName("应该拒绝删除_当备份进行中")
    void shouldRejectDelete_WhenRunning_ThenBusinessException() {
        when(backupRecordMapper.selectById(11L)).thenReturn(BackupRecord.builder().id(11L).status(0).build());

        assertThatThrownBy(() -> backupService.deleteBackup(11L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("进行中");
        verify(backupRecordMapper, never()).deleteById(any(Serializable.class));
    }

    @Test
    @DisplayName("应该删文件并删记录_当删除成功的备份")
    void shouldDeleteFileAndRecord_WhenDeleteSuccessBackup_ThenBoth() throws Exception {
        Path dir = Files.createDirectories(Path.of(properties.getDir()));
        Path file = Files.writeString(dir.resolve("backup_del.sql.gz"), "x");
        when(backupRecordMapper.selectById(12L)).thenReturn(BackupRecord.builder().id(12L).status(1)
                .fileName("backup_del.sql.gz").filePath(file.toString()).build());

        backupService.deleteBackup(12L);

        assertThat(Files.exists(file)).isFalse();
        verify(backupRecordMapper).deleteById(12L);
    }

    @Test
    @DisplayName("应该格式化文件大小_当不同数量级")
    void shouldFormatSize_WhenVariousMagnitudes_ThenHumanReadable() {
        assertThat(BackupFileSupport.humanSize(null)).isEqualTo("-");
        assertThat(BackupFileSupport.humanSize(512L)).isEqualTo("512 B");
        assertThat(BackupFileSupport.humanSize(1536L)).isEqualTo("1.5 KB");
        assertThat(BackupFileSupport.humanSize(5L * 1024 * 1024)).isEqualTo("5.0 MB");
    }

    // ------------------------------------------------------------------
    // 桩工具
    // ------------------------------------------------------------------

    /** stubTables 传入的表（已过滤 flyway），供 stubRows 的 Answer 按调用顺序对应表名 */
    private List<String> stubbedTables = List.of();

    private void stubTables(List<String> tables) {
        stubbedTables = tables.stream().filter(t -> !SqlJdbcBackupStrategy.SKIPPED_TABLES.contains(t)).toList();
        when(jdbcTemplate.queryForList(SqlJdbcBackupStrategy.TABLES_SQL, String.class)).thenReturn(tables);
    }

    @SuppressWarnings("unchecked")
    private void stubSerialColumns(List<String[]> rows) {
        when(jdbcTemplate.query(eq(SqlJdbcBackupStrategy.SERIAL_COLUMNS_SQL), any(RowMapper.class))).thenReturn((List) rows);
    }

    /**
     * 让 SELECT * FROM "table" 返回 rows 行 (id, name, remark)；第 1 行 remark 含引号与换行用于验证转义。
     * 多张表共用同一个 ResultSet 桩，按表名分流行数。
     */
    private final Map<String, Integer> rowCounts = new HashMap<>();

    @SuppressWarnings("unchecked")
    private void stubRows(String table, int rows) throws Exception {
        rowCounts.put(table, rows);
        when(metaData.getColumnCount()).thenReturn(3);
        when(metaData.getColumnLabel(1)).thenReturn("id");
        when(metaData.getColumnLabel(2)).thenReturn("name");
        when(metaData.getColumnLabel(3)).thenReturn("remark");
        when(resultSet.getMetaData()).thenReturn(metaData);

        AtomicInteger cursor = new AtomicInteger();
        AtomicInteger limit = new AtomicInteger();
        when(resultSet.next()).thenAnswer(inv -> cursor.incrementAndGet() <= limit.get());
        when(resultSet.getObject(1)).thenAnswer(inv -> cursor.get());
        when(resultSet.getObject(2)).thenAnswer(inv -> "school " + cursor.get());
        when(resultSet.getObject(3)).thenAnswer(inv -> cursor.get() == 1 ? "it's\nmulti" : null);

        when(jdbcTemplate.query(any(PreparedStatementCreator.class), any(ResultSetExtractor.class))).thenAnswer(inv -> {
            // ponytail: 无法从 PreparedStatementCreator 取回 SQL，用调用顺序对应 stubTables 的表顺序（已过滤 flyway）
            String current = nextTableInOrder();
            cursor.set(0);
            limit.set(rowCounts.getOrDefault(current, 0));
            return ((ResultSetExtractor<Object>) inv.getArgument(1)).extractData(resultSet);
        });
    }

    private final AtomicInteger tableCursor = new AtomicInteger();

    private String nextTableInOrder() {
        // ponytail: 不在 Answer 里再调 mock（queryForList 可能被桩成抛异常），用 stubTables 捕获的列表
        List<String> tables = stubbedTables;
        int idx = tableCursor.getAndIncrement() % Math.max(1, tables.size());
        return tables.isEmpty() ? "" : tables.get(idx);
    }

    private static List<String> readGzLines(Path file) throws Exception {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new GZIPInputStream(Files.newInputStream(file)), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        }
    }

    private static void writeGz(Path file, List<String> lines) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (Writer w = new OutputStreamWriter(new GZIPOutputStream(bytes), StandardCharsets.UTF_8)) {
            for (String line : lines) {
                w.write(line);
                w.write('\n');
            }
        }
        Files.write(file, bytes.toByteArray());
    }

    /** 数 VALUES 后面有多少个 "(" 开头的行元组 */
    private static long countRows(String insert) {
        String values = insert.substring(insert.indexOf(") VALUES ") + ") VALUES ".length());
        return values.chars().filter(c -> c == '(').count();
    }

}
