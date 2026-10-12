package com.school.module.rbac.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 角色响应
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoleResponse {

    private Long id;

    private String roleCode;

    private String roleName;

    private String description;

    /** 所属学校ID，null 为平台级 */
    private Long schoolId;

    private String schoolName;

    /** 是否内置：0/1 */
    private Integer isBuiltin;

    /** 在用用户数（列表与详情返回；为 0 才可删除） */
    private Long userCount;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

}
