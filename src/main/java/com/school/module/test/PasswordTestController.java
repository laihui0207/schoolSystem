package com.school.module.test;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 密码测试控制器（仅用于调试，生产环境请删除）
 */
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class PasswordTestController {

    private final PasswordEncoder passwordEncoder;

    @GetMapping("/hash")
    public Map<String, Object> generateHash(@RequestParam String password) {
        Map<String, Object> result = new HashMap<>();
        String hash = passwordEncoder.encode(password);
        result.put("rawPassword", password);
        result.put("hash", hash);
        result.put("verify", passwordEncoder.matches(password, hash));
        return result;
    }

    @GetMapping("/verify")
    public Map<String, Object> verifyPassword(
            @RequestParam String password,
            @RequestParam String hash) {
        Map<String, Object> result = new HashMap<>();
        result.put("rawPassword", password);
        result.put("hash", hash);
        result.put("matches", passwordEncoder.matches(password, hash));
        return result;
    }
}
