package com.school.module.teacher.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 教师查询请求
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class TeacherQueryRequest extends BasePageQuery {

    /**
     * 学校ID
     */
    private Long schoolId;

    /**
     * 工号（模糊查询）
     */
    private String teacherNo;

    /**
     * 姓名（模糊查询）
     */
    private String name;

    /**
     * 性别
     */
    private Integer gender;

    /**
     * 主教科目ID
     */
    private Long subjectId;

    /**
     * 状态：0-离职，1-在职
     */
    private Integer status;

    public TeacherQueryRequest() {
        super();
    }

}
