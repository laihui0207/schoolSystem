package com.school.module.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.user.dto.UserCreateRequest;
import com.school.module.user.dto.UserQueryRequest;
import com.school.module.user.dto.UserResponse;
import com.school.module.user.dto.UserUpdateRequest;

/**
 * 用户服务接口
 *
 * @author School System
 */
public interface UserService {

    /**
     * 创建用户
     *
     * @param request 创建请求
     * @return 用户响应
     */
    UserResponse createUser(UserCreateRequest request);

    /**
     * 根据ID查询用户详情
     *
     * @param id 用户ID
     * @return 用户响应
     */
    UserResponse getUserById(Long id);

    /**
     * 更新用户信息
     *
     * @param id 用户ID
     * @param request 更新请求
     * @return 用户响应
     */
    UserResponse updateUser(Long id, UserUpdateRequest request);

    /**
     * 更新用户状态
     *
     * @param id 用户ID
     * @param status 状态：0-禁用，1-启用
     */
    void updateUserStatus(Long id, Integer status);

    /**
     * 重置用户密码
     *
     * @param id 用户ID
     * @param password 新密码（明文）
     */
    void resetPassword(Long id, String password);

    /**
     * 删除用户（逻辑删除）
     *
     * @param id 用户ID
     */
    void deleteUser(Long id);

    /**
     * 分页查询用户列表
     *
     * @param request 查询请求
     * @return 分页结果
     */
    Page<UserResponse> listUsers(UserQueryRequest request);

}
