package com.school.module.job.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.job.dto.JobLogQueryRequest;
import com.school.module.job.dto.JobLogResponse;
import com.school.module.job.dto.JobQueryRequest;
import com.school.module.job.dto.JobResponse;
import com.school.module.job.dto.JobSaveRequest;
import com.school.module.job.service.JobService;
import com.school.module.log.annotation.OperationLog;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 定时任务管理控制器
 * <p>
 * ponytail: sys_job 没有 school_id、任务以系统身份跨校执行，所以全部接口只放给超管；
 * 写接口由 OperationLogAspect 自动审计。
 *
 * @author School System
 */
@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;

    /**
     * 分页查询任务
     */
    @GetMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Page<JobResponse>> listJobs(JobQueryRequest request) {
        return Result.success(jobService.listJobs(request));
    }

    /**
     * 容器内全部 JobHandler Bean 名，供新建/编辑下拉
     */
    @GetMapping("/handlers")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<List<String>> listHandlers() {
        return Result.success(jobService.listHandlerBeans());
    }

    /**
     * 新建任务
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(value = "新建定时任务", module = "job")
    public Result<Long> createJob(@Valid @RequestBody JobSaveRequest request) {
        return Result.success(jobService.createJob(request));
    }

    /**
     * 编辑任务
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(value = "编辑定时任务", module = "job")
    public Result<Void> updateJob(@PathVariable Long id, @Valid @RequestBody JobSaveRequest request) {
        jobService.updateJob(id, request);
        return Result.success();
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(value = "删除定时任务", module = "job")
    public Result<Void> deleteJob(@PathVariable Long id) {
        jobService.deleteJob(id);
        return Result.success();
    }

    /**
     * 启用 / 停用
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(value = "启停定时任务", module = "job")
    public Result<Void> updateJobStatus(@PathVariable Long id, @RequestParam Integer status) {
        jobService.updateJobStatus(id, status);
        return Result.success();
    }

    /**
     * 立即异步执行一次，返回 jobLogId
     */
    @PostMapping("/{id}/run")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(value = "立即执行定时任务", module = "job")
    public Result<Long> runJob(@PathVariable Long id) {
        return Result.success(jobService.runJob(id));
    }

    /**
     * 任务执行日志分页
     */
    @GetMapping("/{id}/logs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Page<JobLogResponse>> listJobLogs(@PathVariable Long id, JobLogQueryRequest request) {
        return Result.success(jobService.listJobLogs(id, request));
    }

}
