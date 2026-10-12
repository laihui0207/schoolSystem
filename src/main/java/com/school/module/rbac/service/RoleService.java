package com.school.module.rbac.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.rbac.dto.PermissionTreeNode;
import com.school.module.rbac.dto.RoleCreateRequest;
import com.school.module.rbac.dto.RolePermissionsRequest;
import com.school.module.rbac.dto.RoleQueryRequest;
import com.school.module.rbac.dto.RoleResponse;
import com.school.module.rbac.dto.RoleUpdateRequest;

import java.util.List;

/**
 * 角色管理服务
 *
 * @author School System
 */
public interface RoleService {

    /** 分页：超管全部；校管理员看平台级（school_id IS NULL）+ 本校 */
    Page<RoleResponse> listRoles(RoleQueryRequest request);

    RoleResponse getRole(Long id);

    /** 校管理员创建的角色自动归属本校 */
    RoleResponse createRole(RoleCreateRequest request);

    /** 内置角色 role_code 不可改；校管理员只能改本校自建角色 */
    RoleResponse updateRole(Long id, RoleUpdateRequest request);

    /** 内置拒绝；仍有在用用户拒绝 */
    void deleteRole(Long id);

    /** 角色已分配的权限点（平铺，children 为空） */
    List<PermissionTreeNode> getRolePermissions(Long roleId);

    /** 全量替换；校管理员只能增删自己持有的权限点，未持有的保持原样 */
    void assignPermissions(Long roleId, RolePermissionsRequest request);

}
