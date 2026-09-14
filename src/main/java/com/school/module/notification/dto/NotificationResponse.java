package com.school.module.notification.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知响应
 *
 * @author School System
 */
@Data
@Builder
public class NotificationResponse {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 类型：1-成绩发布，2-成绩预警，3-系统通知
     */
    private Integer type;

    /**
     * 类型名称
     */
    private String typeName;

    /**
     * 是否已读：0-未读，1-已读
     */
    private Integer isRead;

    /**
     * 关联ID（如考试ID、成绩ID等）
     */
    private Long relatedId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

}
