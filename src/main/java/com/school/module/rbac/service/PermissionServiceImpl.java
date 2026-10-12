package com.school.module.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.rbac.dto.PermissionCreateRequest;
import com.school.module.rbac.dto.PermissionTreeNode;
import com.school.module.rbac.dto.PermissionUpdateRequest;
import com.school.module.rbac.entity.SysPermission;
import com.school.module.rbac.mapper.SysPermissionMapper;
import com.school.module.rbac.mapper.SysRolePermissionMapper;
import com.school.security.model.LoginUser;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 权限点服务实现
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private static final String ROLE_PREFIX = "ROLE_";

    private final SysPermissionMapper permissionMapper;
    private final SysRolePermissionMapper rolePermissionMapper;

    @Override
    public List<PermissionTreeNode> getTree() {
        List<SysPermission> all = permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                .orderByAsc(SysPermission::getSortOrder)
                .orderByAsc(SysPermission::getId));
        Map<Long, PermissionTreeNode> byId = new HashMap<>();
        all.forEach(p -> byId.put(p.getId(), RoleConverter.toNode(p)));

        List<PermissionTreeNode> roots = new ArrayList<>();
        for (SysPermission p : all) {
            PermissionTreeNode node = byId.get(p.getId());
            PermissionTreeNode parent = p.getParentId() == null ? null : byId.get(p.getParentId());
            // ponytail: 父节点被逻辑删除后子节点不该从树里消失，提升为一级，管理员才看得到并能重新归位
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }
        return roots;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PermissionTreeNode createPermission(PermissionCreateRequest request) {
        String code = request.getPermissionCode().trim();
        if (permissionMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermissionCode, code)) > 0) {
            throw new BusinessException(ResultCode.DATA_ALREADY_EXISTS, "权限编码已存在：" + code);
        }
        if (request.getParentId() != null) {
            requireExisting(request.getParentId(), "父权限不存在");
        }

        SysPermission permission = new SysPermission();
        permission.setPermissionCode(code);
        permission.setPermissionName(request.getPermissionName().trim());
        permission.setResourceType(request.getResourceType());
        permission.setUrl(request.getUrl());
        permission.setParentId(request.getParentId());
        permission.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        permissionMapper.insert(permission);
        return RoleConverter.toNode(permission);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PermissionTreeNode updatePermission(Long id, PermissionUpdateRequest request) {
        SysPermission existing = requireExisting(id, "权限不存在");

        // parentId 为 null 不改；0 表示移到一级；其余必须存在且不能形成环
        boolean moveToRoot = request.getParentId() != null && request.getParentId() == 0L;
        Long newParentId = moveToRoot ? null : request.getParentId();
        if (newParentId != null) {
            if (newParentId.equals(id)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "父权限不能是自身");
            }
            requireExisting(newParentId, "父权限不存在");
            assertNotDescendant(id, newParentId);
        }

        SysPermission updated = new SysPermission();
        updated.setId(id);
        updated.setPermissionName(request.getPermissionName());
        updated.setResourceType(request.getResourceType());
        updated.setUrl(request.getUrl());
        updated.setSortOrder(request.getSortOrder());
        updated.setParentId(newParentId);
        // MyBatis-Plus 跳过 null 字段，移到一级需显式 SET NULL
        permissionMapper.update(updated, new LambdaUpdateWrapper<SysPermission>()
                .eq(SysPermission::getId, id)
                .setSql(moveToRoot && existing.getParentId() != null, "parent_id = NULL"));
        return RoleConverter.toNode(permissionMapper.selectById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePermission(Long id) {
        requireExisting(id, "权限不存在");
        Long children = permissionMapper.selectCount(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getParentId, id));
        if (children > 0) {
            throw new BusinessException(ResultCode.DATA_IN_USE, "该权限下还有 " + children + " 个子权限，请先删除子权限");
        }
        // sys_role_permission 没有 deleted 列，绑定物理清除，否则角色权限列表 JOIN 仍能查到幽灵权限
        rolePermissionMapper.deleteByPermissionId(id);
        permissionMapper.deleteById(id);
    }

    @Override
    public List<String> currentUserPermissionCodes() {
        LoginUser user = SecurityUtils.currentUser();
        if (user.isSuperAdmin()) {
            // ponytail: 超管在 @perm 里恒为 true，前端 v-permission 也要同样放行，直接返回全部权限码
            return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>()
                            .select(SysPermission::getPermissionCode)).stream()
                    .map(SysPermission::getPermissionCode)
                    .sorted()
                    .collect(Collectors.toList());
        }
        return user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> !a.startsWith(ROLE_PREFIX))
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private SysPermission requireExisting(Long id, String message) {
        SysPermission permission = permissionMapper.selectById(id);
        if (permission == null) {
            throw new BusinessException(ResultCode.DATA_NOT_FOUND, message);
        }
        return permission;
    }

    /**
     * 新父节点不能是 id 的后代，否则树成环、getTree 丢节点
     */
    private void assertNotDescendant(Long id, Long newParentId) {
        Set<Long> visited = new HashSet<>();
        Long cursor = newParentId;
        while (cursor != null && visited.add(cursor)) {
            if (cursor.equals(id)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "不能把权限移动到自己的子节点下");
            }
            SysPermission node = permissionMapper.selectById(cursor);
            cursor = node == null ? null : node.getParentId();
        }
    }

}
