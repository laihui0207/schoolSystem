package com.school.module.score.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 成绩录入请求
 *
 * @author School System
 */
@Data
public class ScoreInputRequest {

    /**
     * 考试ID
     */
    @NotNull(message = "考试ID不能为空")
    private Long examId;

    /**
     * 学生ID
     */
    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    /**
     * 科目ID
     */
    @NotNull(message = "科目ID不能为空")
    private Long subjectId;

    /**
     * 班级ID
     */
    @NotNull(message = "班级ID不能为空")
    private Long classId;

    /**
     * 学校ID
     */
    @NotNull(message = "学校ID不能为空")
    private Long schoolId;

    /**
     * 得分
     */
    private BigDecimal score;

    /**
     * 是否缺考：0-否，1-是
     */
    private Integer isAbsent;

    /**
     * 备注
     */
    // ponytail: B-7——score.remark 是 VARCHAR(500)，超长会让 PG 事务进入 aborted 状态，入口先拦
    @Size(max = 500, message = "备注不能超过500字")
    private String remark;

}
