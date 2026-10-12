package com.school.module.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户响应（不包含密码）
 *
 * @author School System
 */
@Data
@Builder
public class UserResponse {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 角色：super_admin / admin / teacher / student / parent
     */
    private String role;

    /**
     * 用户类型: 1-超级管理员 2-学校管理员 3-教师 4-学生 5-家长
     */
    private Integer userType;

    /**
     * 所属学校ID
     */
    private Long schoolId;

    /**
     * 所属学校名称
     */
    private String schoolName;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 状态: 0-禁用 1-启用
     */
    private Integer status;

    /**
     * 最后登录时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastLoginAt;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

}
