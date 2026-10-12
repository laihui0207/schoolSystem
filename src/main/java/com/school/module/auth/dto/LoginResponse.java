package com.school.module.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

/**
 * 登录响应DTO
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 访问令牌
     */
    private String accessToken;

    /**
     * 刷新令牌
     */
    private String refreshToken;

    /**
     * 令牌类型
     */
    @Builder.Default
    private String tokenType = "Bearer";

    /**
     * 过期时间（秒）
     */
    private Long expiresIn;

    /**
     * 用户信息
     */
    private UserInfo userInfo;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        /**
         * 用户ID
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
         * 头像
         */
        private String avatar;

        /**
         * 用户类型
         */
        private Integer userType;

        /**
         * 学校ID
         */
        private Long schoolId;

        /**
         * 关联学生ID（student.id），非学生账号为 null
         */
        private Long studentId;

        /**
         * 关联教师ID（teacher.id），非教师账号为 null
         */
        private Long teacherId;

        /**
         * 是否班主任
         */
        private boolean headTeacher;

        /**
         * 角色编码：super_admin / admin / teacher / student / parent
         */
        private String role;

        /**
         * 家长绑定的学生ID列表（student.id），非家长为空列表（契约 L）
         */
        private List<Long> childStudentIds;
    }

}
