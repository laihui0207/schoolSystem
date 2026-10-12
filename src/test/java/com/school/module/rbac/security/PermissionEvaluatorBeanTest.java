package com.school.module.rbac.security;

import com.school.module.user.enums.UserRole;
import com.school.security.model.LoginUser;
import com.school.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PermissionEvaluatorBean 单元测试（契约 P）
 *
 * @author School System
 */
@DisplayName("权限点鉴权 Bean 测试")
class PermissionEvaluatorBeanTest {

    private PermissionEvaluatorBean perm;

    @BeforeEach
    void setUp() {
        perm = new PermissionEvaluatorBean();
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    @DisplayName("应该恒通过_当当前用户是超级管理员")
    void shouldAllow_WhenSuperAdmin_ThenAlwaysTrue() {
        TestAuth.asSuperAdmin();

        assertThat(perm.has("score:review")).isTrue();
        assertThat(perm.has("not:exist")).isTrue();
        assertThat(perm.hasAny("a", "b")).isTrue();
    }

    @Test
    @DisplayName("应该按 authorities 判定_当当前用户是校管理员")
    void shouldCheckAuthorities_WhenSchoolAdmin_ThenMatchExactCode() {
        TestAuth.authenticate(LoginUser.builder()
                .userId(100L)
                .username("admin_school1")
                .schoolId(1L)
                .userType(UserRole.ADMIN.getUserType())
                .authorities(List.of(
                        new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN"),
                        new SimpleGrantedAuthority("score:review"),
                        new SimpleGrantedAuthority("sys:role:edit")))
                .build());

        assertThat(perm.has("score:review")).isTrue();
        assertThat(perm.has("sys:role:edit")).isTrue();
        assertThat(perm.has("score:publish")).isFalse();
        assertThat(perm.has("score:rev")).as("前缀不算命中").isFalse();
        assertThat(perm.hasAny("score:publish", "score:review")).isTrue();
        assertThat(perm.hasAny("score:publish", "sys:user:add")).isFalse();
    }

    @Test
    @DisplayName("应该返回 false_当未登录或权限码为空")
    void shouldDeny_WhenUnauthenticatedOrBlankCode_ThenFalse() {
        assertThat(perm.has("score:review")).isFalse();
        assertThat(perm.hasAny()).isFalse();
        assertThat(perm.hasAny((String[]) null)).isFalse();

        TestAuth.asSchoolAdmin(1L);
        assertThat(perm.has("")).isFalse();
        assertThat(perm.has(null)).isFalse();
    }

}
