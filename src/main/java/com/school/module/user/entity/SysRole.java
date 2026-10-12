package com.school.module.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.school.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 角色实体
 *
 * @author School System
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_role")
public class SysRole extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 角色编码
     */
    private String roleCode;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 所属学校ID，NULL表示平台级角色
     */
    private Long schoolId;

    /**
     * 是否内置角色: 0-否 1-是（V19）
     * <p>
     * ponytail: 用 Integer 而非 boolean——Lombok 对 boolean isXxx 生成的 getter 是 isBuiltin()，
     * MyBatis-Plus 反射列名时会丢掉 is 前缀映射成 builtin 列，与 DB 的 is_builtin 对不上。
     * 内置角色（5 个预置角色）不可删除、role_code 不可修改、权限只能由超管调整。
     */
    private Integer isBuiltin;

}
