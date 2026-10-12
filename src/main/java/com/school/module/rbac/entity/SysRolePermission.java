package com.school.module.rbac.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 角色权限关联（sys_role_permission）
 * <p>
 * ponytail: 该表只有 id/role_id/permission_id/created_at，没有 updated_at 与 deleted 列，
 * 不能继承 BaseEntity，否则 MyBatis-Plus 自动填充会生成不存在的列而报错。关联行一律物理删除。
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("sys_role_permission")
public class SysRolePermission implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long roleId;

    private Long permissionId;

    /**
     * 创建时间，由数据库 DEFAULT CURRENT_TIMESTAMP 填充，插入时留空
     */
    private LocalDateTime createdAt;

}
