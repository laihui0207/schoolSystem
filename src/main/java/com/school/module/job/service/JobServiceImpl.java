package com.school.module.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import com.school.module.job.spi.JobParams;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 定时任务管理服务实现
 * <p>
 * ponytail: 写方法不开事务——每个都是单条 UPDATE/INSERT，自动提交后再去碰调度器；
 * 若包在事务里，reschedule 注册的 Runnable 可能在事务提交前就被触发并 selectById 读到旧状态。
 *
 * @author School System
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class JobServiceImpl implements JobService {

    private final SysJobMapper sysJobMapper;
    private final SysJobLogMapper sysJobLogMapper;
    private final JobScheduler jobScheduler;
    private final ApplicationContext applicationContext;

    @Override
    public Page<JobResponse> listJobs(JobQueryRequest request) {
        LambdaQueryWrapper<SysJob> wrapper = new LambdaQueryWrapper<SysJob>()
                .like(StringUtils.hasText(request.getJobName()), SysJob::getJobName, request.getJobName())
                .like(StringUtils.hasText(request.getJobCode()), SysJob::getJobCode, request.getJobCode())
                .eq(request.getStatus() != null, SysJob::getStatus, request.getStatus())
                .orderByAsc(SysJob::getId);
        Page<SysJob> page = sysJobMapper.selectPage(new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
        return convertPage(page, this::convertToResponse);
    }

    @Override
    public Long createJob(JobSaveRequest request) {
        validate(request);
        assertCodeUnique(request.getJobCode().trim(), null);

        SysJob job = toEntity(request);
        sysJobMapper.insert(job);
        syncScheduler(job);
        log.info("新建定时任务 {}（{}），status={}", job.getJobCode(), job.getJobName(), job.getStatus());
        return job.getId();
    }

    @Override
    public void updateJob(Long id, JobSaveRequest request) {
        SysJob existing = requireJob(id);
        validate(request);
        assertCodeUnique(request.getJobCode().trim(), id);

        SysJob patch = toEntity(request);
        // ponytail: params/remark 允许被清空，NOT_NULL 策略下 updateById 清不掉，所以这两列连同停用时的 next_run_at 走 wrapper 显式 set；
        // 不回写整份 existing 实体，避免把并发执行刚写的 last_run_* 用旧值盖回去
        LambdaUpdateWrapper<SysJob> wrapper = new LambdaUpdateWrapper<SysJob>()
                .eq(SysJob::getId, id)
                .set(SysJob::getParams, patch.getParams())
                .set(SysJob::getRemark, patch.getRemark());
        if (patch.getStatus() == SysJob.STATUS_DISABLED) {
            wrapper.set(SysJob::getNextRunAt, null);
        }
        patch.setParams(null);
        patch.setRemark(null);
        sysJobMapper.update(patch, wrapper);

        if (!existing.getJobCode().equals(request.getJobCode().trim())) {
            // 编码变了：调度器里的旧 key 必须一起撤掉，否则旧 Runnable 继续按旧 cron 跑
            jobScheduler.cancel(existing.getJobCode());
        }
        patch.setId(id);
        syncScheduler(patch);
    }

    @Override
    public void deleteJob(Long id) {
        SysJob job = requireJob(id);
        sysJobMapper.deleteById(id);
        jobScheduler.cancel(job.getJobCode());
        log.info("删除定时任务 {}", job.getJobCode());
    }

    @Override
    public void updateJobStatus(Long id, Integer status) {
        if (status == null || (status != SysJob.STATUS_DISABLED && status != SysJob.STATUS_ENABLED)) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "状态只能是 0（停用）或 1（启用）");
        }
        SysJob job = requireJob(id);
        if (status == SysJob.STATUS_ENABLED) {
            // cron 非法的任务开了也注册不上，直接拒绝；handler 缺失不拦——按约定执行时记失败日志
            assertCronValid(job.getCron());
        }

        SysJob patch = new SysJob();
        patch.setStatus(status);
        LambdaUpdateWrapper<SysJob> wrapper = new LambdaUpdateWrapper<SysJob>().eq(SysJob::getId, id);
        if (status == SysJob.STATUS_DISABLED) {
            wrapper.set(SysJob::getNextRunAt, null);
        }
        sysJobMapper.update(patch, wrapper);

        job.setStatus(status);
        syncScheduler(job);
    }

    @Override
    public Long runJob(Long id) {
        SysJob job = requireJob(id);
        String triggeredBy = SecurityUtils.currentUser().getUsername();
        return jobScheduler.runNow(job.getJobCode(), triggeredBy);
    }

    @Override
    public Page<JobLogResponse> listJobLogs(Long id, JobLogQueryRequest request) {
        requireJob(id);
        LambdaQueryWrapper<SysJobLog> wrapper = new LambdaQueryWrapper<SysJobLog>()
                .eq(SysJobLog::getJobId, id)
                .eq(request.getStatus() != null, SysJobLog::getStatus, request.getStatus())
                .orderByDesc(SysJobLog::getStartedAt, SysJobLog::getId);
        Page<SysJobLog> page = sysJobLogMapper.selectPage(
                new Page<>(request.getPageNum(), request.getPageSize()), wrapper);
        return convertPage(page, this::convertLog);
    }

    @Override
    public List<String> listHandlerBeans() {
        return Arrays.stream(applicationContext.getBeanNamesForType(JobHandler.class))
                .sorted()
                .collect(Collectors.toList());
    }

    // ------------------------------------------------------------------
    // 校验
    // ------------------------------------------------------------------

    private void validate(JobSaveRequest request) {
        assertCronValid(request.getCron());
        assertHandlerExists(request.getHandlerBean());
        try {
            JobParams.parse(request.getParams());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID, e.getMessage());
        }
    }

    private void assertCronValid(String cron) {
        if (!CronExpression.isValidExpression(cron)) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "cron 表达式不合法（需 Spring 6 位格式：秒 分 时 日 月 周）: " + cron);
        }
    }

    private void assertHandlerExists(String handlerBean) {
        if (!handlerAvailable(handlerBean)) {
            throw new BusinessException(ResultCode.PARAM_INVALID,
                    "任务处理器「" + handlerBean + "」不存在或未实现 JobHandler");
        }
    }

    private void assertCodeUnique(String jobCode, Long excludeId) {
        Long count = sysJobMapper.selectCount(new LambdaQueryWrapper<SysJob>()
                .eq(SysJob::getJobCode, jobCode)
                .ne(excludeId != null, SysJob::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "任务编码已存在: " + jobCode);
        }
    }

    private boolean handlerAvailable(String handlerBean) {
        if (!StringUtils.hasText(handlerBean)) {
            return false;
        }
        String name = handlerBean.trim();
        return applicationContext.containsBean(name) && applicationContext.isTypeMatch(name, JobHandler.class);
    }

    private SysJob requireJob(Long id) {
        SysJob job = id == null ? null : sysJobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, "定时任务不存在");
        }
        return job;
    }

    // ------------------------------------------------------------------
    // 调度同步 / 转换
    // ------------------------------------------------------------------

    private void syncScheduler(SysJob job) {
        if (Integer.valueOf(SysJob.STATUS_ENABLED).equals(job.getStatus())) {
            jobScheduler.reschedule(job);
        } else {
            jobScheduler.cancel(job.getJobCode());
        }
    }

    private static SysJob toEntity(JobSaveRequest request) {
        SysJob job = new SysJob();
        job.setJobName(request.getJobName().trim());
        job.setJobCode(request.getJobCode().trim());
        job.setHandlerBean(request.getHandlerBean().trim());
        job.setCron(request.getCron().trim());
        job.setParams(StringUtils.hasText(request.getParams()) ? request.getParams().trim() : null);
        job.setStatus(request.getStatus() == null ? SysJob.STATUS_DISABLED : request.getStatus());
        job.setRemark(StringUtils.hasText(request.getRemark()) ? request.getRemark().trim() : null);
        return job;
    }

    private static <S, T> Page<T> convertPage(Page<S> source, Function<S, T> converter) {
        Page<T> target = new Page<>(source.getCurrent(), source.getSize(), source.getTotal());
        target.setRecords(source.getRecords().stream().map(converter).collect(Collectors.toList()));
        return target;
    }

    private JobResponse convertToResponse(SysJob job) {
        return JobResponse.builder()
                .id(job.getId())
                .jobName(job.getJobName())
                .jobCode(job.getJobCode())
                .handlerBean(job.getHandlerBean())
                .handlerAvailable(handlerAvailable(job.getHandlerBean()))
                .cron(job.getCron())
                .params(job.getParams())
                .status(job.getStatus())
                .lastRunAt(job.getLastRunAt())
                .lastRunStatus(job.getLastRunStatus())
                .nextRunAt(job.getNextRunAt())
                .remark(job.getRemark())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt())
                .build();
    }

    private JobLogResponse convertLog(SysJobLog jobLog) {
        return JobLogResponse.builder()
                .id(jobLog.getId())
                .jobId(jobLog.getJobId())
                .jobCode(jobLog.getJobCode())
                .startedAt(jobLog.getStartedAt())
                .finishedAt(jobLog.getFinishedAt())
                .status(jobLog.getStatus())
                .statusName(logStatusName(jobLog.getStatus()))
                .message(jobLog.getMessage())
                .durationMs(jobLog.getDurationMs())
                .triggeredBy(jobLog.getTriggeredBy())
                .build();
    }

    private static String logStatusName(Integer status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case SysJobLog.STATUS_SUCCESS -> "成功";
            case SysJobLog.STATUS_FAIL -> "失败";
            case SysJobLog.STATUS_RUNNING -> "执行中";
            default -> String.valueOf(status);
        };
    }

}
