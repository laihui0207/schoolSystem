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

    /**
     * 按角色编码为用户绑定角色（写入 sys_user_role）
     *
     * @param userId 用户ID
     * @param roleCode 角色编码，如 ROLE_TEACHER
     * @return 影响行数，角色编码不存在时为 0
     */
    int insertUserRole(@Param("userId") Long userId, @Param("roleCode") String roleCode);

    /**
     * 解绑用户的全部角色
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    int deleteUserRolesByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID查询关联的教师ID
     * <p>
     * 放在本 Mapper 而非 TeacherMapper：登录时构建 LoginUser 需要它，
     * 走 TeacherMapper 会让 security 包反向依赖 module.teacher
     *
     * @param userId 用户ID
     * @return 教师ID，非教师账号返回 null
     */
    Long selectTeacherIdByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID查询关联的学生ID
     * <p>
     * 同上，避免 security 包反向依赖 module.student
     *
     * @param userId 用户ID
     * @return 学生ID，非学生账号返回 null
     */
    Long selectStudentIdByUserId(@Param("userId") Long userId);

}
