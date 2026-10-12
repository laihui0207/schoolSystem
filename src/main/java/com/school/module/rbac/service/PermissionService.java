package com.school.module.rbac.service;

import com.school.module.rbac.dto.PermissionCreateRequest;
import com.school.module.rbac.dto.PermissionTreeNode;
import com.school.module.rbac.dto.PermissionUpdateRequest;

import java.util.List;

/**
 * 权限点服务
 *
 * @author School System
 */
public interface PermissionService {

    /** 完整权限树（按 sort_order 排序，父节点被删的孤儿提升为一级） */
    List<PermissionTreeNode> getTree();

    PermissionTreeNode createPermission(PermissionCreateRequest request);

    PermissionTreeNode updatePermission(Long id, PermissionUpdateRequest request);

    /** 有子节点拒绝；同时清理 sys_role_permission 绑定 */
    void deletePermission(Long id);

    /** 当前用户的权限码列表；超管返回全部权限码 */
    List<String> currentUserPermissionCodes();

}
