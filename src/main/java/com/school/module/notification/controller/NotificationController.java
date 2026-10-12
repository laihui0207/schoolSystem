package com.school.module.notification.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.notification.dto.NotificationCreateRequest;
import com.school.module.notification.dto.NotificationQueryRequest;
import com.school.module.notification.dto.NotificationResponse;
import com.school.module.notification.service.NotificationService;
import com.school.security.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 通知管理控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 创建通知
     */
    // ponytail: 手工发通知是管理动作，普通用户可指定任意 userId 伪造系统通知
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    @PostMapping
    public Result<NotificationResponse> createNotification(@Valid @RequestBody NotificationCreateRequest request) {
        NotificationResponse response = notificationService.createNotification(request);
        return Result.success(response);
    }

    /**
     * 查询通知详情
     */
    @GetMapping("/{id}")
    public Result<NotificationResponse> getNotificationById(@PathVariable Long id) {
        NotificationResponse response = notificationService.getNotificationById(id);
        return Result.success(response);
    }

    /**
     * 分页查询通知列表
     */
    @GetMapping
    public Result<Page<NotificationResponse>> listNotifications(NotificationQueryRequest request) {
        Page<NotificationResponse> page = notificationService.listNotifications(request);
        return Result.success(page);
    }

    /**
     * 标记为已读
     */
    @PatchMapping("/{id}/read")
    public Result<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return Result.success();
    }

    /**
     * 批量标记为已读
     */
    @PatchMapping("/batch-read")
    public Result<Void> batchMarkAsRead(@RequestBody List<Long> ids) {
        notificationService.batchMarkAsRead(ids);
        return Result.success();
    }

    /**
     * 标记全部为已读
     */
    @PatchMapping("/read-all")
    public Result<Void> markAllAsRead() {
        notificationService.markAllAsRead(SecurityUtils.currentUserId());
        return Result.success();
    }

    /**
     * 删除通知
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return Result.success();
    }

    /**
     * 获取未读通知数量
     */
    @GetMapping("/unread-count")
    public Result<Integer> getUnreadCount() {
        Integer count = notificationService.getUnreadCount(SecurityUtils.currentUserId());
        return Result.success(count);
    }

}
