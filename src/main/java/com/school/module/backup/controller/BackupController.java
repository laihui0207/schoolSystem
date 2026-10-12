package com.school.module.backup.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.backup.dto.BackupQueryRequest;
import com.school.module.backup.dto.BackupRecordResponse;
import com.school.module.backup.dto.BackupRestoreRequest;
import com.school.module.backup.dto.BackupRestoreResult;
import com.school.module.backup.service.BackupService;
import com.school.module.log.annotation.OperationLog;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 数据备份与恢复控制器（全部仅超级管理员）
 * <p>
 * ponytail: 备份文件是整库明文，下载也限制为超管；校管理员没有任何入口。
 * 写接口由 OperationLogAspect 自动审计，恢复的 confirm 字段会原样记录（它不含敏感信息）。
 *
 * @author School System
 */
@RestController
@RequestMapping("/backups")
@RequiredArgsConstructor
public class BackupController {

    private final BackupService backupService;

    /**
     * 手动触发备份：立即返回 status=0 的记录，后台异步执行
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(value = "手动备份数据库", module = "backup")
    public Result<BackupRecordResponse> triggerBackup() {
        return Result.success(backupService.triggerManualBackup());
    }

    /**
     * 分页查询备份记录
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Page<BackupRecordResponse>> listBackups(BackupQueryRequest request) {
        return Result.success(backupService.listBackups(request));
    }

    /**
     * 上传 .sql.gz 作为可恢复备份
     * <p>
     * ponytail: 必须排在 /{id} 之前声明无关紧要（Spring 按精确度匹配），但 "upload" 不是数字，
     * 误走 /{id} 时会被 MethodArgumentTypeMismatch 处理成 404 而不是 500。
     */
    @PostMapping("/upload")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(value = "上传备份文件", module = "backup")
    public Result<BackupRecordResponse> upload(@RequestParam("file") MultipartFile file) {
        return Result.success(backupService.upload(file));
    }

    /**
     * 单条详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<BackupRecordResponse> getBackup(@PathVariable Long id) {
        return Result.success(backupService.getBackup(id));
    }

    /**
     * 流式下载备份文件
     */
    @GetMapping("/{id}/download")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public void download(@PathVariable Long id, HttpServletResponse response) {
        backupService.download(id, response);
    }

    /**
     * 删除备份（文件 + 记录）
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(value = "删除备份", module = "backup")
    public Result<Void> deleteBackup(@PathVariable Long id) {
        backupService.deleteBackup(id);
        return Result.success();
    }

    /**
     * 在线恢复（body 必须带 confirm:"RESTORE"；仅支持 .sql.gz；恢复前自动备份）
     */
    @PostMapping("/{id}/restore")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(value = "从备份恢复数据库", module = "backup")
    public Result<BackupRestoreResult> restore(@PathVariable Long id, @Valid @RequestBody BackupRestoreRequest request) {
        return Result.success(backupService.restore(id, request));
    }

}
