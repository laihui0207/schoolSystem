package com.school.module.school.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 学校查询请求
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SchoolQueryRequest extends BasePageQuery {

    /** 学校名称（模糊查询） */
    private String schoolName;

    /** 学校编码 */
    private String schoolCode;

    /** 状态：0-禁用，1-启用 */
    private Integer status;

    public SchoolQueryRequest() {
        super();
    }

}
