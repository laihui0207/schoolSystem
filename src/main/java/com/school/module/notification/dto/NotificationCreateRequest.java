package com.school.module.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 通知创建请求
 *
 * @author School System
 */
@Data
public class NotificationCreateRequest {

    /**
     * 用户ID
     */
    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 标题
     */
    @NotBlank(message = "标题不能为空")
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 类型：1-成绩发布，2-成绩预警，3-系统通知
     */
    @NotNull(message = "类型不能为空")
    private Integer type;

    /**
     * 关联ID
     */
    private Long relatedId;

}
