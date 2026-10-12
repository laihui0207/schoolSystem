package com.school.module.user.service;

import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.auth.service.RefreshTokenStore;
import com.school.module.school.mapper.SchoolMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.user.dto.UserCreateRequest;
import com.school.module.user.dto.UserQueryRequest;
import com.school.module.user.dto.UserResponse;
import com.school.module.user.dto.UserUpdateRequest;
import com.school.module.user.entity.SysUser;
import com.school.module.user.mapper.SysUserMapper;
import com.school.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * User Service Unit Tests
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户服务测试")
class UserServiceTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SchoolMapper schoolMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @InjectMocks
    private UserServiceImpl userService;

    private UserCreateRequest createRequest;
    private SysUser mockUser;

    @BeforeEach
    void setUp() {
        TestAuth.asSchoolAdmin(1L);

        createRequest = new UserCreateRequest();
        createRequest.setUsername("teacher01");
        createRequest.setPassword("secret123");
        createRequest.setRealName("张老师");
        createRequest.setRole("teacher");
        createRequest.setPhone("");

        mockUser = new SysUser();
        mockUser.setId(1L);
        mockUser.setUsername("admin");
        mockUser.setPassword("$2a$10$existing");
        mockUser.setUserType(1);
        mockUser.setStatus(1);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("创建用户 - 密码加密入库、空手机号归一化、角色映射")
    void createUser_EncodesPasswordAndMapsRole() {
        // Given
        when(sysUserMapper.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("secret123")).thenReturn("$2a$10$encoded");
        when(sysUserMapper.insert(any(SysUser.class))).thenReturn(1);
        when(sysUserMapper.insertUserRole(any(), eq("ROLE_TEACHER"))).thenReturn(1);

        // When
        UserResponse response = userService.createUser(createRequest);

        // Then
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).insert(captor.capture());
        SysUser saved = captor.getValue();
        assertThat(saved.getPassword()).isEqualTo("$2a$10$encoded").isNotEqualTo("secret123");
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getUserType()).isEqualTo(3);
        assertThat(saved.getStatus()).isEqualTo(1);

        assertThat(response.getRole()).isEqualTo("teacher");
        assertThat(response.getUsername()).isEqualTo("teacher01");
    }

    @Test
    @DisplayName("创建用户 - 用户名已存在")
    void createUser_DuplicateUsername() {
        // Given
        when(sysUserMapper.selectCount(any())).thenReturn(1L);

        // When & Then
        assertThatThrownBy(() -> userService.createUser(createRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessage("用户名已存在");
        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("创建用户 - 角色无效")
    void createUser_InvalidRole() {
        // Given
        createRequest.setRole("hacker");

        // When & Then
        assertThatThrownBy(() -> userService.createUser(createRequest))
                .isInstanceOf(BusinessException.class);
        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("重置密码 - 加密后更新")
    void resetPassword_EncodesBeforeUpdate() {
        // Given
        when(sysUserMapper.selectById(1L)).thenReturn(mockUser);
        when(passwordEncoder.encode("newpass123")).thenReturn("$2a$10$new");
        when(sysUserMapper.updateById(any(SysUser.class))).thenReturn(1);

        // When
        userService.resetPassword(1L, "newpass123");

        // Then
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getPassword()).isEqualTo("$2a$10$new");
        // 契约 T：重置密码后作废该用户的 refresh token
        verify(refreshTokenStore).revokeByUserId(1L);
    }

    @Test
    @DisplayName("禁用用户 - 同时作废其 refresh token")
    void updateUserStatus_DisableRevokesRefreshToken() {
        // Given
        when(sysUserMapper.selectById(1L)).thenReturn(mockUser);
        when(sysUserMapper.updateById(any(SysUser.class))).thenReturn(1);

        // When
        userService.updateUserStatus(1L, 0);

        // Then
        verify(refreshTokenStore).revokeByUserId(1L);
    }

    @Test
    @DisplayName("启用用户 - 不作废 refresh token")
    void updateUserStatus_EnableKeepsRefreshToken() {
        // Given
        when(sysUserMapper.selectById(1L)).thenReturn(mockUser);
        when(sysUserMapper.updateById(any(SysUser.class))).thenReturn(1);

        // When
        userService.updateUserStatus(1L, 1);

        // Then
        verify(refreshTokenStore, never()).revokeByUserId(any());
    }

    @Test
    @DisplayName("用户详情 - 校管理员查他校用户应视为不存在")
    void getUserById_SchoolAdminCannotReadOtherSchoolUser() {
        // Given: 当前身份属于学校 1，目标用户属于学校 2
        mockUser.setSchoolId(2L);
        when(sysUserMapper.selectById(1L)).thenReturn(mockUser);

        // When & Then
        assertThatThrownBy(() -> userService.getUserById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ResultCode.USER_NOT_FOUND.getMessage());
        verify(schoolMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("用户详情 - 校管理员查平台超管（无学校）应视为不存在")
    void getUserById_SchoolAdminCannotReadPlatformUser() {
        // Given: 目标是 school_id 为空的平台级账号
        mockUser.setSchoolId(null);
        when(sysUserMapper.selectById(1L)).thenReturn(mockUser);

        // When & Then
        assertThatThrownBy(() -> userService.getUserById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ResultCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("用户详情 - 校管理员可查本校用户")
    void getUserById_SchoolAdminCanReadOwnSchoolUser() {
        // Given
        mockUser.setSchoolId(1L);
        when(sysUserMapper.selectById(1L)).thenReturn(mockUser);

        // When
        UserResponse response = userService.getUserById(1L);

        // Then
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getSchoolId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("用户详情 - 超管可查任意学校用户")
    void getUserById_SuperAdminCanReadAnyUser() {
        // Given
        TestAuth.asSuperAdmin();
        mockUser.setSchoolId(2L);
        when(sysUserMapper.selectById(1L)).thenReturn(mockUser);

        // When
        UserResponse response = userService.getUserById(1L);

        // Then
        assertThat(response.getSchoolId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("创建用户 - 超管为非超管角色建账号必须指定学校")
    void createUser_SuperAdminMustSpecifySchoolForNonSuperAdminRole() {
        // Given: 超管建 teacher 账号但没传 schoolId
        TestAuth.asSuperAdmin();
        createRequest.setSchoolId(null);
        when(sysUserMapper.selectCount(any())).thenReturn(0L);

        // When & Then
        assertThatThrownBy(() -> userService.createUser(createRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessage("非超级管理员角色必须指定学校");
        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("创建用户 - 超管可建无学校归属的平台级超管账号")
    void createUser_SuperAdminCanCreatePlatformSuperAdmin() {
        // Given
        TestAuth.asSuperAdmin();
        createRequest.setRole("super_admin");
        createRequest.setSchoolId(null);
        when(sysUserMapper.selectCount(any())).thenReturn(0L);
        when(sysUserMapper.insert(any(SysUser.class))).thenReturn(1);
        when(sysUserMapper.insertUserRole(any(), eq("ROLE_SUPER_ADMIN"))).thenReturn(1);

        // When
        UserResponse response = userService.createUser(createRequest);

        // Then
        assertThat(response.getRole()).isEqualTo("super_admin");
        assertThat(response.getSchoolId()).isNull();
    }

    @Test
    @DisplayName("删除用户 - 用户不存在")
    void deleteUser_NotFound() {
        // Given
        when(sysUserMapper.selectById(99L)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> userService.deleteUser(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ResultCode.USER_NOT_FOUND.getMessage());
    }

    @Test
    @DisplayName("删除用户 - 禁止删除当前登录账号")
    void deleteUser_SelfRejected() {
        // Given
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("admin", null));
        when(sysUserMapper.selectById(1L)).thenReturn(mockUser);

        // When & Then
        assertThatThrownBy(() -> userService.deleteUser(1L))
                .isInstanceOf(BusinessException.class);
        verify(sysUserMapper, never()).deleteById(1L);
    }

    @Test
    @DisplayName("创建用户 - 校内管理员越权指定别校时应被拒绝")
    void createUser_SchoolAdminCannotChooseSchool() {
        // Given: 当前身份属于学校 1，请求却指定学校 999
        createRequest.setSchoolId(999L);
        when(sysUserMapper.selectCount(any())).thenReturn(0L);

        // When & Then: 不允许操作其他学校的数据
        assertThatThrownBy(() -> userService.createUser(createRequest))
                .isInstanceOf(BusinessException.class);

        verify(sysUserMapper, never()).insert(any(SysUser.class));
    }

    @Test
    @DisplayName("更新用户 - 校内管理员不能把用户划到别的学校")
    void updateUser_SchoolAdminCannotReassignSchool() {
        // Given: 目标用户属于学校 1，请求试图改到学校 999
        mockUser.setSchoolId(1L);
        when(sysUserMapper.selectById(1L)).thenReturn(mockUser);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setSchoolId(999L);

        // When
        userService.updateUser(1L, request);

        // Then
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).update(captor.capture(), any());
        assertThat(captor.getValue().getSchoolId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("用户列表 - 校内管理员传入别校ID也被强制限定为本校")
    void listUsers_SchoolAdminForcedToOwnSchool() {
        // Given: 当前身份属于学校 1，请求却指定学校 999
        UserQueryRequest request = new UserQueryRequest();
        request.setSchoolId(999L);
        when(sysUserMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10, 0));

        // When
        userService.listUsers(request);

        // Then: 查询条件被改写为本校
        assertThat(request.getSchoolId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("用户列表 - 超管可按任意学校过滤")
    void listUsers_SuperAdminKeepsRequestedSchool() {
        // Given
        TestAuth.asSuperAdmin();
        UserQueryRequest request = new UserQueryRequest();
        request.setSchoolId(999L);
        when(sysUserMapper.selectPage(any(), any())).thenReturn(new Page<>(1, 10, 0));

        // When
        userService.listUsers(request);

        // Then
        assertThat(request.getSchoolId()).isEqualTo(999L);
    }

    @Test
    @DisplayName("更新用户 - 超管可以改学校归属")
    void updateUser_SuperAdminCanReassignSchool() {
        // Given
        TestAuth.asSuperAdmin();
        mockUser.setSchoolId(1L);
        when(sysUserMapper.selectById(1L)).thenReturn(mockUser);
        UserUpdateRequest request = new UserUpdateRequest();
        request.setSchoolId(999L);

        // When
        userService.updateUser(1L, request);

        // Then
        ArgumentCaptor<SysUser> captor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).update(captor.capture(), any());
        assertThat(captor.getValue().getSchoolId()).isEqualTo(999L);
    }

}
