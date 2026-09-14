package com.school.module.score.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 成绩审核请求
 *
 * @author School System
 */
@Data
public class ScoreReviewRequest {

    /**
     * 成绩ID列表
     */
    @NotEmpty(message = "成绩ID列表不能为空")
    private List<Long> scoreIds;

    /**
     * 审核结果：1-通过，2-驳回
     */
    @NotNull(message = "审核结果不能为空")
    private Integer result;

    /**
     * 驳回原因（审核不通过时必填）
     */
    private String rejectReason;

}
