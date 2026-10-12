package com.school.module.rbac.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.log.annotation.OperationLog;
import com.school.module.rbac.dto.PermissionTreeNode;
import com.school.module.rbac.dto.RoleCreateRequest;
import com.school.module.rbac.dto.RolePermissionsRequest;
import com.school.module.rbac.dto.RoleQueryRequest;
import com.school.module.rbac.dto.RoleResponse;
import com.school.module.rbac.dto.RoleUpdateRequest;
import com.school.module.rbac.service.RoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 角色管理控制器
 * <p>
 * ponytail: 契约 P——本模块全部用 {@code @perm.has('sys:role:*')} 鉴权而非 hasRole：
 * 超管在 PermissionEvaluatorBean 里恒通过，校管理员靠 V19 预置的 sys:role:* 权限点通过，
 * 其他身份只要被授予相应权限点同样可用，这是「UserRole 以外的角色也可配置」的落点。
 *
 * @author School System
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    @PreAuthorize("@perm.has('sys:role:list')")
    public Result<Page<RoleResponse>> listRoles(RoleQueryRequest request) {
        return Result.success(roleService.listRoles(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@perm.has('sys:role:list')")
    public Result<RoleResponse> getRole(@PathVariable Long id) {
        return Result.success(roleService.getRole(id));
    }

    @PostMapping
    @PreAuthorize("@perm.has('sys:role:add')")
    @OperationLog(value = "创建角色", module = "rbac")
    public Result<RoleResponse> createRole(@Valid @RequestBody RoleCreateRequest request) {
        return Result.success(roleService.createRole(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@perm.has('sys:role:edit')")
    @OperationLog(value = "更新角色", module = "rbac")
    public Result<RoleResponse> updateRole(@PathVariable Long id, @Valid @RequestBody RoleUpdateRequest request) {
        return Result.success(roleService.updateRole(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@perm.has('sys:role:delete')")
    @OperationLog(value = "删除角色", module = "rbac")
    public Result<Void> deleteRole(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.success();
    }

    @GetMapping("/{id}/permissions")
    @PreAuthorize("@perm.has('sys:role:list')")
    public Result<List<PermissionTreeNode>> getRolePermissions(@PathVariable Long id) {
        return Result.success(roleService.getRolePermissions(id));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("@perm.has('sys:role:edit')")
    @OperationLog(value = "分配角色权限", module = "rbac")
    public Result<Void> assignPermissions(@PathVariable Long id, @Valid @RequestBody RolePermissionsRequest request) {
        roleService.assignPermissions(id, request);
        return Result.success();
    }

}
