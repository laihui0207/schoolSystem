package com.school.module.rbac.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 用户附加角色全量设置请求（PUT /users/{id}/roles）
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRolesUpdateRequest {

    /**
     * 目标角色编码集合（全量）。与 user_type 对应的内置角色始终保留，可不传；
     * 传入其他内置角色会被拒绝（内置角色由用户类型决定）。
     */
    @NotNull(message = "roleCodes 不能为空（可传空数组）")
    private List<String> roleCodes;

}
