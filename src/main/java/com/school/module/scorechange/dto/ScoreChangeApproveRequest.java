package com.school.module.scorechange.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 成绩变更审批请求
 *
 * @author School System
 */
@Data
public class ScoreChangeApproveRequest {

    /**
     * 变更申请ID列表
     */
    @NotEmpty(message = "申请ID列表不能为空")
    private List<Long> changeLogIds;

    /**
     * 审批结果：1-通过，2-驳回
     */
    @NotNull(message = "审批结果不能为空")
    private Integer result;

    /**
     * 驳回原因（驳回时必填）
     */
    private String rejectReason;

}
