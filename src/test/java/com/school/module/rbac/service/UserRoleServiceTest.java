package com.school.module.rbac.service;

import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.rbac.dto.RoleResponse;
import com.school.module.rbac.dto.UserRolesUpdateRequest;
import com.school.module.rbac.mapper.SysRoleMapper;
import com.school.module.user.entity.SysRole;
import com.school.module.user.entity.SysUser;
import com.school.module.user.mapper.SysUserMapper;
import com.school.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 用户附加角色服务测试
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("用户附加角色服务测试")
class UserRoleServiceTest {

    @Mock
    private SysUserMapper userMapper;

    @Mock
    private SysRoleMapper roleMapper;

    @InjectMocks
    private UserRoleServiceImpl service;

    private SysUser teacher;

    @BeforeEach
    void setUp() {
        TestAuth.asSchoolAdmin(7L);
        teacher = new SysUser();
        teacher.setId(30L);
        teacher.setUsername("t01");
        teacher.setUserType(3);
        teacher.setSchoolId(7L);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    private static SysRole role(long id, String code, Long schoolId, int builtin) {
        SysRole r = new SysRole();
        r.setId(id);
        r.setRoleCode(code);
        r.setRoleName(code);
        r.setSchoolId(schoolId);
        r.setIsBuiltin(builtin);
        return r;
    }

    @Test
    @DisplayName("应该拒绝_当附加其他内置角色（内置角色由 user_type 决定）")
    void shouldReject_WhenExtraBuiltinRoleRequested_ThenForbidden() {
        when(userMapper.selectById(30L)).thenReturn(teacher);
        when(roleMapper.selectOne(any())).thenReturn(role(2L, "ROLE_SCHOOL_ADMIN", null, 1));

        assertThatThrownBy(() -> service.updateUserRoles(30L,
                UserRolesUpdateRequest.builder().roleCodes(List.of("ROLE_SCHOOL_ADMIN")).build()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
        verify(userMapper, never()).deleteUserRolesByUserId(anyLong());
    }

    @Test
    @DisplayName("应该始终保留 user_type 对应内置角色_当请求为空数组")
    void shouldKeepBaseRole_WhenEmptyRequest_ThenRebindTeacherOnly() {
        when(userMapper.selectById(30L)).thenReturn(teacher);
        when(userMapper.insertUserRole(30L, "ROLE_TEACHER")).thenReturn(1);
        when(roleMapper.selectByUserId(30L)).thenReturn(List.of(role(3L, "ROLE_TEACHER", null, 1)));

        List<RoleResponse> result = service.updateUserRoles(30L,
                UserRolesUpdateRequest.builder().roleCodes(List.of()).build());

        verify(userMapper).deleteUserRolesByUserId(30L);
        verify(userMapper).insertUserRole(30L, "ROLE_TEACHER");
        assertThat(result).extracting(RoleResponse::getRoleCode).containsExactly("ROLE_TEACHER");
    }

    @Test
    @DisplayName("应该绑定基础角色加本校自定义角色_当校管理员分配本校角色")
    void shouldBindCustomRole_WhenSameSchool_ThenInsertBoth() {
        when(userMapper.selectById(30L)).thenReturn(teacher);
        when(roleMapper.selectOne(any())).thenReturn(role(9L, "head_teacher", 7L, 0));
        when(userMapper.insertUserRole(anyLong(), anyString())).thenReturn(1);
        when(roleMapper.selectByUserId(30L)).thenReturn(List.of());

        service.updateUserRoles(30L, UserRolesUpdateRequest.builder()
                .roleCodes(List.of("head_teacher", "ROLE_TEACHER", " head_teacher ")).build());

        verify(userMapper).insertUserRole(30L, "ROLE_TEACHER");
        verify(userMapper).insertUserRole(30L, "head_teacher");
    }

    @Test
    @DisplayName("应该拒绝_当校管理员分配他校或平台级自定义角色")
    void shouldReject_WhenSchoolAdminAssignsForeignRole_ThenForbidden() {
        when(userMapper.selectById(30L)).thenReturn(teacher);
        when(roleMapper.selectOne(any())).thenReturn(role(9L, "auditor", null, 0));

        assertThatThrownBy(() -> service.updateUserRoles(30L,
                UserRolesUpdateRequest.builder().roleCodes(List.of("auditor")).build()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("应该视为用户不存在_当校管理员访问他校用户")
    void shouldHide_WhenUserInOtherSchool_ThenUserNotFound() {
        teacher.setSchoolId(8L);
        when(userMapper.selectById(30L)).thenReturn(teacher);

        assertThatThrownBy(() -> service.getUserRoles(30L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.USER_NOT_FOUND.getCode());
    }

}
