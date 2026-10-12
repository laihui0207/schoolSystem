package com.school.module.sms.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

/**
 * 短信验证码使用场景
 * <p>
 * 验证码按场景隔离存储（sms:code:&lt;scene&gt;:&lt;phone&gt;），注册场景拿到的码不能拿去登录。
 *
 * @author School System
 */
@Getter
@AllArgsConstructor
public enum SmsScene {

    LOGIN("login", "登录"),
    REGISTER("register", "注册"),
    RESET("reset", "重置密码");

    /**
     * 接口参数 scene 的取值
     */
    private final String code;

    private final String label;

    /**
     * 按接口参数解析，未匹配返回 null
     */
    public static SmsScene fromCode(String code) {
        if (code == null) {
            return null;
        }
        String normalized = code.trim().toLowerCase();
        return Arrays.stream(values())
                .filter(scene -> scene.code.equals(normalized))
                .findFirst()
                .orElse(null);
    }

}
