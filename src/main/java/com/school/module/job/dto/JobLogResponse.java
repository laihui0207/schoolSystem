package com.school.module.job.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 任务执行日志响应
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobLogResponse {

    private Long id;

    private Long jobId;

    private String jobCode;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startedAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime finishedAt;

    /**
     * 1-成功，0-失败，2-执行中
     */
    private Integer status;

    private String statusName;

    private String message;

    private Long durationMs;

    private String triggeredBy;

}
