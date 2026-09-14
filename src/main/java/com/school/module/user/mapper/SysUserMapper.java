package com.school.module.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.school.module.user.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 用户Mapper
 *
 * @author School System
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 根据用户ID查询角色列表
     */
    List<String> selectRoleCodesByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID查询权限列表
     */
    List<String> selectPermissionCodesByUserId(@Param("userId") Long userId);

}
