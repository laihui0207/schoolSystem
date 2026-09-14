package com.school.module.scorechange.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 成绩修改申请请求
 *
 * @author School System
 */
@Data
public class ScoreChangeApplyRequest {

    /**
     * 成绩ID
     */
    @NotNull(message = "成绩ID不能为空")
    private Long scoreId;

    /**
     * 新成绩
     */
    @NotNull(message = "新成绩不能为空")
    private BigDecimal newScore;

    /**
     * 修改原因
     */
    @NotNull(message = "修改原因不能为空")
    private String changeReason;

}
