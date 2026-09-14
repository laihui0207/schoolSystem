package com.school.security.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 密码工具类
 *
 * @author School System
 */
public class PasswordUtil {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    /**
     * 加密密码
     */
    public static String encode(String rawPassword) {
        return PASSWORD_ENCODER.encode(rawPassword);
    }

    /**
     * 验证密码
     */
    public static boolean matches(String rawPassword, String encodedPassword) {
        return PASSWORD_ENCODER.matches(rawPassword, encodedPassword);
    }

    /**
     * 生成BCrypt加密的密码（用于测试）
     */
    public static void main(String[] args) {
        // 生成admin123的加密密码
        String password = "admin123";
        String encoded = encode(password);
        System.out.println("原始密码: " + password);
        System.out.println("加密密码: " + encoded);
        System.out.println("验证结果: " + matches(password, encoded));
    }

}
