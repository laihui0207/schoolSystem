package com.school.module.job.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.job.dto.JobLogQueryRequest;
import com.school.module.job.dto.JobLogResponse;
import com.school.module.job.dto.JobQueryRequest;
import com.school.module.job.dto.JobResponse;
import com.school.module.job.dto.JobSaveRequest;

import java.util.List;

/**
 * 定时任务管理服务
 *
 * @author School System
 */
public interface JobService {

    /**
     * 分页查询任务
     */
    Page<JobResponse> listJobs(JobQueryRequest request);

    /**
     * 新建任务；启用状态的任务立即注册到调度器
     *
     * @return 新任务 ID
     */
    Long createJob(JobSaveRequest request);

    /**
     * 编辑任务并按新状态 / 新 cron 重排调度
     */
    void updateJob(Long id, JobSaveRequest request);

    /**
     * 逻辑删除任务并取消调度（执行日志保留）
     */
    void deleteJob(Long id);

    /**
     * 启用 / 停用：1 注册调度，0 取消调度并清空 next_run_at
     */
    void updateJobStatus(Long id, Integer status);

    /**
     * 立即异步执行一次
     *
     * @return sys_job_log.id，可用 listJobLogs 轮询结果
     */
    Long runJob(Long id);

    /**
     * 分页查询某任务的执行日志（最近的在前）
     */
    Page<JobLogResponse> listJobLogs(Long id, JobLogQueryRequest request);

    /**
     * 容器内全部 JobHandler Bean 名（排序后），供前端下拉
     */
    List<String> listHandlerBeans();

}
