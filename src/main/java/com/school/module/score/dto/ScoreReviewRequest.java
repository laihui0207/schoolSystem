package com.school.module.score.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 成绩审核请求
 * <p>
 * 选择待审核成绩的两种方式（二选一，优先 scoreIds）：
 * 1. scoreIds：显式成绩ID列表
 * 2. examId + classId + subjectId：按考试/班级/科目选中该组合下所有待审核成绩
 *
 * @author School System
 */
@Data
public class ScoreReviewRequest {

    /**
     * 成绩ID列表（与三元组二选一）
     */
    private List<Long> scoreIds;

    /**
     * 考试ID（三元组选择器）
     */
    private Long examId;

    /**
     * 班级ID（三元组选择器）
     */
    private Long classId;

    /**
     * 科目ID（三元组选择器）
     */
    private Long subjectId;

    /**
     * 审核结果：1-通过，2-驳回
     */
    @NotNull(message = "审核结果不能为空")
    private Integer result;

    /**
     * 驳回原因（审核不通过时必填）
     */
    private String rejectReason;

    /**
     * 是否给了显式成绩ID列表
     */
    public boolean hasScoreIds() {
        return scoreIds != null && !scoreIds.isEmpty();
    }

    /**
     * 是否给了完整三元组
     */
    public boolean hasTriplet() {
        return examId != null && classId != null && subjectId != null;
    }

    /**
     * 入参校验：scoreIds 与三元组至少给一个
     */
    @AssertTrue(message = "请提供成绩ID列表，或完整的考试ID/班级ID/科目ID")
    public boolean isSelectorPresent() {
        return hasScoreIds() || hasTriplet();
    }

}
