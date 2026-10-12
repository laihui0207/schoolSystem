package com.school.module.rbac.controller;

import com.school.common.result.Result;
import com.school.module.log.annotation.OperationLog;
import com.school.module.rbac.dto.RoleResponse;
import com.school.module.rbac.dto.UserRolesUpdateRequest;
import com.school.module.rbac.service.UserRoleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户附加角色控制器（/users/{id}/roles）
 * <p>
 * 路径与 user 模块 UserController 的 /users/{id}、/users/{id}/status、/users/{id}/password 不冲突。
 *
 * @author School System
 */
@RestController
@RequestMapping("/users/{id}/roles")
@RequiredArgsConstructor
public class UserRoleController {

    private final UserRoleService userRoleService;

    @GetMapping
    @PreAuthorize("@perm.has('sys:user:list')")
    public Result<List<RoleResponse>> getUserRoles(@PathVariable Long id) {
        return Result.success(userRoleService.getUserRoles(id));
    }

    @PutMapping
    @PreAuthorize("@perm.has('sys:user:assign-role')")
    @OperationLog(value = "设置用户附加角色", module = "rbac")
    public Result<List<RoleResponse>> updateUserRoles(@PathVariable Long id,
                                                      @Valid @RequestBody UserRolesUpdateRequest request) {
        return Result.success(userRoleService.updateUserRoles(id, request));
    }

}
