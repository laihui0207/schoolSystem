package com.school.module.backup.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.school.common.base.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 备份记录实体（sys_backup）
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("sys_backup")
public class BackupRecord extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 手动触发（含上传导入、恢复前自动备份） */
    public static final int TYPE_MANUAL = 1;
    /** 定时任务触发 */
    public static final int TYPE_SCHEDULED = 2;

    /** 进行中 */
    public static final int STATUS_RUNNING = 0;
    /** 成功 */
    public static final int STATUS_SUCCESS = 1;
    /** 失败 */
    public static final int STATUS_FAILED = 2;

    /** 上传导入的文件在 backup_mode 列的取值 */
    public static final String MODE_UPLOAD = "upload";

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 文件名：.dump 为 pg_dump -Fc 产物，.sql.gz 为本应用 JDBC 生成（可在线恢复）
     */
    private String fileName;

    /**
     * 文件绝对路径
     */
    private String filePath;

    /**
     * 文件字节数
     */
    private Long fileSize;

    /**
     * 1-手动，2-定时
     */
    private Integer backupType;

    /**
     * 实际使用的策略：pg_dump / docker / sql / upload
     */
    private String backupMode;

    /**
     * 0-进行中，1-成功，2-失败
     */
    private Integer status;

    /**
     * 失败原因或备注
     */
    private String message;

    /**
     * 触发人：用户名 / job
     */
    private String triggeredBy;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

}
