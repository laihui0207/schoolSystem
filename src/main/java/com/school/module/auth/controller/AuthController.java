package com.school.module.auth.controller;

import com.school.common.result.Result;
import com.school.module.auth.dto.CaptchaResponse;
import com.school.module.auth.dto.LoginRequest;
import com.school.module.auth.dto.LoginResponse;
import com.school.module.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 认证控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return Result.success(response);
    }

    @PostMapping("/logout")
    public Result<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authorization != null && authorization.startsWith("Bearer ")) {
            String token = authorization.substring(7);
            authService.logout(token);
        }
        return Result.success();
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refreshToken(@RequestParam String refreshToken) {
        LoginResponse response = authService.refreshToken(refreshToken);
        return Result.success(response);
    }

    @GetMapping("/captcha")
    public Result<CaptchaResponse> getCaptcha() {
        CaptchaResponse response = authService.generateCaptcha();
        return Result.success(response);
    }

}
