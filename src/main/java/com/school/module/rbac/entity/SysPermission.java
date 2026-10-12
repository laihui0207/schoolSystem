package com.school.module.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.school.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 权限点实体（sys_permission）
 * <p>
 * permission_code 同时是 Spring Security 的 authority（见 UserDetailsServiceImpl），
 * 因此 {@code @PreAuthorize("@perm.has('score:review')")} 直接按 code 判定。
 *
 * @author School System
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_permission")
public class SysPermission extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 资源类型：菜单 */
    public static final int TYPE_MENU = 1;
    /** 资源类型：按钮 */
    public static final int TYPE_BUTTON = 2;
    /** 资源类型：接口 */
    public static final int TYPE_API = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 权限编码，如 score:review / sys:user:add
     */
    private String permissionCode;

    /**
     * 权限名称
     */
    private String permissionName;

    /**
     * 资源类型: 1-菜单 2-按钮 3-接口
     */
    private Integer resourceType;

    /**
     * 资源URL（后端实际路径，仅作展示）
     */
    private String url;

    /**
     * 父权限ID，NULL 为一级菜单
     */
    private Long parentId;

    /**
     * 同级排序
     */
    private Integer sortOrder;

}
