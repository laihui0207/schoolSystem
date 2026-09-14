package com.school.module.student.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 学生查询请求
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class StudentQueryRequest extends BasePageQuery {

    /**
     * 学校ID
     */
    private Long schoolId;

    /**
     * 班级ID
     */
    private Long classId;

    /**
     * 学号（模糊查询）
     */
    private String studentNo;

    /**
     * 姓名（模糊查询）
     */
    private String name;

    /**
     * 性别
     */
    private Integer gender;

    /**
     * 状态：0-离校，1-在读，2-休学
     */
    private Integer status;

    public StudentQueryRequest() {
        super();
    }

}
