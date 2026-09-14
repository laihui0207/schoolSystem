package com.school.module.score.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 成绩查询请求
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ScoreQueryRequest extends BasePageQuery {

    /**
     * 考试ID
     */
    private Long examId;

    /**
     * 学生ID
     */
    private Long studentId;

    /**
     * 科目ID
     */
    private Long subjectId;

    /**
     * 班级ID
     */
    private Long classId;

    /**
     * 学校ID
     */
    private Long schoolId;

    /**
     * 状态：0-待审核，1-已通过，2-已驳回
     */
    private Integer status;

    /**
     * 学生姓名（模糊查询）
     */
    private String studentName;

    /**
     * 学号（模糊查询）
     */
    private String studentNo;

    public ScoreQueryRequest() {
        super();
    }

}
