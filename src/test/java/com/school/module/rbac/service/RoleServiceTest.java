package com.school.module.rbac.service;

import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.rbac.dto.RoleCreateRequest;
import com.school.module.rbac.dto.RolePermissionsRequest;
import com.school.module.rbac.dto.RoleResponse;
import com.school.module.rbac.dto.RoleUpdateRequest;
import com.school.module.rbac.entity.SysPermission;
import com.school.module.rbac.mapper.SysPermissionMapper;
import com.school.module.rbac.mapper.SysRoleMapper;
import com.school.module.rbac.mapper.SysRolePermissionMapper;
import com.school.module.school.mapper.SchoolMapper;
import com.school.module.user.entity.SysRole;
import com.school.module.user.enums.UserRole;
import com.school.security.model.LoginUser;
import com.school.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 角色服务测试：内置角色保护、校管理员权限分配边界
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("角色服务测试")
class RoleServiceTest {

    @Mock
    private SysRoleMapper roleMapper;

    @Mock
    private SysPermissionMapper permissionMapper;

    @Mock
    private SysRolePermissionMapper rolePermissionMapper;

    @Mock
    private SchoolMapper schoolMapper;

    @InjectMocks
    private RoleServiceImpl service;

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

    private static SysPermission perm(long id, String code) {
        SysPermission p = new SysPermission();
        p.setId(id);
        p.setPermissionCode(code);
        p.setPermissionName(code);
        p.setResourceType(3);
        return p;
    }

    /** 学校 7 的校管理员，authorities 为 ROLE_SCHOOL_ADMIN + 指定的权限码 */
    private static void asSchoolAdminHolding(String... codes) {
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_SCHOOL_ADMIN"));
        for (String c : codes) {
            authorities.add(new SimpleGrantedAuthority(c));
        }
        TestAuth.authenticate(LoginUser.builder()
                .userId(100L).username("admin7").schoolId(7L)
                .userType(UserRole.ADMIN.getUserType())
                .authorities(authorities)
                .build());
    }

    @Nested
    @DisplayName("内置角色保护")
    class BuiltinProtection {

        @Test
        @DisplayName("应该拒绝删除_当角色为内置")
        void shouldRejectDelete_WhenBuiltin_ThenForbidden() {
            TestAuth.asSuperAdmin();
            when(roleMapper.selectById(3L)).thenReturn(role(3L, "ROLE_TEACHER", null, 1));

            assertThatThrownBy(() -> service.deleteRole(3L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
            verify(roleMapper, never()).deleteById(anyLong());
            verify(rolePermissionMapper, never()).deleteByRoleId(anyLong());
        }

        @Test
        @DisplayName("应该拒绝修改编码_当角色为内置（名称与描述可改）")
        void shouldRejectCodeChange_WhenBuiltin_ThenForbidden() {
            TestAuth.asSuperAdmin();
            when(roleMapper.selectById(3L)).thenReturn(role(3L, "ROLE_TEACHER", null, 1));

            assertThatThrownBy(() -> service.updateRole(3L,
                    RoleUpdateRequest.builder().roleCode("teacher_v2").build()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
            verify(roleMapper, never()).updateById(any(SysRole.class));
        }

        @Test
        @DisplayName("应该拒绝删除_当角色仍有在用用户")
        void shouldRejectDelete_WhenUsersBound_ThenDataInUse() {
            TestAuth.asSuperAdmin();
            when(roleMapper.selectById(9L)).thenReturn(role(9L, "auditor", null, 0));
            when(roleMapper.countActiveUsersByRoleId(9L)).thenReturn(2L);

            assertThatThrownBy(() -> service.deleteRole(9L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.DATA_IN_USE.getCode());
            verify(roleMapper, never()).deleteById(anyLong());
        }

        @Test
        @DisplayName("应该删除并清理权限绑定_当自定义角色无用户")
        void shouldDelete_WhenCustomRoleUnused_ThenRemoveBindings() {
            TestAuth.asSuperAdmin();
            when(roleMapper.selectById(9L)).thenReturn(role(9L, "auditor", null, 0));
            when(roleMapper.countActiveUsersByRoleId(9L)).thenReturn(0L);

            service.deleteRole(9L);

            verify(rolePermissionMapper).deleteByRoleId(9L);
            verify(roleMapper).deleteById(9L);
        }

        @Test
        @DisplayName("应该拒绝 ROLE_ 前缀_当创建自定义角色")
        void shouldRejectRolePrefix_WhenCreateCustomRole_ThenParamError() {
            TestAuth.asSuperAdmin();

            assertThatThrownBy(() -> service.createRole(RoleCreateRequest.builder()
                    .roleCode("ROLE_AUDITOR").roleName("审计").build()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.PARAM_ERROR.getCode());
            verify(roleMapper, never()).insert(any(SysRole.class));
        }
    }

    @Nested
    @DisplayName("校管理员边界")
    class SchoolAdminScope {

        @Test
        @DisplayName("应该强制归属本校_当校管理员创建角色时传了别校ID")
        void shouldForceOwnSchool_WhenSchoolAdminCreates_ThenIgnoreRequestedSchool() {
            asSchoolAdminHolding("sys:role:add");
            when(roleMapper.selectCount(any())).thenReturn(0L);
            when(roleMapper.insert(any(SysRole.class))).thenAnswer(inv -> {
                inv.<SysRole>getArgument(0).setId(20L);
                return 1;
            });

            RoleResponse response = service.createRole(RoleCreateRequest.builder()
                    .roleCode("head_teacher").roleName("班主任").schoolId(99L).build());

            ArgumentCaptor<SysRole> captor = ArgumentCaptor.forClass(SysRole.class);
            verify(roleMapper).insert(captor.capture());
            assertThat(captor.getValue().getSchoolId()).isEqualTo(7L);
            assertThat(captor.getValue().getIsBuiltin()).isZero();
            assertThat(response.getSchoolId()).isEqualTo(7L);
            assertThat(response.getUserCount()).isZero();
        }

        @Test
        @DisplayName("应该拒绝_当校管理员给平台级角色分配权限")
        void shouldRejectAssign_WhenSchoolAdminTargetsPlatformRole_ThenForbidden() {
            asSchoolAdminHolding("score:input");
            when(roleMapper.selectById(3L)).thenReturn(role(3L, "ROLE_TEACHER", null, 1));

            assertThatThrownBy(() -> service.assignPermissions(3L,
                    RolePermissionsRequest.builder().permissionIds(List.of(1L)).build()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
            verify(rolePermissionMapper, never()).deleteByRoleId(anyLong());
        }

        @Test
        @DisplayName("应该拒绝_当校管理员分配自己未持有且角色原本没有的权限")
        void shouldRejectAssign_WhenSchoolAdminLacksPermission_ThenForbidden() {
            asSchoolAdminHolding("score:input");
            when(roleMapper.selectById(9L)).thenReturn(role(9L, "head_teacher", 7L, 0));
            when(permissionMapper.selectBatchIds(any())).thenReturn(List.of(perm(1L, "score:input"), perm(2L, "score:publish")));
            when(permissionMapper.selectByRoleId(9L)).thenReturn(List.of());

            assertThatThrownBy(() -> service.assignPermissions(9L,
                    RolePermissionsRequest.builder().permissionIds(List.of(1L, 2L)).build()))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode())
                    .hasMessageContaining("score:publish");
            verify(rolePermissionMapper, never()).deleteByRoleId(anyLong());
            verify(rolePermissionMapper, never()).insertBatch(anyLong(), any());
        }

        @Test
        @DisplayName("应该全量替换_当校管理员只分配自己持有的权限")
        void shouldAssign_WhenSchoolAdminHoldsAll_ThenReplace() {
            asSchoolAdminHolding("score:input", "score:query");
            when(roleMapper.selectById(9L)).thenReturn(role(9L, "head_teacher", 7L, 0));
            when(permissionMapper.selectBatchIds(any())).thenReturn(List.of(perm(1L, "score:input"), perm(4L, "score:query")));
            when(permissionMapper.selectByRoleId(9L)).thenReturn(List.of(perm(4L, "score:query")));

            service.assignPermissions(9L, RolePermissionsRequest.builder().permissionIds(List.of(1L, 4L)).build());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(rolePermissionMapper).deleteByRoleId(9L);
            verify(rolePermissionMapper).insertBatch(eq(9L), captor.capture());
            assertThat(captor.getValue()).containsExactlyInAnyOrder(1L, 4L);
        }

        @Test
        @DisplayName("应该保留未持有的既有权限_当校管理员提交的集合里没有它（冻结语义）")
        void shouldKeepFrozen_WhenSchoolAdminOmitsUnheldExisting_ThenPreserve() {
            asSchoolAdminHolding("score:input");
            when(roleMapper.selectById(9L)).thenReturn(role(9L, "head_teacher", 7L, 0));
            when(permissionMapper.selectBatchIds(any())).thenReturn(List.of(perm(1L, "score:input")));
            // 超管曾给该角色加过 score:publish，校管理员未持有 → 既不能撤也不算越权
            when(permissionMapper.selectByRoleId(9L)).thenReturn(List.of(perm(2L, "score:publish")));

            service.assignPermissions(9L, RolePermissionsRequest.builder().permissionIds(List.of(1L)).build());

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Collection<Long>> captor = ArgumentCaptor.forClass(Collection.class);
            verify(rolePermissionMapper).insertBatch(eq(9L), captor.capture());
            assertThat(captor.getValue()).containsExactlyInAnyOrder(1L, 2L);
        }

        @Test
        @DisplayName("应该视为不存在_当校管理员访问他校角色")
        void shouldHide_WhenRoleBelongsToOtherSchool_ThenNotFound() {
            asSchoolAdminHolding("sys:role:list");
            when(roleMapper.selectById(11L)).thenReturn(role(11L, "x", 8L, 0));

            assertThatThrownBy(() -> service.getRole(11L))
                    .isInstanceOf(BusinessException.class)
                    .hasFieldOrPropertyWithValue("code", ResultCode.DATA_NOT_FOUND.getCode());
        }

        @Test
        @DisplayName("应该放行任意权限_当超管分配")
        void shouldAssignAnything_WhenSuperAdmin_ThenNoHeldCheck() {
            TestAuth.asSuperAdmin();
            when(roleMapper.selectById(9L)).thenReturn(role(9L, "head_teacher", 7L, 0));
            when(permissionMapper.selectBatchIds(any())).thenReturn(List.of(perm(2L, "score:publish")));

            service.assignPermissions(9L, RolePermissionsRequest.builder().permissionIds(List.of(2L)).build());

            verify(permissionMapper, never()).selectByRoleId(anyLong());
            verify(rolePermissionMapper).deleteByRoleId(9L);
            verify(rolePermissionMapper).insertBatch(eq(9L), any());
        }
    }

}
