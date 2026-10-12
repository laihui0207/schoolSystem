package com.school.module.backup.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.backup.dto.BackupQueryRequest;
import com.school.module.backup.dto.BackupRecordResponse;
import com.school.module.backup.dto.BackupRestoreRequest;
import com.school.module.backup.dto.BackupRestoreResult;
import com.school.module.backup.entity.BackupRecord;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据备份与恢复服务
 * <p>
 * 契约 S：Bean 名固定为 "backupService"，{@link #runBackup(String)} 供定时任务模块通过
 * {@code com.school.module.backup.job.BackupJobHandler} 调用。
 *
 * @author School System
 */
public interface BackupService {

    /**
     * 定时任务触发时的 triggered_by 取值；据此把 backup_type 记为 2-定时
     */
    String TRIGGER_JOB = "job";

    /**
     * 同步执行一次备份：先落 status=0 记录，按 app.backup.mode 选策略导出，成功 status=1 并按 keep-count 清理最旧，
     * 失败 status=2 + message。同一时刻只允许一个备份/恢复在跑（排队等待）。
     *
     * @param triggeredBy 触发人（用户名或 {@link #TRIGGER_JOB}）
     * @return 最终状态的记录；失败不抛异常，调用方看 status
     */
    BackupRecord runBackup(String triggeredBy);

    /**
     * 手动备份：先插入 status=0 的记录并立即返回，实际导出在后台线程执行
     *
     * @return 进行中的记录（含 id，前端轮询列表看结果）
     */
    BackupRecordResponse triggerManualBackup();

    Page<BackupRecordResponse> listBackups(BackupQueryRequest request);

    BackupRecordResponse getBackup(Long id);

    /**
     * 流式下载备份文件
     */
    void download(Long id, HttpServletResponse response);

    /**
     * 删除文件与记录（进行中的不能删）
     */
    void deleteBackup(Long id);

    /**
     * 在线恢复：仅支持本应用生成的 .sql.gz；恢复前自动先做一次 sql 模式备份；confirm 必须为 RESTORE
     */
    BackupRestoreResult restore(Long id, BackupRestoreRequest request);

    /**
     * 上传一个 .sql.gz 作为可恢复备份（校验 gzip 魔数与首行标记）
     */
    BackupRecordResponse upload(MultipartFile file);

}
