package com.school.module.job.scheduler;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.school.common.exception.BusinessException;
import com.school.module.job.entity.SysJob;
import com.school.module.job.entity.SysJobLog;
import com.school.module.job.mapper.SysJobLogMapper;
import com.school.module.job.mapper.SysJobMapper;
import com.school.module.job.spi.JobHandler;
import com.school.security.util.SecurityUtils;
import com.school.support.TestAuth;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JobScheduler 单元测试：注册 / 取消 / 立即执行写日志 / 系统身份 / 异常隔离
 * <p>
 * TaskScheduler 为 Mockito 桩，捕获提交的 Runnable 后在测试线程同步执行。
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("定时任务调度器测试")
class JobSchedulerTest {

    private static final String CODE = "cleanOperationLogs";
    private static final String CRON = "0 0 3 * * ?";
    private static final String HANDLER = "cleanOperationLogsHandler";

    @Mock
    private TaskScheduler taskScheduler;

    @Mock
    private SysJobMapper sysJobMapper;

    @Mock
    private SysJobLogMapper sysJobLogMapper;

    @Mock
    private ApplicationContext applicationContext;

    @Mock
    private ScheduledFuture<Object> future;

    @Mock
    private ScheduledFuture<Object> secondFuture;

    private JobScheduler scheduler;

    @BeforeAll
    static void initTableInfo() {
        // ponytail: LambdaQueryWrapper 解析 SysJob::getJobCode 依赖 MP 的 TableInfo 缓存；
        // 纯 Mockito 没有 SqlSessionFactory 帮忙初始化，这里手动注册，测试才能单独运行
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysJob.class);
    }

    @BeforeEach
    void setUp() {
        scheduler = new JobScheduler(taskScheduler, sysJobMapper, sysJobLogMapper, applicationContext);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static SysJob job(Long id, String code, String cron, int status, String params, String handlerBean) {
        SysJob job = new SysJob();
        job.setId(id);
        job.setJobName(code);
        job.setJobCode(code);
        job.setCron(cron);
        job.setStatus(status);
        job.setParams(params);
        job.setHandlerBean(handlerBean);
        return job;
    }

    private void stubInsertLogId(long id) {
        doAnswer(invocation -> {
            invocation.getArgument(0, SysJobLog.class).setId(id);
            return 1;
        }).when(sysJobLogMapper).insert(any(SysJobLog.class));
    }

    private Runnable capturedRunNowRunnable() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(captor.capture(), any(Instant.class));
        return captor.getValue();
    }

    private SysJobLog finishedLog() {
        ArgumentCaptor<SysJobLog> captor = ArgumentCaptor.forClass(SysJobLog.class);
        verify(sysJobLogMapper).updateById(captor.capture());
        return captor.getValue();
    }

    private SysJob jobPatch() {
        ArgumentCaptor<SysJob> captor = ArgumentCaptor.forClass(SysJob.class);
        verify(sysJobMapper).updateById(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------
    // 注册 / 取消
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该用 CronTrigger 注册并回写下次运行时间_当任务启用且 cron 合法")
    void shouldRegisterCronTrigger_WhenJobEnabled_ThenHoldFutureAndWriteNextRun() {
        SysJob job = job(1L, CODE, CRON, SysJob.STATUS_ENABLED, null, HANDLER);
        doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

        boolean registered = scheduler.reschedule(job);

        assertThat(registered).isTrue();
        assertThat(scheduler.isScheduled(CODE)).isTrue();
        ArgumentCaptor<Trigger> trigger = ArgumentCaptor.forClass(Trigger.class);
        verify(taskScheduler).schedule(any(Runnable.class), trigger.capture());
        assertThat(trigger.getValue()).isInstanceOf(CronTrigger.class);
        assertThat(((CronTrigger) trigger.getValue()).getExpression()).isEqualTo(CRON);
        SysJob patch = jobPatch();
        assertThat(patch.getId()).isEqualTo(1L);
        assertThat(patch.getNextRunAt()).isAfter(LocalDateTime.now());
    }

    @Test
    @DisplayName("应该拒绝注册_当 cron 不合法")
    void shouldNotRegister_WhenCronInvalid_ThenReturnFalse() {
        SysJob job = job(1L, CODE, "every day at 3", SysJob.STATUS_ENABLED, null, HANDLER);

        assertThat(scheduler.reschedule(job)).isFalse();

        assertThat(scheduler.isScheduled(CODE)).isFalse();
        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
        verify(sysJobMapper, never()).updateById(any(SysJob.class));
    }

    @Test
    @DisplayName("应该拒绝注册_当任务停用")
    void shouldNotRegister_WhenJobDisabled_ThenReturnFalse() {
        SysJob job = job(1L, CODE, CRON, SysJob.STATUS_DISABLED, null, HANDLER);

        assertThat(scheduler.reschedule(job)).isFalse();

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Trigger.class));
    }

    @Test
    @DisplayName("应该取消旧 future_当同一任务重复注册")
    void shouldCancelPreviousFuture_WhenRescheduled_ThenOnlyLatestHeld() {
        SysJob job = job(1L, CODE, CRON, SysJob.STATUS_ENABLED, null, HANDLER);
        doReturn(future).doReturn(secondFuture)
                .when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

        scheduler.reschedule(job);
        scheduler.reschedule(job);

        verify(future).cancel(false);
        verify(secondFuture, never()).cancel(anyBoolean());
        assertThat(scheduler.isScheduled(CODE)).isTrue();
    }

    @Test
    @DisplayName("应该取消 future 并移除_当调用 cancel")
    void shouldCancelFuture_WhenCancel_ThenRemoved() {
        SysJob job = job(1L, CODE, CRON, SysJob.STATUS_ENABLED, null, HANDLER);
        doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        scheduler.reschedule(job);

        scheduler.cancel(CODE);

        verify(future).cancel(false);
        assertThat(scheduler.isScheduled(CODE)).isFalse();
        assertThatCode(() -> scheduler.cancel("unknown")).doesNotThrowAnyException();
        assertThatCode(() -> scheduler.cancel(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("应该取消全部 future_当容器关闭")
    void shouldCancelAll_WhenShutdown_ThenNothingScheduled() {
        doReturn(future).doReturn(secondFuture)
                .when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        scheduler.reschedule(job(1L, CODE, CRON, SysJob.STATUS_ENABLED, null, HANDLER));
        scheduler.reschedule(job(2L, "scoreWarningScan", "0 0 6 * * ?", SysJob.STATUS_ENABLED, null, "scoreWarningScanHandler"));

        scheduler.shutdown();

        verify(future).cancel(false);
        verify(secondFuture).cancel(false);
        assertThat(scheduler.isScheduled(CODE)).isFalse();
        assertThat(scheduler.isScheduled("scoreWarningScan")).isFalse();
    }

    @Test
    @DisplayName("应该只注册 cron 合法的启用任务_当启动加载")
    @SuppressWarnings("unchecked")
    void shouldLoadOnlyEnabledWithValidCron_WhenStartup_ThenRegisterOnce() {
        SysJob valid = job(1L, CODE, CRON, SysJob.STATUS_ENABLED, null, HANDLER);
        SysJob broken = job(2L, "broken", "not a cron", SysJob.STATUS_ENABLED, null, HANDLER);
        when(sysJobMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(valid, broken));
        doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));

        scheduler.loadEnabledJobs();

        verify(taskScheduler, times(1)).schedule(any(Runnable.class), any(Trigger.class));
        assertThat(scheduler.isScheduled(CODE)).isTrue();
        assertThat(scheduler.isScheduled("broken")).isFalse();
    }

    @Test
    @DisplayName("应该计算下次触发时间_当 cron 合法；非法返回 null")
    void shouldComputeNextRun_WhenCronValid_ThenAfterNow() {
        assertThat(JobScheduler.nextRunAt(CRON)).isAfter(LocalDateTime.now());
        assertThat(JobScheduler.nextRunAt("bad")).isNull();
    }

    // ------------------------------------------------------------------
    // 立即执行
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该以系统身份执行并写成功日志_当立即执行")
    @SuppressWarnings("unchecked")
    void shouldRunWithSystemIdentity_WhenRunNow_ThenLogSuccess() {
        SysJob job = job(1L, CODE, CRON, SysJob.STATUS_ENABLED, "{\"retainDays\":30}", HANDLER);
        when(sysJobMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(job);
        when(sysJobMapper.selectById(1L)).thenReturn(job);
        stubInsertLogId(10L);
        AtomicReference<String> seenUser = new AtomicReference<>();
        AtomicReference<Boolean> seenSuperAdmin = new AtomicReference<>();
        AtomicReference<Map<String, Object>> seenParams = new AtomicReference<>();
        JobHandler handler = params -> {
            seenUser.set(SecurityUtils.currentUser().getUsername());
            seenSuperAdmin.set(SecurityUtils.isSuperAdmin());
            seenParams.set(params);
            return "done";
        };
        when(applicationContext.getBean(HANDLER, JobHandler.class)).thenReturn(handler);

        Long logId = scheduler.runNow(CODE, "admin");

        assertThat(logId).isEqualTo(10L);
        ArgumentCaptor<SysJobLog> inserted = ArgumentCaptor.forClass(SysJobLog.class);
        verify(sysJobLogMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getStatus()).isEqualTo(SysJobLog.STATUS_RUNNING);
        assertThat(inserted.getValue().getJobId()).isEqualTo(1L);
        assertThat(inserted.getValue().getJobCode()).isEqualTo(CODE);
        assertThat(inserted.getValue().getTriggeredBy()).isEqualTo("admin");
        assertThat(inserted.getValue().getStartedAt()).isNotNull();

        capturedRunNowRunnable().run();

        assertThat(seenUser.get()).isEqualTo("system");
        assertThat(seenSuperAdmin.get()).isTrue();
        assertThat(seenParams.get()).containsEntry("retainDays", 30);
        SysJobLog finished = finishedLog();
        assertThat(finished.getId()).isEqualTo(10L);
        assertThat(finished.getStatus()).isEqualTo(SysJobLog.STATUS_SUCCESS);
        assertThat(finished.getMessage()).isEqualTo("done");
        assertThat(finished.getFinishedAt()).isNotNull();
        assertThat(finished.getDurationMs()).isGreaterThanOrEqualTo(0L);
        SysJob patch = jobPatch();
        assertThat(patch.getId()).isEqualTo(1L);
        assertThat(patch.getLastRunStatus()).isEqualTo(SysJobLog.STATUS_SUCCESS);
        assertThat(patch.getLastRunAt()).isNotNull();
        // 未注册到调度器的任务不该凭空算出 next_run_at
        assertThat(patch.getNextRunAt()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("应该写失败日志且不抛出_当 handler Bean 不存在")
    @SuppressWarnings("unchecked")
    void shouldLogFailure_WhenHandlerBeanMissing_ThenNoThrow() {
        SysJob job = job(3L, "backupDatabase", "0 0 2 * * ?", SysJob.STATUS_DISABLED, null, "backupJobHandler");
        when(sysJobMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(job);
        when(sysJobMapper.selectById(3L)).thenReturn(job);
        stubInsertLogId(11L);
        when(applicationContext.getBean("backupJobHandler", JobHandler.class))
                .thenThrow(new NoSuchBeanDefinitionException("backupJobHandler"));

        scheduler.runNow("backupDatabase", "admin");
        Runnable runnable = capturedRunNowRunnable();

        assertThatCode(runnable::run).doesNotThrowAnyException();
        SysJobLog finished = finishedLog();
        assertThat(finished.getId()).isEqualTo(11L);
        assertThat(finished.getStatus()).isEqualTo(SysJobLog.STATUS_FAIL);
        assertThat(finished.getMessage()).contains("backupJobHandler");
        assertThat(jobPatch().getLastRunStatus()).isEqualTo(SysJobLog.STATUS_FAIL);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("应该写失败日志并清掉系统身份_当 handler 抛异常")
    @SuppressWarnings("unchecked")
    void shouldLogFailure_WhenHandlerThrows_ThenContextCleared() {
        SysJob job = job(1L, CODE, CRON, SysJob.STATUS_ENABLED, null, HANDLER);
        when(sysJobMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(job);
        when(sysJobMapper.selectById(1L)).thenReturn(job);
        stubInsertLogId(12L);
        JobHandler handler = params -> {
            throw new IllegalStateException("boom");
        };
        when(applicationContext.getBean(HANDLER, JobHandler.class)).thenReturn(handler);

        scheduler.runNow(CODE, "admin");
        Runnable runnable = capturedRunNowRunnable();

        assertThatCode(runnable::run).doesNotThrowAnyException();
        SysJobLog finished = finishedLog();
        assertThat(finished.getStatus()).isEqualTo(SysJobLog.STATUS_FAIL);
        assertThat(finished.getMessage()).contains("boom");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    @DisplayName("应该写失败日志_当 params 不是合法 JSON")
    @SuppressWarnings("unchecked")
    void shouldLogFailure_WhenParamsNotJson_ThenHandlerNotInvoked() {
        SysJob job = job(1L, CODE, CRON, SysJob.STATUS_ENABLED, "not-json", HANDLER);
        when(sysJobMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(job);
        when(sysJobMapper.selectById(1L)).thenReturn(job);
        stubInsertLogId(13L);
        AtomicReference<Boolean> invoked = new AtomicReference<>(false);
        JobHandler handler = params -> {
            invoked.set(true);
            return "should not run";
        };
        when(applicationContext.getBean(HANDLER, JobHandler.class)).thenReturn(handler);

        scheduler.runNow(CODE, "admin");
        capturedRunNowRunnable().run();

        assertThat(invoked.get()).isFalse();
        SysJobLog finished = finishedLog();
        assertThat(finished.getStatus()).isEqualTo(SysJobLog.STATUS_FAIL);
        assertThat(finished.getMessage()).contains("JSON");
    }

    @Test
    @DisplayName("应该把预插的日志标记失败_当任务在执行前被删除")
    @SuppressWarnings("unchecked")
    void shouldMarkPresetLogFailed_WhenJobDeletedBeforeRun_ThenSkipHandler() {
        SysJob job = job(1L, CODE, CRON, SysJob.STATUS_ENABLED, null, HANDLER);
        when(sysJobMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(job);
        when(sysJobMapper.selectById(1L)).thenReturn(null);
        stubInsertLogId(14L);

        scheduler.runNow(CODE, "admin");
        capturedRunNowRunnable().run();

        SysJobLog finished = finishedLog();
        assertThat(finished.getId()).isEqualTo(14L);
        assertThat(finished.getStatus()).isEqualTo(SysJobLog.STATUS_FAIL);
        assertThat(finished.getMessage()).contains("删除");
        verify(applicationContext, never()).getBean(anyString(), eq(JobHandler.class));
        verify(sysJobMapper, never()).updateById(any(SysJob.class));
    }

    @Test
    @DisplayName("应该拒绝再次触发_当同一任务正在执行")
    @SuppressWarnings("unchecked")
    void shouldRejectRunNow_WhenJobAlreadyRunning_ThenBusinessException() {
        SysJob job = job(1L, CODE, CRON, SysJob.STATUS_ENABLED, null, HANDLER);
        when(sysJobMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(job);
        when(sysJobMapper.selectById(1L)).thenReturn(job);
        stubInsertLogId(15L);
        AtomicReference<Throwable> nested = new AtomicReference<>();
        JobHandler handler = params -> {
            // 任务体内再触发同一任务：应被 running 集合拦下
            try {
                scheduler.runNow(CODE, "again");
            } catch (BusinessException e) {
                nested.set(e);
            }
            return "ok";
        };
        when(applicationContext.getBean(HANDLER, JobHandler.class)).thenReturn(handler);

        scheduler.runNow(CODE, "admin");
        capturedRunNowRunnable().run();

        assertThat(nested.get()).isInstanceOf(BusinessException.class).hasMessageContaining("正在执行");
        verify(sysJobLogMapper, times(1)).insert(any(SysJobLog.class));
        assertThat(finishedLog().getStatus()).isEqualTo(SysJobLog.STATUS_SUCCESS);
    }

    @Test
    @DisplayName("应该抛出任务不存在_当立即执行未知编码")
    @SuppressWarnings("unchecked")
    void shouldThrow_WhenRunNowJobNotFound_ThenNoSchedule() {
        when(sysJobMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> scheduler.runNow("nope", "admin"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务不存在");

        verify(taskScheduler, never()).schedule(any(Runnable.class), any(Instant.class));
        verify(sysJobLogMapper, never()).insert(any(SysJobLog.class));
    }

    // ------------------------------------------------------------------
    // cron 触发
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该写 cron 触发日志并刷新下次运行时间_当调度触发")
    void shouldWriteCronLogAndNextRun_WhenScheduledRunFires_ThenSuccess() {
        SysJob job = job(1L, CODE, CRON, SysJob.STATUS_ENABLED, null, HANDLER);
        doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Trigger.class));
        when(sysJobMapper.selectById(1L)).thenReturn(job);
        when(applicationContext.getBean(HANDLER, JobHandler.class)).thenReturn(params -> "ok");
        stubInsertLogId(16L);
        scheduler.reschedule(job);
        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        verify(taskScheduler).schedule(runnable.capture(), any(Trigger.class));

        runnable.getValue().run();

        ArgumentCaptor<SysJobLog> inserted = ArgumentCaptor.forClass(SysJobLog.class);
        verify(sysJobLogMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getTriggeredBy()).isEqualTo(JobScheduler.TRIGGER_CRON);
        assertThat(finishedLog().getStatus()).isEqualTo(SysJobLog.STATUS_SUCCESS);
        ArgumentCaptor<SysJob> patches = ArgumentCaptor.forClass(SysJob.class);
        verify(sysJobMapper, times(2)).updateById(patches.capture());
        SysJob afterRun = patches.getAllValues().get(1);
        assertThat(afterRun.getLastRunStatus()).isEqualTo(SysJobLog.STATUS_SUCCESS);
        assertThat(afterRun.getNextRunAt()).isAfter(LocalDateTime.now());
    }

}
