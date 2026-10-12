package com.school.module.school.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 学校查询请求DTO
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class SchoolQueryRequest extends BasePageQuery {

    private static final long serialVersionUID = 1L;

    /**
     * 学校ID（精确匹配）
     */
    // ponytail: G-2 — 非超管查询时由服务层注入本校ID，SchoolMapper.xml 据此收窄
    private Long id;

    /**
     * 学校名称（模糊查询）
     */
    private String schoolName;

    /**
     * 学校编码
     */
    private String schoolCode;

    /**
     * 学校类型
     */
    private String schoolType;

    /**
     * 省份
     */
    private String province;

    /**
     * 城市
     */
    private String city;

    /**
     * 区县
     */
    private String district;

    /**
     * 状态
     */
    private Integer status;

    public SchoolQueryRequest() {
        super();
    }

}
