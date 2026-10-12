package com.school.module.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 学生 / 家长自助注册请求
 * <p>
 * username 固定取手机号；role 只允许 student / parent，其余角色一律由管理员创建。
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 短信验证码（scene=register）
     */
    @NotBlank(message = "短信验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "短信验证码为 6 位数字")
    private String code;

    /**
     * 密码：至少 8 位，且同时包含字母和数字
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 72, message = "密码长度需在 8-72 位之间")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)\\S+$", message = "密码需同时包含字母和数字，且不含空格")
    private String password;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过 50 个字符")
    private String realName;

    /**
     * 角色：student | parent
     */
    @NotBlank(message = "角色不能为空")
    @Pattern(regexp = "^(student|parent)$", message = "仅支持学生或家长自助注册")
    private String role;

    @NotNull(message = "请选择学校")
    private Long schoolId;

    /**
     * 学号（可选，role=student 时用于自动绑定学生档案）
     */
    @Size(max = 30, message = "学号长度不能超过 30 个字符")
    private String studentNo;

}
