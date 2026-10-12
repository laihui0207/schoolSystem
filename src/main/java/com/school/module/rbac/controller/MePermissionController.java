package com.school.module.rbac.controller;

import com.school.common.result.Result;
import com.school.module.rbac.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 当前用户权限码（GET /me/permissions）
 * <p>
 * ponytail: 放在 rbac 模块而非 AuthController，避免并行开发时改动 auth 模块；
 * 任何已登录身份都可调用（SecurityConfig anyRequest().authenticated() 兜底），供前端 v-permission 指令拉取。
 *
 * @author School System
 */
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MePermissionController {

    private final PermissionService permissionService;

    @GetMapping("/permissions")
    public Result<List<String>> myPermissions() {
        return Result.success(permissionService.currentUserPermissionCodes());
    }

}
