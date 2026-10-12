package com.school.module.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.module.rbac.entity.SysRolePermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;

/**
 * 角色权限关联 Mapper
 *
 * @author School System
 */
@Mapper
public interface SysRolePermissionMapper extends BaseMapper<SysRolePermission> {

    /**
     * 清空角色的全部权限（全量替换前调用）
     */
    int deleteByRoleId(@Param("roleId") Long roleId);

    /**
     * 删除某权限点在所有角色上的绑定（删除权限点时调用）
     */
    int deleteByPermissionId(@Param("permissionId") Long permissionId);

    /**
     * 批量写入角色权限
     *
     * @return 影响行数，permissionIds 为空时调用方应跳过
     */
    int insertBatch(@Param("roleId") Long roleId, @Param("permissionIds") Collection<Long> permissionIds);

}
