package com.school.module.notification.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.notification.dto.NotificationCreateRequest;
import com.school.module.notification.dto.NotificationQueryRequest;
import com.school.module.notification.dto.NotificationResponse;
import com.school.module.notification.entity.Notification;
import com.school.module.notification.mapper.NotificationMapper;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 通知服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationResponse createNotification(NotificationCreateRequest request) {
        Notification notification = new Notification();
        notification.setUserId(request.getUserId());
        notification.setTitle(request.getTitle());
        notification.setContent(request.getContent());
        notification.setType(request.getType());
        notification.setIsRead(0);
        notification.setRelatedId(request.getRelatedId());

        notificationMapper.insert(notification);

        return convertToResponse(notification);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer batchCreateNotifications(List<Long> userIds, String title, String content, Integer type, Long relatedId) {
        int count = 0;
        for (Long userId : userIds) {
            Notification notification = new Notification();
            notification.setUserId(userId);
            notification.setTitle(title);
            notification.setContent(content);
            notification.setType(type);
            notification.setIsRead(0);
            notification.setRelatedId(relatedId);

            notificationMapper.insert(notification);
            count++;
        }
        return count;
    }

    @Override
    public NotificationResponse getNotificationById(Long id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException("通知不存在");
        }
        assertOwner(notification);
        return convertToResponse(notification);
    }

    @Override
    public Page<NotificationResponse> listNotifications(NotificationQueryRequest request) {
        // ponytail: notification 不在租户插件表清单内，收件人隔离只能在这里收口；忽略客户端传入的 userId
        request.setUserId(SecurityUtils.currentUserId());
        Page<Notification> page = new Page<>(request.getPageNum(), request.getPageSize());
        Page<Notification> resultPage = notificationMapper.selectPageByQuery(page, request);

        Page<NotificationResponse> responsePage = new Page<>(
                resultPage.getCurrent(),
                resultPage.getSize(),
                resultPage.getTotal()
        );

        List<NotificationResponse> responses = resultPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        responsePage.setRecords(responses);
        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAsRead(Long id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException("通知不存在");
        }
        assertOwner(notification);
        markRead(notification);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchMarkAsRead(List<Long> ids) {
        // ponytail: 原先 try/catch 吞掉所有异常，越权会被静默忽略；现在不存在的跳过、越权则抛出
        for (Long id : ids) {
            Notification notification = notificationMapper.selectById(id);
            if (notification == null) {
                continue;
            }
            assertOwner(notification);
            markRead(notification);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markAllAsRead(Long userId) {
        NotificationQueryRequest request = NotificationQueryRequest.builder()
                .userId(userId)
                .isRead(0)
                .build();

        Page<Notification> page = new Page<>(1, 1000);
        Page<Notification> resultPage = notificationMapper.selectPageByQuery(page, request);

        for (Notification notification : resultPage.getRecords()) {
            Notification updated = new Notification();
            updated.setId(notification.getId());
            updated.setUserId(notification.getUserId());
            updated.setTitle(notification.getTitle());
            updated.setContent(notification.getContent());
            updated.setType(notification.getType());
            updated.setIsRead(1);
            updated.setRelatedId(notification.getRelatedId());

            notificationMapper.updateById(updated);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotification(Long id) {
        Notification notification = notificationMapper.selectById(id);
        if (notification == null) {
            throw new BusinessException("通知不存在");
        }
        assertOwner(notification);
        notificationMapper.deleteById(id);
    }

    @Override
    public Integer getUnreadCount(Long userId) {
        return notificationMapper.countUnread(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendScorePublishNotification(Long examId, List<Long> studentIds) {
        String title = "成绩发布通知";
        String content = "您有新的考试成绩已发布，请登录查看。";

        batchCreateNotifications(studentIds, title, content, 1, examId);
    }

    /**
     * 校验通知归属：只有收件人本人或超级管理员可以读取/操作
     */
    private void assertOwner(Notification notification) {
        // ponytail: 通知 id 自增可枚举，不比对 user_id 任何登录用户都能读/删别人的通知
        if (SecurityUtils.isSuperAdmin()) {
            return;
        }
        if (!SecurityUtils.currentUserId().equals(notification.getUserId())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "无权操作他人的通知");
        }
    }

    /**
     * 标记单条通知为已读
     */
    private void markRead(Notification notification) {
        Notification updated = new Notification();
        updated.setId(notification.getId());
        updated.setUserId(notification.getUserId());
        updated.setTitle(notification.getTitle());
        updated.setContent(notification.getContent());
        updated.setType(notification.getType());
        updated.setIsRead(1);
        updated.setRelatedId(notification.getRelatedId());

        notificationMapper.updateById(updated);
    }

    /**
     * 转换为响应DTO
     */
    private NotificationResponse convertToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .title(notification.getTitle())
                .content(notification.getContent())
                .type(notification.getType())
                .typeName(getTypeName(notification.getType()))
                .isRead(notification.getIsRead())
                .relatedId(notification.getRelatedId())
                .createdAt(notification.getCreatedAt())
                .build();
    }

    /**
     * 获取类型名称
     */
    private String getTypeName(Integer type) {
        if (type == null) {
            return null;
        }
        return switch (type) {
            case 1 -> "成绩发布";
            case 2 -> "成绩预警";
            case 3 -> "系统通知";
            default -> "未知";
        };
    }

}
