package com.school;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordTest {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        String rawPassword = "admin123";
        String dbHash = "$2a$10$dXJ3SW6G7P7mh/ylBfDzXOykoIX5yH9tEmrJ3NVskrzQBPXccbyDC";

        boolean matches = encoder.matches(rawPassword, dbHash);

        System.out.println("原始密码: " + rawPassword);
        System.out.println("数据库哈希: " + dbHash);
        System.out.println("验证结果: " + matches);

        // 生成一个新的哈希用于对比
        String newHash = encoder.encode(rawPassword);
        System.out.println("新生成的哈希: " + newHash);
        System.out.println("新哈希验证: " + encoder.matches(rawPassword, newHash));
    }
}
