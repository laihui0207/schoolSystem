package com.school.security.util;

import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.user.enums.UserRole;
import com.school.security.model.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 当前登录用户工具测试
 * <p>
 * 核心断言：身份取不到时必须抛异常。若退化为返回 null，
 * 调用方的租户条件会静默消失，隔离随之失效。
 *
 * @author School System
 */
@DisplayName("当前登录用户工具测试")
class SecurityUtilsTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate(LoginUser loginUser) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, loginUser.getAuthorities()));
    }

    private LoginUser.LoginUserBuilder teacher() {
        return LoginUser.builder()
                .userId(10L)
                .username("teacher01")
                .schoolId(2L)
                .userType(UserRole.TEACHER.getUserType())
                .teacherId(77L)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_TEACHER")));
    }

    @Test
    @DisplayName("应该返回当前用户的身份字段")
    void shouldReturnCurrentUserFields() {
        // Given
        authenticate(teacher().build());

        // Then
        assertEquals(10L, SecurityUtils.currentUserId());
        assertEquals(2L, SecurityUtils.currentSchoolId());
        assertEquals(77L, SecurityUtils.currentTeacherId());
        assertTrue(SecurityUtils.isTeacher());
        assertFalse(SecurityUtils.isSuperAdmin());
    }

    @Test
    @DisplayName("未认证时应该抛出未授权异常而非返回空")
    void shouldThrowWhenNotAuthenticated() {
        // Given: 未设置任何认证信息
        // Then
        BusinessException ex = assertThrows(BusinessException.class, SecurityUtils::currentSchoolId);
        assertEquals(ResultCode.UNAUTHORIZED.getCode(), ex.getCode());
        assertThrows(BusinessException.class, SecurityUtils::currentUserId);
        assertFalse(SecurityUtils.isAuthenticated());
    }

    @Test
    @DisplayName("匿名认证不应被视为已登录")
    void shouldRejectAnonymousAuthentication() {
        // Given: 匿名 token 的主体是字符串，不是 LoginUser
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));

        // Then
        assertFalse(SecurityUtils.isAuthenticated());
        assertThrows(BusinessException.class, SecurityUtils::currentUser);
    }

    @Test
    @DisplayName("超级管理员无学校归属时取学校ID应该抛异常")
    void shouldThrowWhenSuperAdminHasNoSchool() {
        // Given
        authenticate(LoginUser.builder()
                .userId(1L)
                .username("admin")
                .schoolId(null)
                .userType(UserRole.SUPER_ADMIN.getUserType())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")))
                .build());

        // Then: 必须先用 isSuperAdmin 分流，直接取会被拒绝而不是放行全库
        assertTrue(SecurityUtils.isSuperAdmin());
        BusinessException ex = assertThrows(BusinessException.class, SecurityUtils::currentSchoolId);
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    @DisplayName("非教师账号取教师ID应该抛异常")
    void shouldThrowWhenNoTeacherProfile() {
        // Given
        authenticate(teacher().userType(UserRole.SCHOOL_ADMIN.getUserType()).teacherId(null).build());

        // Then
        assertFalse(SecurityUtils.isTeacher());
        assertThrows(BusinessException.class, SecurityUtils::currentTeacherId);
    }

}
