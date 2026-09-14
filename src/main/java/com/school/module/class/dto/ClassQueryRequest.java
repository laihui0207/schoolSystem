package com.school.module.class_module.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 班级查询请求
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ClassQueryRequest extends BasePageQuery {

    /**
     * 学校ID
     */
    private Long schoolId;

    /**
     * 年级ID
     */
    private Long gradeId;

    /**
     * 学期ID
     */
    private Long termId;

    /**
     * 班级名称（模糊查询）
     */
    private String className;

    /**
     * 班主任ID
     */
    private Long headTeacherId;

    public ClassQueryRequest() {
        super();
    }

}
