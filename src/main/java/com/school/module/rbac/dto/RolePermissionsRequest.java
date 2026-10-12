package com.school.module.rbac.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 角色权限全量替换请求
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionsRequest {

    /**
     * 目标权限ID集合（全量）；空列表表示清空
     */
    @NotNull(message = "permissionIds 不能为空（可传空数组）")
    private List<Long> permissionIds;

}
