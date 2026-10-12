package com.school.module.job.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 定时任务执行日志（sys_job_log）
 * <p>
 * ponytail: 与 OperationLog 一样不继承 BaseEntity——表没有 updated_at / deleted 列，日志只追加、不软删；
 * started_at 由调度器插入时手动赋值。
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_job_log")
public class SysJobLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 执行失败 */
    public static final int STATUS_FAIL = 0;
    /** 执行成功 */
    public static final int STATUS_SUCCESS = 1;
    /** 执行中：立即执行先插行再异步跑，接口据此先返回 jobLogId */
    public static final int STATUS_RUNNING = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long jobId;

    private String jobCode;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    /**
     * 1-成功，0-失败，2-执行中
     */
    private Integer status;

    /**
     * handler 返回的摘要或异常消息（截 2000）
     */
    private String message;

    private Long durationMs;

    /**
     * "cron" 表示调度触发，否则为手动触发者用户名
     */
    private String triggeredBy;

}
