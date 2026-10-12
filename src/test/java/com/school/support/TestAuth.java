package com.school.support;

import com.school.module.user.enums.UserRole;
import com.school.security.model.LoginUser;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

/**
 * 单元测试的登录身份桩
 * <p>
 * 租户隔离后写入路径都要从 SecurityContext 取学校，纯 Mockito 测试没有上下文会被
 * 判为未认证。此处集中构造，避免每个测试类各自拼 LoginUser。
 *
 * @author School System
 */
public final class TestAuth {

    private TestAuth() {
    }

    /**
     * 以指定学校的管理员身份登录
     */
    public static void asSchoolAdmin(Long schoolId) {
        authenticate(LoginUser.builder()
                .userId(100L)
                .username("admin_school" + schoolId)
                .schoolId(schoolId)
                .userType(UserRole.ADMIN.getUserType())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN")))
                .build());
    }

    /**
     * 以超级管理员身份登录（无学校归属，可跨校操作）
     */
    public static void asSuperAdmin() {
        authenticate(LoginUser.builder()
                .userId(1L)
                .username("superadmin")
                .schoolId(null)
                .userType(UserRole.SUPER_ADMIN.getUserType())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")))
                .build());
    }

    public static void authenticate(LoginUser loginUser) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }

}
