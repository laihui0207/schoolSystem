package com.school.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户创建请求
 *
 * @author School System
 */
@Data
public class UserCreateRequest {

    /**
     * 用户名
     */
    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过50个字符")
    private String username;

    /**
     * 密码（明文，入库前 BCrypt 加密）
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 72, message = "密码长度需在6-72位之间")
    private String password;

    /**
     * 真实姓名
     */
    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过50个字符")
    private String realName;

    /**
     * 角色：super_admin / admin / teacher / student / parent
     */
    @NotBlank(message = "角色不能为空")
    private String role;

    /**
     * 所属学校ID
     */
    private Long schoolId;

    /**
     * 手机号（允许为空）
     */
    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    /**
     * 状态：0-禁用，1-启用（默认启用）
     */
    private Integer status;

}
