package com.school.module.score.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * 批量成绩录入请求（契约 1：POST /scores/batch-input）
 * <p>
 * ponytail: 删除了 schoolId 字段——学校归属由服务端按学生档案（student.school_id）解析，
 * 客户端不得逐条指定；顶层 classId/subjectId 作为各项的默认值，单项可覆盖。
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreBatchInputRequest {

    /**
     * 考试ID
     */
    @NotNull(message = "考试ID不能为空")
    private Long examId;

    /**
     * 班级ID（可选，作为各项默认值）
     */
    private Long classId;

    /**
     * 科目ID（可选，作为各项默认值）
     */
    private Long subjectId;

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
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScoreItem {
        /**
         * 学生ID
         */
        @NotNull(message = "学生ID不能为空")
        private Long studentId;

        /**
         * 科目ID（可选，为空时取顶层默认）
         */
        private Long subjectId;

        /**
         * 班级ID（可选，为空时取顶层默认）
         */
        private Long classId;

        /**
         * 得分（缺考时可空）
         */
        private BigDecimal score;

        /**
         * 是否缺考：0-否，1-是，默认 0
         */
        @Builder.Default
        private Integer isAbsent = 0;

        /**
         * 备注
         */
        // ponytail: B-7——与 ScoreInputRequest 一致，批量项的备注同样限 500 字
        @Size(max = 500, message = "备注不能超过500字")
        private String remark;
    }

}
