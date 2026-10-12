package com.school.module.rbac.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建角色请求
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleCreateRequest {

    /**
     * 角色编码：字母开头，允许字母/数字/下划线/冒号/中划线。
     * ponytail: ROLE_ 前缀保留给内置角色——role_code 会原样进入 Spring Security authorities，
     * 自定义角色若叫 ROLE_XXX 就能命中 hasRole('XXX') 风格的表达式，这里从源头禁止（见 RoleServiceImpl）。
     */
    @NotBlank(message = "角色编码不能为空")
    @Size(max = 50, message = "角色编码不能超过50个字符")
    @Pattern(regexp = "^[A-Za-z][A-Za-z0-9_:\\-]*$", message = "角色编码只能包含字母、数字、下划线、冒号、中划线，且以字母开头")
    private String roleCode;

    @NotBlank(message = "角色名称不能为空")
    @Size(max = 50, message = "角色名称不能超过50个字符")
    private String roleName;

    @Size(max = 200, message = "角色描述不能超过200个字符")
    private String description;

    /**
     * 所属学校ID；超管可空（平台级角色），校管理员忽略此值、强制为本校
     */
    private Long schoolId;

}
