package com.school.module.rbac.controller;

import com.school.common.result.Result;
import com.school.module.log.annotation.OperationLog;
import com.school.module.rbac.dto.PermissionCreateRequest;
import com.school.module.rbac.dto.PermissionTreeNode;
import com.school.module.rbac.dto.PermissionUpdateRequest;
import com.school.module.rbac.service.PermissionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 权限点控制器
 * <p>
 * 树是只读共享资源，持有 sys:permission:list 即可看；增删改只开放给超管——
 * 权限码会被 @PreAuthorize 字面量与前端 v-permission 引用，不该由校级账号改动。
 *
 * @author School System
 */
@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping("/tree")
    @PreAuthorize("@perm.has('sys:permission:list')")
    public Result<List<PermissionTreeNode>> getTree() {
        return Result.success(permissionService.getTree());
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(value = "创建权限点", module = "rbac")
    public Result<PermissionTreeNode> createPermission(@Valid @RequestBody PermissionCreateRequest request) {
        return Result.success(permissionService.createPermission(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(value = "更新权限点", module = "rbac")
    public Result<PermissionTreeNode> updatePermission(@PathVariable Long id,
                                                       @Valid @RequestBody PermissionUpdateRequest request) {
        return Result.success(permissionService.updatePermission(id, request));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @OperationLog(value = "删除权限点", module = "rbac")
    public Result<Void> deletePermission(@PathVariable Long id) {
        permissionService.deletePermission(id);
        return Result.success();
    }

}
