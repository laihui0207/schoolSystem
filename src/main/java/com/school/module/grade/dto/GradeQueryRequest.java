package com.school.module.grade.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 年级查询请求
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class GradeQueryRequest extends BasePageQuery {

    private Long schoolId;
    private String gradeName;
    private Integer gradeCode;

    public GradeQueryRequest() {
        super();
    }

}
