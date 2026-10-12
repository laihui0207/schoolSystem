package com.school.module.backup.job;

import com.school.module.backup.entity.BackupRecord;
import com.school.module.backup.service.BackupFileSupport;
import com.school.module.backup.service.BackupService;
import com.school.module.job.spi.JobHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 定时任务「backupDatabase」的处理器（契约 S）
 * <p>
 * ponytail: 定时任务模块按 Bean 名 "backupJobHandler" 从容器取、经 JobHandler 接口调用，不 import 本模块的类；
 * 这里是唯一的耦合点。runBackup 本身不抛异常（失败落 status=2），任务框架需要靠异常判定失败，所以这里把 status=2 转成异常。
 *
 * @author School System
 */
@Component("backupJobHandler")
@RequiredArgsConstructor
public class BackupJobHandler implements JobHandler {

    private final BackupService backupService;

    @Override
    public String handle(Map<String, Object> params) {
        BackupRecord record = backupService.runBackup(BackupService.TRIGGER_JOB);
        if (!Integer.valueOf(BackupRecord.STATUS_SUCCESS).equals(record.getStatus())) {
            throw new IllegalStateException("备份失败: " + record.getMessage());
        }
        return "备份完成: " + record.getFileName() + " (" + BackupFileSupport.humanSize(record.getFileSize()) + ")";
    }

}
