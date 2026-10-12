package com.school.module.job.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.job.entity.SysJob;
import com.school.module.job.entity.SysJobLog;
import com.school.module.job.mapper.SysJobLogMapper;
import com.school.module.job.mapper.SysJobMapper;
import com.school.module.job.spi.JobHandler;
import com.school.module.job.spi.JobParams;
import com.school.module.user.enums.UserRole;
import com.school.security.model.LoginUser;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeansException;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 定时任务调度器（不依赖 Quartz）
 * <p>
 * 启动完成后加载 status=1 的任务，用 {@link TaskScheduler#schedule(Runnable, org.springframework.scheduling.Trigger)}
 * + {@link CronTrigger} 注册，并持有 jobCode → ScheduledFuture 便于停用 / 改 cron 时取消重排。
 * 每次执行写一条 sys_job_log，并回写 sys_job.last_run_* / next_run_at。
 * <p>
 * ponytail: 执行时往 SecurityContext 放一个 userType=1 的系统身份（userId=0, username="system"），
 * 被调用的 Service 里 SecurityUtils.currentXxx() 才不会抛 UNAUTHORIZED、租户插件才会放行；
 * 线程池线程会复用，所以 finally 必须 clearContext，否则下一个任务或别的调用会继承这份超管身份。
 * ponytail: Runnable 只捕获 jobId，真正执行时重新 selectById——编辑过 handler/params 或刚被停用、删除的任务
 * 不能再按注册时的旧快照跑。
 *
 * @author School System
 */
@Component
@Slf4j
public class JobScheduler {

    /** sys_job_log.triggered_by 的调度触发标记 */
    public static final String TRIGGER_CRON = "cron";

    static final int MESSAGE_MAX_LENGTH = 2000;
    private static final int TRIGGERED_BY_MAX_LENGTH = 50;
    private static final Long SYSTEM_USER_ID = 0L;
    private static final String SYSTEM_USERNAME = "system";

    private final TaskScheduler taskScheduler;
    private final SysJobMapper sysJobMapper;
    private final SysJobLogMapper sysJobLogMapper;
    private final ApplicationContext applicationContext;

    private final Map<String, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();
    /** 正在执行的 jobCode：同一任务不并发，手动触发撞上在跑的实例直接拒绝 */
    private final Set<String> running = ConcurrentHashMap.newKeySet();

    public JobScheduler(TaskScheduler taskScheduler,
                        SysJobMapper sysJobMapper,
                        SysJobLogMapper sysJobLogMapper,
                        ApplicationContext applicationContext) {
        this.taskScheduler = taskScheduler;
        this.sysJobMapper = sysJobMapper;
        this.sysJobLogMapper = sysJobLogMapper;
        this.applicationContext = applicationContext;
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    /**
     * 容器就绪后注册所有启用中的任务
     * <p>
     * ponytail: 用 ApplicationReadyEvent 而非 @PostConstruct——此时 Flyway 已跑完、Mapper 可用；
     * devtools 热重启会再次触发，旧上下文的 future 已在 {@link #shutdown()} 取消。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadEnabledJobs() {
        List<SysJob> jobs = sysJobMapper.selectList(new LambdaQueryWrapper<SysJob>()
                .eq(SysJob::getStatus, SysJob.STATUS_ENABLED));
        int registered = 0;
        for (SysJob job : jobs) {
            if (reschedule(job)) {
                registered++;
            }
        }
        log.info("定时任务加载完成：启用 {} 个，成功注册 {} 个", jobs.size(), registered);
    }

    @PreDestroy
    public void shutdown() {
        futures.values().forEach(future -> future.cancel(false));
        futures.clear();
    }

    // ------------------------------------------------------------------
    // 注册 / 取消 / 立即执行
    // ------------------------------------------------------------------

    /**
     * （重新）注册任务：先取消旧 future；停用或 cron 非法的任务不注册
     *
     * @return 是否已注册到调度器
     */
    public boolean reschedule(SysJob job) {
        cancel(job.getJobCode());
        if (job.getStatus() == null || job.getStatus() != SysJob.STATUS_ENABLED) {
            return false;
        }
        if (!CronExpression.isValidExpression(job.getCron())) {
            log.warn("任务 {} 的 cron「{}」不合法，未注册", job.getJobCode(), job.getCron());
            return false;
        }
        Long jobId = job.getId();
        ScheduledFuture<?> future = taskScheduler.schedule(
                () -> execute(jobId, TRIGGER_CRON, null), new CronTrigger(job.getCron()));
        if (future == null) {
            log.warn("任务 {} 的 cron「{}」没有下一次触发时间，未注册", job.getJobCode(), job.getCron());
            return false;
        }
        futures.put(job.getJobCode(), future);
        updateNextRunAt(job);
        log.info("任务 {} 已注册，cron={}", job.getJobCode(), job.getCron());
        return true;
    }

    /**
     * 取消调度（任务不存在于调度器时静默）
     */
    public void cancel(String jobCode) {
        if (jobCode == null) {
            return;
        }
        ScheduledFuture<?> future = futures.remove(jobCode);
        if (future != null) {
            future.cancel(false);
            log.info("任务 {} 已取消调度", jobCode);
        }
    }

    /**
     * 是否已注册到调度器
     */
    public boolean isScheduled(String jobCode) {
        return jobCode != null && futures.containsKey(jobCode);
    }

    /**
     * 立即异步执行一次（不要求任务处于启用状态）
     * <p>
     * 先插入一条 status=2（执行中）的日志再提交到线程池，调用方立刻拿到 jobLogId 可轮询结果。
     *
     * @param jobCode     任务编码
     * @param triggeredBy 触发者（用户名）
     * @return sys_job_log.id
     */
    public Long runNow(String jobCode, String triggeredBy) {
        SysJob job = sysJobMapper.selectOne(new LambdaQueryWrapper<SysJob>()
                .eq(SysJob::getJobCode, jobCode));
        if (job == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "任务不存在: " + jobCode);
        }
        if (running.contains(jobCode)) {
            throw new BusinessException("任务正在执行中，请稍后再试");
        }
        SysJobLog jobLog = insertRunningLog(job, triggeredBy);
        Long jobId = job.getId();
        Long logId = jobLog.getId();
        taskScheduler.schedule(() -> execute(jobId, triggeredBy, logId), Instant.now());
        return logId;
    }

    // ------------------------------------------------------------------
    // 执行
    // ------------------------------------------------------------------

    /**
     * 执行一次任务；handler 的任何异常只记日志，绝不向线程池上抛
     *
     * @param presetLogId runNow 预先插入的日志 ID，调度触发为 null
     */
    void execute(Long jobId, String triggeredBy, Long presetLogId) {
        SysJob job = sysJobMapper.selectById(jobId);
        if (job == null) {
            log.warn("任务 {} 已不存在，跳过执行", jobId);
            if (presetLogId != null) {
                finishLog(presetLogId, SysJobLog.STATUS_FAIL, "任务已被删除", 0L);
            }
            return;
        }
        if (!running.add(job.getJobCode())) {
            log.warn("任务 {} 上一次执行尚未结束，跳过本次", job.getJobCode());
            if (presetLogId != null) {
                finishLog(presetLogId, SysJobLog.STATUS_FAIL, "上一次执行尚未结束，本次跳过", 0L);
            }
            return;
        }

        long startNanos = System.nanoTime();
        Long logId = presetLogId != null ? presetLogId : insertRunningLog(job, triggeredBy).getId();
        int status;
        String message;
        try {
            installSystemIdentity();
            JobHandler handler = applicationContext.getBean(job.getHandlerBean(), JobHandler.class);
            Map<String, Object> params = JobParams.parse(job.getParams());
            message = handler.handle(params);
            status = SysJobLog.STATUS_SUCCESS;
        } catch (BeansException e) {
            // 预置的 backupJobHandler 在备份模块未就绪时会落到这里
            status = SysJobLog.STATUS_FAIL;
            message = "找不到任务处理器 Bean「" + job.getHandlerBean() + "」或其未实现 JobHandler: " + e.getMessage();
            log.error("任务 {} 执行失败: {}", job.getJobCode(), message);
        } catch (Exception e) {
            status = SysJobLog.STATUS_FAIL;
            message = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.error("任务 {} 执行失败", job.getJobCode(), e);
        } finally {
            SecurityContextHolder.clearContext();
            running.remove(job.getJobCode());
        }

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        finishLog(logId, status, message, durationMs);
        updateJobAfterRun(job, status);
    }

    private void installSystemIdentity() {
        LoginUser systemUser = LoginUser.builder()
                .userId(SYSTEM_USER_ID)
                .username(SYSTEM_USERNAME)
                .userType(UserRole.SUPER_ADMIN.getUserType())
                .authorities(List.of(new SimpleGrantedAuthority(UserRole.SUPER_ADMIN.getRoleCode())))
                .build();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                systemUser, null, systemUser.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }

    // ------------------------------------------------------------------
    // 持久化
    // ------------------------------------------------------------------

    private SysJobLog insertRunningLog(SysJob job, String triggeredBy) {
        SysJobLog jobLog = SysJobLog.builder()
                .jobId(job.getId())
                .jobCode(job.getJobCode())
                .startedAt(LocalDateTime.now())
                .status(SysJobLog.STATUS_RUNNING)
                .triggeredBy(truncate(triggeredBy, TRIGGERED_BY_MAX_LENGTH))
                .build();
        sysJobLogMapper.insert(jobLog);
        return jobLog;
    }

    private void finishLog(Long logId, int status, String message, long durationMs) {
        if (logId == null) {
            return;
        }
        try {
            SysJobLog patch = SysJobLog.builder()
                    .id(logId)
                    .finishedAt(LocalDateTime.now())
                    .status(status)
                    .message(truncate(message, MESSAGE_MAX_LENGTH))
                    .durationMs(durationMs)
                    .build();
            sysJobLogMapper.updateById(patch);
        } catch (Exception e) {
            // ponytail: 日志写失败不能把调度线程打死，只能 error
            log.error("任务日志 {} 回写失败", logId, e);
        }
    }

    /**
     * 回写 last_run_*；仍在调度中的任务顺带刷新 next_run_at。
     * ponytail: 新建只含 id + 本次字段的对象做 updateById，NOT_NULL 策略下不会把别的列覆盖回旧值
     */
    private void updateJobAfterRun(SysJob job, int status) {
        try {
            SysJob patch = new SysJob();
            patch.setId(job.getId());
            patch.setLastRunAt(LocalDateTime.now());
            patch.setLastRunStatus(status);
            if (isScheduled(job.getJobCode())) {
                patch.setNextRunAt(nextRunAt(job.getCron()));
            }
            sysJobMapper.updateById(patch);
        } catch (Exception e) {
            log.error("任务 {} 执行结果回写失败", job.getJobCode(), e);
        }
    }

    private void updateNextRunAt(SysJob job) {
        SysJob patch = new SysJob();
        patch.setId(job.getId());
        patch.setNextRunAt(nextRunAt(job.getCron()));
        sysJobMapper.updateById(patch);
    }

    /**
     * 按 cron 计算下一次触发时间；非法表达式返回 null
     */
    public static LocalDateTime nextRunAt(String cron) {
        if (!CronExpression.isValidExpression(cron)) {
            return null;
        }
        return CronExpression.parse(cron).next(LocalDateTime.now());
    }

    private static String truncate(String value, int maxLength) {
        if (!StringUtils.hasLength(value) || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

}
