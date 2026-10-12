package com.school.module.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.user.dto.UserCreateRequest;
import com.school.module.user.dto.UserPasswordRequest;
import com.school.module.user.dto.UserQueryRequest;
import com.school.module.user.dto.UserResponse;
import com.school.module.user.dto.UserUpdateRequest;
import com.school.module.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器
 * <p>
 * 写操作仅超级管理员可用（authority 为 ROLE_SUPER_ADMIN，见 UserDetailsServiceImpl）
 *
 * @author School System
 */
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 创建用户
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<UserResponse> createUser(@Valid @RequestBody UserCreateRequest request) {
        UserResponse response = userService.createUser(request);
        return Result.success(response);
    }

    /**
     * 查询用户详情
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Result<UserResponse> getUserById(@PathVariable Long id) {
        UserResponse response = userService.getUserById(id);
        return Result.success(response);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<UserResponse> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateRequest request) {
        UserResponse response = userService.updateUser(id, request);
        return Result.success(response);
    }

    /**
     * 更新用户状态（启用/禁用）
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Void> updateUserStatus(
            @PathVariable Long id,
            @RequestParam Integer status) {
        userService.updateUserStatus(id, status);
        return Result.success();
    }

    /**
     * 重置用户密码
     */
    @PatchMapping("/{id}/password")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Void> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody UserPasswordRequest request) {
        userService.resetPassword(id, request.getPassword());
        return Result.success();
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Result<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return Result.success();
    }

    /**
     * 分页查询用户列表
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN')")
    public Result<Page<UserResponse>> listUsers(UserQueryRequest request) {
        Page<UserResponse> page = userService.listUsers(request);
        return Result.success(page);
    }

}
