package com.school.module.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 角色在用用户数（SysRoleMapper.countActiveUsersByRoleIds 的结果行）
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleUserCount {

    private Long roleId;

    private Long userCount;

}
