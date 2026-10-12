package com.school.module.rbac.service;

import com.school.module.rbac.dto.PermissionTreeNode;
import com.school.module.rbac.dto.RoleResponse;
import com.school.module.rbac.entity.SysPermission;
import com.school.module.user.entity.SysRole;

/**
 * 实体 → 响应 DTO 的手写转换（MapStruct 在本项目被禁用）
 * <p>
 * RoleServiceImpl 与 UserRoleServiceImpl 都要转换 SysRole，集中在这里避免两份拷贝漂移。
 *
 * @author School System
 */
final class RoleConverter {

    private RoleConverter() {
    }

    static RoleResponse toResponse(SysRole role, String schoolName, Long userCount) {
        return RoleResponse.builder()
                .id(role.getId())
                .roleCode(role.getRoleCode())
                .roleName(role.getRoleName())
                .description(role.getDescription())
                .schoolId(role.getSchoolId())
                .schoolName(schoolName)
                .isBuiltin(role.getIsBuiltin() == null ? 0 : role.getIsBuiltin())
                .userCount(userCount)
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .build();
    }

    static boolean isBuiltin(SysRole role) {
        return role.getIsBuiltin() != null && role.getIsBuiltin() == 1;
    }

    static PermissionTreeNode toNode(SysPermission p) {
        return PermissionTreeNode.builder()
                .id(p.getId())
                .permissionCode(p.getPermissionCode())
                .permissionName(p.getPermissionName())
                .resourceType(p.getResourceType())
                .url(p.getUrl())
                .parentId(p.getParentId())
                .sortOrder(p.getSortOrder())
                .build();
    }

}
