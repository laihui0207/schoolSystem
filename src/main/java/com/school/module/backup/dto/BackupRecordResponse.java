package com.school.module.backup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 备份记录响应
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupRecordResponse {

    private Long id;

    private String fileName;

    private Long fileSize;

    /**
     * 人类可读大小，如 "12.3 MB"
     */
    private String fileSizeText;

    /**
     * 1-手动，2-定时
     */
    private Integer backupType;

    private String backupTypeName;

    /**
     * pg_dump / docker / sql / upload
     */
    private String backupMode;

    /**
     * 是否可通过 POST /backups/{id}/restore 在线恢复（仅本应用生成的 .sql.gz 且备份成功、文件仍在）
     */
    private Boolean restorable;

    /**
     * 0-进行中，1-成功，2-失败
     */
    private Integer status;

    private String statusName;

    private String message;

    private String triggeredBy;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private LocalDateTime createdAt;

}
