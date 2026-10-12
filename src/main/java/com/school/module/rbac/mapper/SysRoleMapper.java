package com.school.module.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.module.rbac.dto.RoleUserCount;
import com.school.module.user.entity.SysRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

/**
 * 角色 Mapper
 * <p>
 * 实体 SysRole 留在 user 模块（UserDetailsServiceImpl 依赖），Mapper 放在 rbac 模块：
 * 角色管理是 RBAC 的职责，避免 user 模块继续膨胀。
 *
 * @author School System
 */
@Mapper
public interface SysRoleMapper extends BaseMapper<SysRole> {

    /**
     * 查询用户已绑定的角色（含内置角色与附加角色）
     */
    List<SysRole> selectByUserId(@Param("userId") Long userId);

    /**
     * 统计角色下仍在用（未逻辑删除）的用户数
     */
    long countActiveUsersByRoleId(@Param("roleId") Long roleId);

    /**
     * 批量统计每个角色的在用用户数（列表页展示）
     */
    List<RoleUserCount> countActiveUsersByRoleIds(@Param("roleIds") Collection<Long> roleIds);

}
