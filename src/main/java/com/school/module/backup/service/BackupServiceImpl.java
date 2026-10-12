package com.school.module.backup.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.backup.config.BackupDataSourceInfo;
import com.school.module.backup.config.BackupProperties;
import com.school.module.backup.dto.BackupQueryRequest;
import com.school.module.backup.dto.BackupRecordResponse;
import com.school.module.backup.dto.BackupRestoreRequest;
import com.school.module.backup.dto.BackupRestoreResult;
import com.school.module.backup.entity.BackupRecord;
import com.school.module.backup.mapper.BackupRecordMapper;
import com.school.module.backup.service.strategy.BackupSqlFormat;
import com.school.module.backup.service.strategy.BackupStrategy;
import com.school.module.backup.service.strategy.BackupStrategyResolver;
import com.school.security.util.SecurityUtils;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.school.module.backup.entity.BackupRecord.STATUS_FAILED;
import static com.school.module.backup.entity.BackupRecord.STATUS_RUNNING;
import static com.school.module.backup.entity.BackupRecord.STATUS_SUCCESS;
import static com.school.module.backup.entity.BackupRecord.TYPE_MANUAL;
import static com.school.module.backup.entity.BackupRecord.TYPE_SCHEDULED;

/**
 * 数据备份与恢复服务实现
 * <p>
 * ponytail: Bean 名显式写成 "backupService"（契约 S，定时任务模块按名取）。
 * ponytail: 备份与恢复共用一把锁、同一时刻只跑一个——两个 pg_dump 同时跑会把 IO 打满，恢复期间跑备份会备出半截数据。
 * 手动备份在单线程池里异步执行，接口立即返回 status=0 的记录；定时任务与恢复前保险用同步的 runBackup。
 *
 * @author School System
 */
@Slf4j
@Service("backupService")
public class BackupServiceImpl implements BackupService {

    static final String PRE_RESTORE_NOTE = "恢复前自动备份";
    static final String UPLOAD_NOTE_PREFIX = "上传导入：";
    private static final int MESSAGE_MAX = 2000;
    private static final int TRIGGERED_BY_MAX = 50;

    private final BackupRecordMapper backupRecordMapper;
    private final BackupProperties properties;
    private final BackupDataSourceInfo dataSourceInfo;
    private final BackupStrategyResolver strategyResolver;
    private final BackupRestoreExecutor restoreExecutor;
    private final ExecutorService worker;
    private final Object lock = new Object();

    public BackupServiceImpl(BackupRecordMapper backupRecordMapper,
                             BackupProperties properties,
                             BackupDataSourceInfo dataSourceInfo,
                             BackupStrategyResolver strategyResolver,
                             BackupRestoreExecutor restoreExecutor) {
        this.backupRecordMapper = backupRecordMapper;
        this.properties = properties;
        this.dataSourceInfo = dataSourceInfo;
        this.strategyResolver = strategyResolver;
        this.restoreExecutor = restoreExecutor;
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "backup-worker");
            t.setDaemon(true);
            return t;
        });
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }

    /**
     * 应用重启时把上次遗留的「进行中」记录标为失败，否则它们会永远卡在 0
     */
    @EventListener(ApplicationReadyEvent.class)
    public void markInterruptedBackups() {
        try {
            int n = backupRecordMapper.update(null, new LambdaUpdateWrapper<BackupRecord>()
                    .eq(BackupRecord::getStatus, STATUS_RUNNING)
                    .set(BackupRecord::getStatus, STATUS_FAILED)
                    .set(BackupRecord::getFinishedAt, LocalDateTime.now())
                    .set(BackupRecord::getMessage, "应用重启，备份被中断"));
            if (n > 0) {
                log.warn("启动时发现 {} 条中断的备份记录，已标记为失败", n);
            }
        } catch (Exception e) {
            log.warn("标记中断备份失败（不影响启动）: {}", e.toString());
        }
    }

    // ------------------------------------------------------------------
    // 备份
    // ------------------------------------------------------------------

    @Override
    public BackupRecord runBackup(String triggeredBy) {
        int type = TRIGGER_JOB.equals(triggeredBy) ? TYPE_SCHEDULED : TYPE_MANUAL;
        BackupRecord record = createPending(triggeredBy, type, null);
        return execute(record, null, null);
    }

    @Override
    public BackupRecordResponse triggerManualBackup() {
        BackupRecord record = createPending(currentUsername(), TYPE_MANUAL, null);
        worker.submit(() -> {
            // ponytail: 工作线程在首次提交时才创建，MODE_INHERITABLETHREADLOCAL 会让它继承当时请求的 SecurityContext；
            // 清掉以免后续任务带着陈旧身份跑（备份走 JdbcTemplate 本不受影响，但不留隐患）
            SecurityContextHolder.clearContext();
            execute(record, null, null);
        });
        return convertToResponse(record);
    }

    private BackupRecord createPending(String triggeredBy, int type, String note) {
        BackupRecord record = BackupRecord.builder()
                .fileName("pending")
                .backupType(type)
                .status(STATUS_RUNNING)
                .triggeredBy(BackupFileSupport.truncate(triggeredBy, TRIGGERED_BY_MAX))
                .startedAt(LocalDateTime.now())
                .message(note)
                .build();
        backupRecordMapper.insert(record);
        return record;
    }

    /**
     * 真正执行导出；失败不抛，落 status=2
     *
     * @param forcedMode  非 null 时忽略 app.backup.mode（恢复前保险强制 sql）
     * @param protectedId 清理过期备份时绝不删除的记录（正在被恢复的那份）
     */
    private BackupRecord execute(BackupRecord record, String forcedMode, Long protectedId) {
        synchronized (lock) {
            Path target = null;
            try {
                Path dir = ensureDir();
                BackupStrategy strategy = strategyResolver.resolve(forcedMode != null ? forcedMode : properties.getMode());
                String fileName = BackupFileSupport.backupFileName(record.getId(), record.getStartedAt(), strategy.fileExtension());
                target = dir.resolve(fileName);
                record.setFileName(fileName);
                record.setFilePath(target.toString());
                record.setBackupMode(strategy.mode());
                log.info("开始备份 #{} 模式={} 文件={}", record.getId(), strategy.mode(), target);

                String summary = strategy.dump(target);
                long size = Files.isRegularFile(target) ? Files.size(target) : 0;
                if (size == 0) {
                    throw new IOException("备份文件为空");
                }
                record.setFileSize(size);
                record.setStatus(STATUS_SUCCESS);
                record.setFinishedAt(LocalDateTime.now());
                record.setMessage(joinNote(record.getMessage(), summary));
                backupRecordMapper.updateById(record);
                log.info("备份 #{} 完成: {} ({})", record.getId(), fileName, BackupFileSupport.humanSize(size));
                prune(protectedId);
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.error("备份 #{} 失败", record.getId(), e);
                BackupFileSupport.deleteQuietly(target);
                record.setStatus(STATUS_FAILED);
                record.setFinishedAt(LocalDateTime.now());
                record.setMessage(BackupFileSupport.truncate(joinNote(record.getMessage(), describe(e)), MESSAGE_MAX));
                backupRecordMapper.updateById(record);
            }
            return record;
        }
    }

    /**
     * 只保留最近 keep-count 份成功备份，其余删文件 + 删记录
     */
    private void prune(Long protectedId) {
        int keep = properties.getKeepCount();
        if (keep <= 0) {
            return;
        }
        List<BackupRecord> successes = backupRecordMapper.selectList(new LambdaQueryWrapper<BackupRecord>()
                .eq(BackupRecord::getStatus, STATUS_SUCCESS)
                .orderByDesc(BackupRecord::getId));
        if (successes.size() <= keep) {
            return;
        }
        for (BackupRecord old : successes.subList(keep, successes.size())) {
            // ponytail: 恢复前自动备份也会触发清理，正被恢复的那份绝不能在此刻被清掉
            if (old.getId().equals(protectedId)) {
                continue;
            }
            deleteFile(old);
            backupRecordMapper.deleteById(old.getId());
            log.info("清理过期备份 #{} {}", old.getId(), old.getFileName());
        }
    }

    // ------------------------------------------------------------------
    // 查询 / 下载 / 删除
    // ------------------------------------------------------------------

    @Override
    public Page<BackupRecordResponse> listBackups(BackupQueryRequest request) {
        Page<BackupRecord> page = backupRecordMapper.selectPage(
                new Page<>(request.getPageNum(), request.getPageSize()),
                new LambdaQueryWrapper<BackupRecord>()
                        .eq(request.getStatus() != null, BackupRecord::getStatus, request.getStatus())
                        .eq(request.getBackupType() != null, BackupRecord::getBackupType, request.getBackupType())
                        .orderByDesc(BackupRecord::getId));
        Page<BackupRecordResponse> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::convertToResponse).toList());
        return result;
    }

    @Override
    public BackupRecordResponse getBackup(Long id) {
        return convertToResponse(requireRecord(id));
    }

    @Override
    public void download(Long id, HttpServletResponse response) {
        BackupRecord record = requireRecord(id);
        Path file = requireFile(record);
        try {
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + record.getFileName() + "\"");
            response.setContentLengthLong(Files.size(file));
            Files.copy(file, response.getOutputStream());
            response.flushBuffer();
        } catch (IOException e) {
            // ponytail: 响应已提交（客户端中途取消下载）时不能再抛业务异常，否则全局处理器往已提交的响应里写 JSON 又报一次错
            if (response.isCommitted()) {
                log.warn("下载备份 #{} 中断: {}", id, e.toString());
                return;
            }
            throw new BusinessException(ResultCode.FAIL, "下载失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteBackup(Long id) {
        BackupRecord record = requireRecord(id);
        if (isStatus(record, STATUS_RUNNING)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "备份进行中，不能删除");
        }
        deleteFile(record);
        backupRecordMapper.deleteById(id);
    }

    // ------------------------------------------------------------------
    // 恢复 / 上传
    // ------------------------------------------------------------------

    @Override
    public BackupRestoreResult restore(Long id, BackupRestoreRequest request) {
        if (request == null || !BackupRestoreRequest.CONFIRM_WORD.equals(request.getConfirm())) {
            throw new IllegalArgumentException("confirm 必须为 RESTORE 才能执行恢复");
        }
        BackupRecord record = requireRecord(id);
        if (!isStatus(record, STATUS_SUCCESS)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "只能从状态为「成功」的备份恢复");
        }
        Path file = requireFile(record);
        if (!BackupSqlFormat.isSqlBackupFile(record.getFileName())) {
            // ponytail: -Fc 产物只能用 pg_restore；用 IllegalArgumentException 走 HTTP 400 通道，把命令原文给到用户
            throw new IllegalArgumentException("该备份需用 pg_restore 手工恢复：pg_restore -d "
                    + dataSourceInfo.getDatabase() + " " + file);
        }
        String username = currentUsername();
        long start = System.currentTimeMillis();
        synchronized (lock) {
            BackupRecord safety = createPending(username, TYPE_MANUAL, PRE_RESTORE_NOTE);
            execute(safety, BackupProperties.MODE_SQL, id);
            if (!isStatus(safety, STATUS_SUCCESS)) {
                throw new BusinessException(ResultCode.FAIL, "恢复前自动备份失败，已中止恢复：" + safety.getMessage());
            }
            log.warn("用户 {} 开始从备份 #{} ({}) 恢复数据库，保险备份 #{}", username, id, record.getFileName(), safety.getId());
            BackupRestoreExecutor.Outcome outcome;
            try {
                outcome = restoreExecutor.restore(file);
            } catch (IOException e) {
                throw new BusinessException(ResultCode.FAIL, "读取备份文件失败: " + e.getMessage());
            } catch (IllegalStateException e) {
                throw new BusinessException(ResultCode.FAIL, "恢复失败: " + e.getMessage());
            } catch (DataAccessException e) {
                // ponytail: 任一语句失败整笔已回滚；把 PG 原始错误带给用户，否则只会看到 500「系统错误」
                log.error("备份 #{} 恢复失败，事务已回滚", id, e);
                throw new BusinessException(ResultCode.FAIL, "恢复失败（已回滚）: " + e.getMostSpecificCause().getMessage());
            }
            return BackupRestoreResult.builder()
                    .backupId(id)
                    .preRestoreBackupId(safety.getId())
                    .restoredTables(outcome.restoredTables())
                    .skippedTables(outcome.skippedTables())
                    .executedStatements(outcome.executedStatements())
                    .elapsedMs(System.currentTimeMillis() - start)
                    .build();
        }
    }

    @Override
    public BackupRecordResponse upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR, "请选择要上传的备份文件");
        }
        String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        if (!BackupSqlFormat.isSqlBackupFile(original)) {
            throw new BusinessException(ResultCode.FILE_TYPE_ERROR, "仅支持本系统生成的 .sql.gz 备份文件");
        }
        Path dir = ensureDir();
        Path temp = dir.resolve("upload_tmp_" + UUID.randomUUID() + ".part");
        try {
            file.transferTo(temp);
            String invalid = BackupRestoreExecutor.validateSqlBackup(temp);
            if (invalid != null) {
                BackupFileSupport.deleteQuietly(temp);
                throw new BusinessException(ResultCode.FILE_TYPE_ERROR, "备份文件校验失败：" + invalid);
            }
            LocalDateTime now = LocalDateTime.now();
            BackupRecord record = BackupRecord.builder()
                    .fileName("pending")
                    .backupType(TYPE_MANUAL)
                    .backupMode(BackupRecord.MODE_UPLOAD)
                    .status(STATUS_SUCCESS)
                    .triggeredBy(BackupFileSupport.truncate(currentUsername(), TRIGGERED_BY_MAX))
                    .startedAt(now)
                    .finishedAt(now)
                    .message(BackupFileSupport.truncate(UPLOAD_NOTE_PREFIX + original, MESSAGE_MAX))
                    .build();
            synchronized (lock) {
                backupRecordMapper.insert(record);
                String fileName = BackupFileSupport.uploadFileName(record.getId(), now, BackupSqlFormat.FILE_EXTENSION);
                Path target = dir.resolve(fileName);
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
                record.setFileName(fileName);
                record.setFilePath(target.toString());
                record.setFileSize(Files.size(target));
                backupRecordMapper.updateById(record);
                prune(record.getId());
            }
            log.info("上传备份 #{} {} ({})", record.getId(), record.getFileName(), BackupFileSupport.humanSize(record.getFileSize()));
            return convertToResponse(record);
        } catch (IOException e) {
            BackupFileSupport.deleteQuietly(temp);
            throw new BusinessException(ResultCode.FILE_UPLOAD_ERROR, "保存上传文件失败: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // 内部工具
    // ------------------------------------------------------------------

    private BackupRecord requireRecord(Long id) {
        BackupRecord record = backupRecordMapper.selectById(id);
        if (record == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "备份记录不存在");
        }
        return record;
    }

    private Path requireFile(BackupRecord record) {
        if (record.getFilePath() == null || record.getFilePath().isBlank()) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND, "该备份没有对应文件");
        }
        Path file = Paths.get(record.getFilePath());
        if (!BackupFileSupport.isInside(backupDir(), file)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "备份文件不在备份目录内，拒绝访问");
        }
        if (!Files.isRegularFile(file)) {
            throw new BusinessException(ResultCode.FILE_NOT_FOUND, "备份文件已不存在: " + record.getFileName());
        }
        return file;
    }

    private void deleteFile(BackupRecord record) {
        if (record.getFilePath() == null) {
            return;
        }
        Path file = Paths.get(record.getFilePath());
        if (BackupFileSupport.isInside(backupDir(), file)) {
            BackupFileSupport.deleteQuietly(file);
        }
    }

    private Path backupDir() {
        return Paths.get(properties.getDir()).toAbsolutePath().normalize();
    }

    private Path ensureDir() {
        Path dir = backupDir();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建备份目录 " + dir + ": " + e.getMessage(), e);
        }
        return dir;
    }

    private static String currentUsername() {
        return SecurityUtils.currentUser().getUsername();
    }

    private static boolean isStatus(BackupRecord record, int status) {
        return record.getStatus() != null && record.getStatus() == status;
    }

    private static String joinNote(String note, String extra) {
        if (note == null || note.isBlank()) {
            return extra;
        }
        if (extra == null || extra.isBlank()) {
            return note;
        }
        return note + "；" + extra;
    }

    private static String describe(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private BackupRecordResponse convertToResponse(BackupRecord r) {
        boolean restorable = isStatus(r, STATUS_SUCCESS)
                && BackupSqlFormat.isSqlBackupFile(r.getFileName())
                && r.getFilePath() != null
                && Files.isRegularFile(Paths.get(r.getFilePath()));
        return BackupRecordResponse.builder()
                .id(r.getId())
                .fileName(r.getFileName())
                .fileSize(r.getFileSize())
                .fileSizeText(BackupFileSupport.humanSize(r.getFileSize()))
                .backupType(r.getBackupType())
                .backupTypeName(typeName(r.getBackupType()))
                .backupMode(r.getBackupMode())
                .restorable(restorable)
                .status(r.getStatus())
                .statusName(statusName(r.getStatus()))
                .message(r.getMessage())
                .triggeredBy(r.getTriggeredBy())
                .startedAt(r.getStartedAt())
                .finishedAt(r.getFinishedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }

    private static String typeName(Integer type) {
        if (type == null) {
            return null;
        }
        return type == TYPE_SCHEDULED ? "定时" : "手动";
    }

    private static String statusName(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case STATUS_RUNNING -> "进行中";
            case STATUS_SUCCESS -> "成功";
            case STATUS_FAILED -> "失败";
            default -> String.valueOf(status);
        };
    }

}
