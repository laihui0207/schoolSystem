package com.school.module.student.roster;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;

/**
 * 名册 Excel 单元格取值解析
 * <p>
 * 所有列都按 String 读入再在这里解析：EasyExcel 对日期/数字列的类型转换失败会直接中断整次读取，
 * 用户只能看到一条含类名的异常；按 String 读入后能逐行报出「第 N 行 出生日期格式不正确」。
 * 解析失败抛 {@link IllegalArgumentException}，由调用方按行收集进失败明细。
 *
 * @author School System
 */
public final class RosterCells {

    /**
     * 批量开户统一初始密码（与 seed 账号一致）
     */
    public static final String DEFAULT_PASSWORD = "123456";

    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd"),
            DateTimeFormatter.ofPattern("yyyy-M-d"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy/M/d"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            DateTimeFormatter.ofPattern("yyyy.M.d"),
            DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA),
            DateTimeFormatter.ofPattern("yyyyMMdd"));

    // ponytail: Excel 1900 日期系统的序列号零点（含 1900-02-29 历史 bug 后等价于 1899-12-30）
    private static final LocalDate EXCEL_EPOCH = LocalDate.of(1899, 12, 30);

    private RosterCells() {
    }

    /**
     * 去首尾空白，空串归一化为 null
     */
    public static String trimToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 学号/工号：去空白，并去掉数字单元格带出的 ".0" 尾巴
     * <p>
     * ponytail: EasyExcel 把数字单元格 123 读成 "123.0"（BigDecimal.valueOf(double) 的字符串形态），
     * 不处理的话短数字学号会以 "123.0" 入库、且与库内 "123" 比对不上
     */
    public static String normalizeCode(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        return value.matches("\\d+\\.0") ? value.substring(0, value.length() - 2) : value;
    }

    /**
     * 性别：男/1 → 1，女/2 → 2，空 → null
     */
    public static Integer parseGender(String raw) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        return switch (value) {
            // ponytail: "1.0" 是 Excel 数字单元格被按 String 读出的形态
            case "男", "1", "1.0", "M", "m" -> 1;
            case "女", "2", "2.0", "F", "f" -> 2;
            default -> throw new IllegalArgumentException("性别只能填「男」或「女」");
        };
    }

    /**
     * 是/否：是/1/Y/true → true，空/否/0/N/false → false
     */
    public static boolean parseYesNo(String raw, String columnLabel) {
        String value = trimToNull(raw);
        if (value == null) {
            return false;
        }
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "是", "1", "1.0", "y", "yes", "true" -> true;
            case "否", "0", "0.0", "n", "no", "false" -> false;
            default -> throw new IllegalArgumentException(columnLabel + "只能填「是」或「否」");
        };
    }

    /**
     * 日期：兼容 2010-01-01 / 2010/1/1 / 2010年1月1日 / 20100101 / Excel 序列号（如 40179）
     */
    public static LocalDate parseDate(String raw, String columnLabel) {
        String value = trimToNull(raw);
        if (value == null) {
            return null;
        }
        // 日期单元格带时间部分（"2010-01-01 00:00:00"）时只取日期段
        int space = value.indexOf(' ');
        String datePart = space > 0 ? value.substring(0, space) : value;

        LocalDate bySerial = fromExcelSerial(datePart);
        if (bySerial != null) {
            return bySerial;
        }
        for (DateTimeFormatter formatter : DATE_FORMATS) {
            try {
                return LocalDate.parse(datePart, formatter);
            } catch (DateTimeParseException ignored) {
                // 尝试下一种格式
            }
        }
        throw new IllegalArgumentException(columnLabel + "格式不正确，请填写如 2010-01-01");
    }

    /**
     * 5 位以内的纯数字（可带 .0）视为 Excel 日期序列号；8 位数字留给 yyyyMMdd
     */
    private static LocalDate fromExcelSerial(String value) {
        String digits = value.endsWith(".0") ? value.substring(0, value.length() - 2) : value;
        if (!digits.matches("\\d{1,5}")) {
            return null;
        }
        long serial = Long.parseLong(digits);
        if (serial < 1) {
            return null;
        }
        return EXCEL_EPOCH.plusDays(serial);
    }

}
