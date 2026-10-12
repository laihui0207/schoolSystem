package com.school.module.backup.service.strategy;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * 本应用 .sql.gz 备份的文件格式（导出与恢复共用，改一处必须同时看另一处）
 * <p>
 * 格式约定（恢复端是按行解析的，所以每条语句必须恰好占一行）：
 * <pre>
 * -- school-system sql backup          ← 首行标记，上传校验 / 恢复前校验都认这一行
 * -- format: 1
 * -- database: school_system
 * -- generated_at: 2026-10-12T10:00:00
 * -- tables: school,sys_user,...        ← 恢复端据此决定 TRUNCATE 哪些表
 * -- table: school                      ← 切换当前表；其后的语句都属于该表
 * INSERT INTO "school" ("id",...) VALUES (1,E'x',...),(2,...);   ← 每 1000 行一条
 * -- sequence: school                   ← 同样切换当前表，便于恢复端按表过滤
 * SELECT setval(pg_get_serial_sequence('"school"','id'), COALESCE((SELECT MAX("id") FROM "school"),0)+1, false);
 * -- end
 * </pre>
 * ponytail: 字符串一律用 E'...' 转义串并把 \n \r \t \ ' 全部转义，这样任何业务数据都不会在输出里产生换行，
 * 恢复端才能安全地按行切语句，不必实现一个完整的 SQL 词法分析器。
 *
 * @author School System
 */
public final class BackupSqlFormat {

    public static final String HEADER_LINE = "-- school-system sql backup";
    public static final String FORMAT_VERSION = "1";
    public static final String TABLES_PREFIX = "-- tables: ";
    public static final String TABLE_MARKER = "-- table: ";
    public static final String SEQUENCE_MARKER = "-- sequence: ";
    public static final String END_MARKER = "-- end";
    public static final String FILE_EXTENSION = ".sql.gz";

    private BackupSqlFormat() {
    }

    /**
     * 是否本应用生成的可在线恢复的备份文件（按扩展名判断；内容在恢复时再验首行）
     */
    public static boolean isSqlBackupFile(String fileName) {
        return fileName != null && fileName.endsWith(FILE_EXTENSION);
    }

    /**
     * 标识符加双引号（表名 / 列名），内部双引号加倍
     */
    public static String quoteIdent(String name) {
        return "\"" + name.replace("\"", "\"\"") + "\"";
    }

    public static String joinIdents(Collection<String> names) {
        return names.stream().map(BackupSqlFormat::quoteIdent).collect(Collectors.joining(","));
    }

    /**
     * 把 JDBC 取出的任意值渲染为 SQL 字面量
     */
    public static String literal(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Boolean b) {
            return b ? "TRUE" : "FALSE";
        }
        if (value instanceof BigDecimal d) {
            return d.toPlainString();
        }
        if (value instanceof Double d && (d.isNaN() || d.isInfinite())) {
            return escape(d.toString());
        }
        if (value instanceof Float f && (f.isNaN() || f.isInfinite())) {
            return escape(f.toString());
        }
        if (value instanceof Number n) {
            return n.toString();
        }
        if (value instanceof byte[] bytes) {
            // ponytail: decode(hex) 与 standard_conforming_strings 开关无关，比 '\x..' 字面量更稳
            return "decode('" + toHex(bytes) + "','hex')";
        }
        // 时间、UUID、json、数组（PGobject/PgArray 的 toString 就是 PG 文本形式）统统走字符串，由列类型隐式转换
        return escape(String.valueOf(value));
    }

    /**
     * E'...' 转义字符串字面量：换行/回车/制表/反斜杠/单引号全部转义，保证结果不含原始换行
     */
    public static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8).append("E'");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\'' -> sb.append("''");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\0' -> { /* PG text 不允许 NUL，丢弃 */ }
                default -> sb.append(c);
            }
        }
        return sb.append('\'').toString();
    }

    /**
     * 恢复序列的语句：取当前最大值 + 1 作为下一个值
     */
    public static String setvalStatement(String table, String column) {
        String ident = quoteIdent(table);
        return "SELECT setval(pg_get_serial_sequence('" + ident.replace("'", "''") + "', '" + column.replace("'", "''")
                + "'), COALESCE((SELECT MAX(" + quoteIdent(column) + ") FROM " + ident + "), 0) + 1, false);";
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }

}
