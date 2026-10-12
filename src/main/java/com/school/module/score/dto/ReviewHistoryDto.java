package com.school.module.score.dto;

import lombok.Data;

@Data
public class ReviewHistoryDto {
    private Long id;
    /** 展示用操作人：有 reviewerId 取审核人姓名；历史数据无审核人时退回录入人并标注 */
    private String operator;
    private String operateTime;
    /** approve | reject */
    private String action;
    /** 驳回原因（与 rejectReason 同值，保留给旧前端字段） */
    private String reason;
    private String remark;

    // ponytail: 契约 K2——显式暴露审核人与驳回原因，operator/reason 只是兼容字段
    /** 审核人ID（sys_user.id），历史数据可能为 null */
    private Long reviewerId;
    /** 审核人姓名，reviewerId 为 null 时亦为 null */
    private String reviewerName;
    /** 驳回原因，status=2 时有值 */
    private String rejectReason;
}
