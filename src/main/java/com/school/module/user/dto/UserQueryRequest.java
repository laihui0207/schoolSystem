package com.school.module.user.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 用户查询请求
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class UserQueryRequest extends BasePageQuery {

    /**
     * 用户名（模糊查询）
     */
    private String username;

    /**
     * 真实姓名（模糊查询）
     */
    private String realName;

    /**
     * 角色：super_admin / admin / teacher / student / parent
     */
    private String role;

    /**
     * 所属学校ID
     */
    private Long schoolId;

    /**
     * 状态：0-禁用，1-启用
     */
    private Integer status;

    public UserQueryRequest() {
        super();
    }

}
