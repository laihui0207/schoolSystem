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
    @DisplayName("查询用学校ID：超管返回null放开全库，其他角色必须限定本校")
    void shouldReturnNullSchoolForQueryOnlyForSuperAdmin() {
        // Given: 超级管理员
        authenticate(LoginUser.builder()
                .userId(1L)
                .username("admin")
                .schoolId(null)
                .userType(UserRole.SUPER_ADMIN.getUserType())
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")))
                .build());

        // Then: null 表示不拼 school_id 条件，跨校可见
        assertNull(SecurityUtils.currentSchoolIdForQuery());

        // Given: 普通教师
        authenticate(teacher().build());

        // Then: 必须回落到本校，绝不能是 null
        assertEquals(2L, SecurityUtils.currentSchoolIdForQuery());
    }

    @Test
    @DisplayName("未登录时查询用学校ID也必须抛异常，不能退化为不限校")
    void shouldThrowForQuerySchoolWhenNotAuthenticated() {
        // Given: 未设置任何认证信息
        // Then: 若返回 null，匿名请求将读到全库
        assertThrows(BusinessException.class, SecurityUtils::currentSchoolIdForQuery);
    }

    @Test
    @DisplayName("非教师账号取教师ID应该抛异常")
    void shouldThrowWhenNoTeacherProfile() {
        // Given
        authenticate(teacher().userType(UserRole.ADMIN.getUserType()).teacherId(null).build());

        // Then
        assertFalse(SecurityUtils.isTeacher());
        assertThrows(BusinessException.class, SecurityUtils::currentTeacherId);
    }

    // ------------------------------------------------------------------
    // 契约 L：canViewStudent / isParent / currentChildStudentIds
    // ------------------------------------------------------------------

    private LoginUser.LoginUserBuilder student(Long studentId) {
        return LoginUser.builder()
                .userId(20L)
                .username("student01")
                .schoolId(2L)
                .userType(UserRole.STUDENT.getUserType())
                .studentId(studentId)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));
    }

    private LoginUser.LoginUserBuilder parent(List<Long> childStudentIds) {
        return LoginUser.builder()
                .userId(30L)
                .username("parent01")
                .schoolId(2L)
                .userType(UserRole.PARENT.getUserType())
                .childStudentIds(childStudentIds)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_PARENT")));
    }

    @Test
    @DisplayName("canViewStudent：学生只能看本人")
    void shouldAllowOnlySelf_WhenStudent_ThenCanViewStudent() {
        // Given
        authenticate(student(42L).build());

        // Then
        assertTrue(SecurityUtils.isStudent());
        assertFalse(SecurityUtils.isParent());
        assertTrue(SecurityUtils.canViewStudent(42L));
        assertFalse(SecurityUtils.canViewStudent(43L));
        assertFalse(SecurityUtils.canViewStudent(null));
    }

    @Test
    @DisplayName("canViewStudent：家长只能看已绑定的孩子")
    void shouldAllowOnlyBoundChildren_WhenParent_ThenCanViewStudent() {
        // Given
        authenticate(parent(List.of(42L, 43L)).build());

        // Then
        assertTrue(SecurityUtils.isParent());
        assertFalse(SecurityUtils.isStudent());
        assertTrue(SecurityUtils.canViewStudent(42L));
        assertTrue(SecurityUtils.canViewStudent(43L));
        assertFalse(SecurityUtils.canViewStudent(44L));
        assertFalse(SecurityUtils.canViewStudent(null));
        assertEquals(List.of(42L, 43L), SecurityUtils.currentChildStudentIds());
    }

    @Test
    @DisplayName("canViewStudent：未绑定孩子的家长什么都看不到，childStudentIds 为空列表而非 null")
    void shouldDenyAll_WhenParentHasNoChildren_ThenEmptyListNotNull() {
        // Given: builder 未设置 childStudentIds（与 TestAuth 的用法一致）
        authenticate(parent(null).build());

        // Then
        assertNotNull(SecurityUtils.currentChildStudentIds());
        assertTrue(SecurityUtils.currentChildStudentIds().isEmpty());
        assertFalse(SecurityUtils.canViewStudent(42L));
    }

    @Test
    @DisplayName("canViewStudent：教师/管理员放行，取孩子列表则拒绝")
    void shouldAllowAny_WhenTeacherOrAdmin_ThenCanViewStudentButNoChildren() {
        // Given: 教师
        authenticate(teacher().build());

        // Then: 班级范围另由 TeacherScopeService 收窄，这里只做身份维度放行
        assertTrue(SecurityUtils.canViewStudent(42L));
        assertTrue(SecurityUtils.canViewStudent(null));
        BusinessException ex = assertThrows(BusinessException.class, SecurityUtils::currentChildStudentIds);
        assertEquals(ResultCode.FORBIDDEN.getCode(), ex.getCode());

        // Given: 校管理员
        authenticate(teacher().userType(UserRole.ADMIN.getUserType()).teacherId(null).build());

        // Then
        assertTrue(SecurityUtils.canViewStudent(42L));
        assertFalse(SecurityUtils.isParent());
    }

}
