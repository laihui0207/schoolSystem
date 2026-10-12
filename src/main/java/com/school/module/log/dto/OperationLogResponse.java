package com.school.module.log.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 操作日志响应
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogResponse {

    private Long id;

    private Long userId;

    private String username;

    private String operation;

    private String method;

    /**
     * 请求参数 JSON 原文（已打码）
     */
    private String params;

    private String ip;

    private String location;

    /**
     * 1-成功，0-失败
     */
    private Integer status;

    private String statusName;

    private String errorMsg;

    private Long executeTime;

    private LocalDateTime createdAt;

}
