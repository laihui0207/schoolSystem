package com.school.module.notification.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.notification.dto.NotificationCreateRequest;
import com.school.module.notification.dto.NotificationQueryRequest;
import com.school.module.notification.dto.NotificationResponse;

import java.util.List;

/**
 * 通知服务接口
 *
 * @author School System
 */
public interface NotificationService {

    /**
     * 创建通知
     *
     * @param request 创建请求
     * @return 通知响应
     */
    NotificationResponse createNotification(NotificationCreateRequest request);

    /**
     * 批量创建通知
     *
     * @param userIds 用户ID列表
     * @param title 标题
     * @param content 内容
     * @param type 类型
     * @param relatedId 关联ID
     * @return 创建数量
     */
    Integer batchCreateNotifications(List<Long> userIds, String title, String content, Integer type, Long relatedId);

    /**
     * 查询通知详情
     *
     * @param id 通知ID
     * @return 通知响应
     */
    NotificationResponse getNotificationById(Long id);

    /**
     * 分页查询通知列表
     *
     * @param request 查询请求
     * @return 分页结果
     */
    Page<NotificationResponse> listNotifications(NotificationQueryRequest request);

    /**
     * 标记为已读
     *
     * @param id 通知ID
     */
    void markAsRead(Long id);

    /**
     * 批量标记为已读
     *
     * @param ids 通知ID列表
     */
    void batchMarkAsRead(List<Long> ids);

    /**
     * 标记全部为已读
     *
     * @param userId 用户ID
     */
    void markAllAsRead(Long userId);

    /**
     * 删除通知
     *
     * @param id 通知ID
     */
    void deleteNotification(Long id);

    /**
     * 获取未读通知数量
     *
     * @param userId 用户ID
     * @return 未读数量
     */
    Integer getUnreadCount(Long userId);

    /**
     * 发送成绩发布通知
     *
     * @param examId 考试ID
     * @param studentIds 学生ID列表
     */
    void sendScorePublishNotification(Long examId, List<Long> studentIds);

}
