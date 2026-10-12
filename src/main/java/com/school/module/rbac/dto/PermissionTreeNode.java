package com.school.module.rbac.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 权限树节点（GET /permissions/tree）
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionTreeNode {

    private Long id;

    private String permissionCode;

    private String permissionName;

    /** 1-菜单 2-按钮 3-接口 */
    private Integer resourceType;

    private String url;

    private Long parentId;

    private Integer sortOrder;

    @Builder.Default
    private List<PermissionTreeNode> children = new ArrayList<>();

}
