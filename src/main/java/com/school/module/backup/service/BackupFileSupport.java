package com.school.module.backup.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 备份文件相关的小工具（命名、大小格式化、安全删除）
 *
 * @author School System
 */
public final class BackupFileSupport {

    private static final DateTimeFormatter FILE_TS = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String[] UNITS = {"B", "KB", "MB", "GB", "TB"};

    private BackupFileSupport() {
    }

    /**
     * 备份文件名：backup_20261012_101500_<id>.sql.gz —— 带记录 ID 保证唯一
     */
    public static String backupFileName(Long id, LocalDateTime time, String extension) {
        return "backup_" + FILE_TS.format(time) + "_" + id + extension;
    }

    /**
     * 上传文件名：upload_20261012_101500_<id>.sql.gz
     */
    public static String uploadFileName(Long id, LocalDateTime time, String extension) {
        return "upload_" + FILE_TS.format(time) + "_" + id + extension;
    }

    /**
     * 字节数 → "12.3 MB"
     */
    public static String humanSize(Long bytes) {
        if (bytes == null) {
            return "-";
        }
        double v = bytes;
        int unit = 0;
        while (v >= 1024 && unit < UNITS.length - 1) {
            v /= 1024;
            unit++;
        }
        return unit == 0 ? bytes + " B" : String.format("%.1f %s", v, UNITS[unit]);
    }

    /**
     * 删除文件，忽略不存在与 IO 错误（返回是否真的删掉了）
     */
    public static boolean deleteQuietly(Path path) {
        if (path == null) {
            return false;
        }
        try {
            return Files.deleteIfExists(path);
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * 文件必须位于备份目录之内（防止记录被篡改后读到任意路径）
     */
    public static boolean isInside(Path dir, Path file) {
        Path normalizedDir = dir.toAbsolutePath().normalize();
        Path normalizedFile = file.toAbsolutePath().normalize();
        return normalizedFile.startsWith(normalizedDir) && !normalizedFile.equals(normalizedDir);
    }

    /**
     * 截断异常消息，避免 message 列无限膨胀
     */
    public static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

}
