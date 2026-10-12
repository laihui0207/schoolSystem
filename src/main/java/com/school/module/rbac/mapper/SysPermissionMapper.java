package com.school.module.rbac.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.module.rbac.entity.SysPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 权限点 Mapper
 *
 * @author School System
 */
@Mapper
public interface SysPermissionMapper extends BaseMapper<SysPermission> {

    /**
     * 查询角色已分配的权限点（排除已逻辑删除的权限）
     */
    List<SysPermission> selectByRoleId(@Param("roleId") Long roleId);

}
