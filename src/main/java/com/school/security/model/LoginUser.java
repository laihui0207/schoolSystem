package com.school.security.model;

import com.school.module.user.enums.UserRole;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 登录用户主体
 * <p>
 * 替代 Spring Security 默认的 User，额外携带租户身份（schoolId）与教师身份（teacherId）。
 * 数据隔离依赖这些字段：没有它们，服务端无法在查询时约束租户范围。
 *
 * @author School System
 */
@Getter
@Builder
public class LoginUser implements UserDetails, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户ID（sys_user.id）
     */
    private final Long userId;

    private final String username;

    private final String password;

    /**
     * 所属学校ID，平台级超管为 null
     */
    private final Long schoolId;

    /**
     * 用户类型（sys_user.user_type）
     */
    private final Integer userType;

    /**
     * 关联的教师ID（teacher.id），非教师账号为 null
     */
    private final Long teacherId;

    /**
     * 关联的学生ID（student.id），非学生账号为 null
     */
    private final Long studentId;

    /**
     * 是否班主任：教师账号登录时查 class.head_teacher_id 确定
     */
    private final boolean headTeacher;

    private final Collection<? extends GrantedAuthority> authorities;

    /**
     * 是否超级管理员：可跨学校访问，租户约束对其不生效
     */
    public boolean isSuperAdmin() {
        return UserRole.SUPER_ADMIN.getUserType().equals(userType);
    }

    /**
     * 是否校管理员
     */
    public boolean isSchoolAdmin() {
        return UserRole.ADMIN.getUserType().equals(userType);
    }

    /**
     * 是否教师（任课老师或班主任）：查询需进一步收窄到其任教班级
     */
    public boolean isTeacher() {
        return UserRole.TEACHER.getUserType().equals(userType);
    }

    /**
     * 是否班主任：班主任可审核成绩、管理班级学生名单
     */
    public boolean isHeadTeacher() {
        return isTeacher() && headTeacher;
    }

    /**
     * 是否学生：只能查询自身成绩
     */
    public boolean isStudent() {
        return UserRole.STUDENT.getUserType().equals(userType);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities == null ? List.of() : authorities;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

}
