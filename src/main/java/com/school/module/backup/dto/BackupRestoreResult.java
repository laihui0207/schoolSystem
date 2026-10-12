package com.school.module.backup.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 恢复结果
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BackupRestoreResult {

    /**
     * 被恢复的备份记录 ID
     */
    private Long backupId;

    /**
     * 恢复前自动备份的记录 ID
     */
    private Long preRestoreBackupId;

    /**
     * 实际被 TRUNCATE 并重新装载的表
     */
    private List<String> restoredTables;

    /**
     * 备份文件里有、但当前库里不存在或被排除而跳过的表
     */
    private List<String> skippedTables;

    /**
     * 执行的 SQL 语句数
     */
    private Integer executedStatements;

    private Long elapsedMs;

}
