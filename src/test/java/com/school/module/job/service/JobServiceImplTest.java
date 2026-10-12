package com.school.module.job.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.job.dto.JobLogQueryRequest;
import com.school.module.job.dto.JobLogResponse;
import com.school.module.job.dto.JobQueryRequest;
import com.school.module.job.dto.JobResponse;
import com.school.module.job.dto.JobSaveRequest;
import com.school.module.job.entity.SysJob;
import com.school.module.job.entity.SysJobLog;
import com.school.module.job.mapper.SysJobLogMapper;
import com.school.module.job.mapper.SysJobMapper;
import com.school.module.job.scheduler.JobScheduler;
import com.school.module.job.spi.JobHandler;
import com.school.support.TestAuth;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * JobServiceImpl 单元测试：校验、唯一性、启停与调度器联动、立即执行委托
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("定时任务管理服务测试")
class JobServiceImplTest {

    private static final String CODE = "cleanOperationLogs";
    private static final String CRON = "0 0 3 * * ?";
    private static final String HANDLER = "cleanOperationLogsHandler";

    @Mock
    private SysJobMapper sysJobMapper;

    @Mock
    private SysJobLogMapper sysJobLogMapper;

    @Mock
    private JobScheduler jobScheduler;

    @Mock
    private ApplicationContext applicationContext;

    @InjectMocks
    private JobServiceImpl jobService;

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SysJob.class);
        TableInfoHelper.initTableInfo(assistant, SysJobLog.class);
    }

    @BeforeEach
    void setUp() {
        TestAuth.asSuperAdmin();
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private static SysJob job(Long id, String code, String cron, int status) {
        SysJob job = new SysJob();
        job.setId(id);
        job.setJobName("任务" + id);
        job.setJobCode(code);
        job.setCron(cron);
        job.setStatus(status);
        job.setHandlerBean(HANDLER);
        return job;
    }

    private static JobSaveRequest request(String code, String cron, Integer status, String params) {
        return JobSaveRequest.builder()
                .jobName("清理操作日志")
                .jobCode(code)
                .handlerBean(HANDLER)
                .cron(cron)
                .params(params)
                .status(status)
                .remark("test")
                .build();
    }

    private void handlerExists(String name) {
        when(applicationContext.containsBean(name)).thenReturn(true);
        when(applicationContext.isTypeMatch(name, JobHandler.class)).thenReturn(true);
    }

    private void stubInsertId(long id) {
        doAnswer(invocation -> {
            invocation.getArgument(0, SysJob.class).setId(id);
            return 1;
        }).when(sysJobMapper).insert(any(SysJob.class));
    }

    // ------------------------------------------------------------------
    // 新建
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该插入并注册调度_当新建启用任务且参数合法")
    @SuppressWarnings("unchecked")
    void shouldCreateAndSchedule_WhenEnabledAndValid_ThenReturnId() {
        handlerExists(HANDLER);
        when(sysJobMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        stubInsertId(7L);

        Long id = jobService.createJob(request(CODE, CRON, SysJob.STATUS_ENABLED, "{\"retainDays\":30}"));

        assertThat(id).isEqualTo(7L);
        ArgumentCaptor<SysJob> captor = ArgumentCaptor.forClass(SysJob.class);
        verify(jobScheduler).reschedule(captor.capture());
        assertThat(captor.getValue().getJobCode()).isEqualTo(CODE);
        assertThat(captor.getValue().getStatus()).isEqualTo(SysJob.STATUS_ENABLED);
        assertThat(captor.getValue().getParams()).isEqualTo("{\"retainDays\":30}");
        verify(jobScheduler, never()).cancel(anyString());
    }

    @Test
    @DisplayName("应该默认停用且不注册_当新建未传状态")
    @SuppressWarnings("unchecked")
    void shouldCreateDisabled_WhenStatusMissing_ThenCancelOnly() {
        handlerExists(HANDLER);
        when(sysJobMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        stubInsertId(8L);

        jobService.createJob(request(CODE, CRON, null, null));

        ArgumentCaptor<SysJob> inserted = ArgumentCaptor.forClass(SysJob.class);
        verify(sysJobMapper).insert(inserted.capture());
        assertThat(inserted.getValue().getStatus()).isEqualTo(SysJob.STATUS_DISABLED);
        assertThat(inserted.getValue().getParams()).isNull();
        verify(jobScheduler).cancel(CODE);
        verify(jobScheduler, never()).reschedule(any(SysJob.class));
    }

    @Test
    @DisplayName("应该拒绝_当 cron 不合法")
    void shouldReject_WhenCronInvalid_ThenNoInsert() {
        assertThatThrownBy(() -> jobService.createJob(request(CODE, "bad cron", SysJob.STATUS_ENABLED, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cron");

        verify(sysJobMapper, never()).insert(any(SysJob.class));
    }

    @Test
    @DisplayName("应该拒绝_当 handler Bean 不存在")
    void shouldReject_WhenHandlerMissing_ThenNoInsert() {
        when(applicationContext.containsBean(HANDLER)).thenReturn(false);

        assertThatThrownBy(() -> jobService.createJob(request(CODE, CRON, SysJob.STATUS_ENABLED, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("任务处理器");

        verify(sysJobMapper, never()).insert(any(SysJob.class));
    }

    @Test
    @DisplayName("应该拒绝_当 params 不是 JSON 对象")
    void shouldReject_WhenParamsNotJsonObject_ThenNoInsert() {
        handlerExists(HANDLER);

        assertThatThrownBy(() -> jobService.createJob(request(CODE, CRON, SysJob.STATUS_ENABLED, "[1,2]")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("JSON");

        verify(sysJobMapper, never()).insert(any(SysJob.class));
    }

    @Test
    @DisplayName("应该拒绝_当任务编码重复")
    @SuppressWarnings("unchecked")
    void shouldReject_WhenCodeDuplicated_ThenDataAlreadyExists() {
        handlerExists(HANDLER);
        when(sysJobMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> jobService.createJob(request(CODE, CRON, SysJob.STATUS_ENABLED, null)))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.DATA_ALREADY_EXISTS.getCode()));

        verify(sysJobMapper, never()).insert(any(SysJob.class));
    }

    // ------------------------------------------------------------------
    // 编辑
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该更新并重排调度_当编辑启用任务")
    @SuppressWarnings("unchecked")
    void shouldUpdateAndReschedule_WhenUpdateEnabledJob_ThenNoCancel() {
        when(sysJobMapper.selectById(1L)).thenReturn(job(1L, CODE, CRON, SysJob.STATUS_DISABLED));
        handlerExists(HANDLER);
        when(sysJobMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        jobService.updateJob(1L, request(CODE, "0 0 4 * * ?", SysJob.STATUS_ENABLED, "{\"retainDays\":60}"));

        verify(sysJobMapper).update(any(SysJob.class), any(LambdaUpdateWrapper.class));
        ArgumentCaptor<SysJob> captor = ArgumentCaptor.forClass(SysJob.class);
        verify(jobScheduler).reschedule(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getCron()).isEqualTo("0 0 4 * * ?");
        verify(jobScheduler, never()).cancel(anyString());
    }

    @Test
    @DisplayName("应该撤掉旧编码的调度_当编辑时改了任务编码")
    @SuppressWarnings("unchecked")
    void shouldCancelOldCode_WhenJobCodeChanged_ThenRescheduleNew() {
        when(sysJobMapper.selectById(1L)).thenReturn(job(1L, "oldCode", CRON, SysJob.STATUS_ENABLED));
        handlerExists(HANDLER);
        when(sysJobMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        jobService.updateJob(1L, request(CODE, CRON, SysJob.STATUS_ENABLED, null));

        verify(jobScheduler).cancel("oldCode");
        ArgumentCaptor<SysJob> captor = ArgumentCaptor.forClass(SysJob.class);
        verify(jobScheduler).reschedule(captor.capture());
        assertThat(captor.getValue().getJobCode()).isEqualTo(CODE);
    }

    // ------------------------------------------------------------------
    // 启停 / 删除 / 立即执行
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该写状态并取消调度_当停用任务")
    @SuppressWarnings("unchecked")
    void shouldDisableAndCancel_WhenStatusZero_ThenNoReschedule() {
        when(sysJobMapper.selectById(1L)).thenReturn(job(1L, CODE, CRON, SysJob.STATUS_ENABLED));

        jobService.updateJobStatus(1L, SysJob.STATUS_DISABLED);

        verify(sysJobMapper).update(any(SysJob.class), any(LambdaUpdateWrapper.class));
        verify(jobScheduler).cancel(CODE);
        verify(jobScheduler, never()).reschedule(any(SysJob.class));
    }

    @Test
    @DisplayName("应该写状态并注册调度_当启用任务")
    @SuppressWarnings("unchecked")
    void shouldEnableAndReschedule_WhenStatusOne_ThenScheduled() {
        when(sysJobMapper.selectById(1L)).thenReturn(job(1L, CODE, CRON, SysJob.STATUS_DISABLED));

        jobService.updateJobStatus(1L, SysJob.STATUS_ENABLED);

        verify(sysJobMapper).update(any(SysJob.class), any(LambdaUpdateWrapper.class));
        ArgumentCaptor<SysJob> captor = ArgumentCaptor.forClass(SysJob.class);
        verify(jobScheduler).reschedule(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(SysJob.STATUS_ENABLED);
    }

    @Test
    @DisplayName("应该拒绝启用_当任务 cron 已不合法")
    @SuppressWarnings("unchecked")
    void shouldRejectEnable_WhenCronInvalid_ThenNoUpdate() {
        when(sysJobMapper.selectById(1L)).thenReturn(job(1L, CODE, "broken", SysJob.STATUS_DISABLED));

        assertThatThrownBy(() -> jobService.updateJobStatus(1L, SysJob.STATUS_ENABLED))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cron");

        verify(sysJobMapper, never()).update(any(SysJob.class), any(LambdaUpdateWrapper.class));
        verify(jobScheduler, never()).reschedule(any(SysJob.class));
    }

    @Test
    @DisplayName("应该拒绝_当状态值不是 0 或 1")
    void shouldReject_WhenStatusOutOfRange_ThenNoQuery() {
        assertThatThrownBy(() -> jobService.updateJobStatus(1L, 5))
                .isInstanceOf(BusinessException.class);

        verify(sysJobMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("应该逻辑删除并取消调度_当删除任务")
    void shouldDeleteAndCancel_WhenDeleteJob_ThenSuccess() {
        when(sysJobMapper.selectById(1L)).thenReturn(job(1L, CODE, CRON, SysJob.STATUS_ENABLED));

        jobService.deleteJob(1L);

        verify(sysJobMapper).deleteById(1L);
        verify(jobScheduler).cancel(CODE);
    }

    @Test
    @DisplayName("应该以当前用户名委托调度器立即执行_当触发任务")
    void shouldDelegateRunNowWithUsername_WhenRunJob_ThenReturnLogId() {
        when(sysJobMapper.selectById(1L)).thenReturn(job(1L, CODE, CRON, SysJob.STATUS_DISABLED));
        when(jobScheduler.runNow(CODE, "superadmin")).thenReturn(99L);

        assertThat(jobService.runJob(1L)).isEqualTo(99L);
    }

    @Test
    @DisplayName("应该抛出数据不存在_当任务不存在")
    void shouldThrow_WhenJobNotFound_ThenDataNotFound() {
        when(sysJobMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> jobService.runJob(404L))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.DATA_NOT_FOUND.getCode()));
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该返回排序后的 handler Bean 名")
    void shouldListHandlersSorted_WhenBeansExist_ThenAlphabetical() {
        when(applicationContext.getBeanNamesForType(JobHandler.class))
                .thenReturn(new String[]{"scoreWarningScanHandler", "backupJobHandler", "cleanOperationLogsHandler"});

        assertThat(jobService.listHandlerBeans())
                .containsExactly("backupJobHandler", "cleanOperationLogsHandler", "scoreWarningScanHandler");
    }

    @Test
    @DisplayName("应该标记 handler 可用性_当分页查询任务")
    @SuppressWarnings("unchecked")
    void shouldListJobsWithHandlerAvailability_WhenQuery_ThenConverted() {
        SysJob job = job(1L, CODE, CRON, SysJob.STATUS_ENABLED);
        job.setNextRunAt(LocalDateTime.now().plusHours(1));
        Page<SysJob> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(job));
        when(sysJobMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        handlerExists(HANDLER);

        Page<JobResponse> result = jobService.listJobs(new JobQueryRequest());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        JobResponse response = result.getRecords().get(0);
        assertThat(response.getJobCode()).isEqualTo(CODE);
        assertThat(response.getHandlerAvailable()).isTrue();
        assertThat(response.getNextRunAt()).isNotNull();
    }

    @Test
    @DisplayName("应该返回带状态名的日志_当查询任务日志")
    @SuppressWarnings("unchecked")
    void shouldListLogsWithStatusName_WhenJobExists_ThenConverted() {
        when(sysJobMapper.selectById(1L)).thenReturn(job(1L, CODE, CRON, SysJob.STATUS_ENABLED));
        SysJobLog jobLog = SysJobLog.builder().id(5L).jobId(1L).jobCode(CODE)
                .status(SysJobLog.STATUS_SUCCESS).message("ok").durationMs(12L).triggeredBy("cron").build();
        Page<SysJobLog> page = new Page<>(1, 10, 1);
        page.setRecords(List.of(jobLog));
        when(sysJobLogMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);

        Page<JobLogResponse> result = jobService.listJobLogs(1L, new JobLogQueryRequest());

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getStatusName()).isEqualTo("成功");
        assertThat(result.getRecords().get(0).getTriggeredBy()).isEqualTo("cron");
    }

    @Test
    @DisplayName("应该抛出数据不存在_当查询不存在任务的日志")
    void shouldThrow_WhenListLogsOfMissingJob_ThenDataNotFound() {
        when(sysJobMapper.selectById(9L)).thenReturn(null);

        assertThatThrownBy(() -> jobService.listJobLogs(9L, new JobLogQueryRequest()))
                .isInstanceOf(BusinessException.class);
    }

}
