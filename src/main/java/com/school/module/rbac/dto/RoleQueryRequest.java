package com.school.module.rbac.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 角色分页查询
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class RoleQueryRequest extends BasePageQuery {

    /** 角色编码（模糊） */
    private String roleCode;

    /** 角色名称（模糊） */
    private String roleName;

    /** 所属学校（仅超管生效；校管理员固定为平台级 + 本校） */
    private Long schoolId;

    /** 是否内置：0/1，null 不过滤 */
    private Integer isBuiltin;

    public RoleQueryRequest() {
        super();
    }

}
