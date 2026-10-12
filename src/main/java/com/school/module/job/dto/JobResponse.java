package com.school.module.job.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 定时任务响应
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobResponse {

    private Long id;

    private String jobName;

    private String jobCode;

    private String handlerBean;

    /**
     * 容器里是否存在该 handler Bean：预置的 backupJobHandler 在备份模块未就绪时为 false，前端据此提示
     */
    private Boolean handlerAvailable;

    private String cron;

    private String params;

    /**
     * 0-停用，1-启用
     */
    private Integer status;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastRunAt;

    /**
     * 1-成功，0-失败
     */
    private Integer lastRunStatus;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime nextRunAt;

    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

}
