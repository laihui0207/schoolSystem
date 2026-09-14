package com.school.module.score.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 批量成绩录入请求
 *
 * @author School System
 */
@Data
public class ScoreBatchInputRequest {

    /**
     * 考试ID
     */
    @NotNull(message = "考试ID不能为空")
    private Long examId;

    /**
     * 成绩列表
     */
    @NotEmpty(message = "成绩列表不能为空")
    @Valid
    private List<ScoreItem> scores;

    /**
     * 成绩项
     */
    @Data
    public static class ScoreItem {
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
         * 是否缺考
         */
        private Integer isAbsent;
    }

}
