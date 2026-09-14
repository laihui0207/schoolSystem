package com.school.module.scorechange.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩变更日志响应
 *
 * @author School System
 */
@Data
@Builder
public class ScoreChangeLogResponse {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 成绩ID
     */
    private Long scoreId;

    /**
     * 考试ID
     */
    private Long examId;

    /**
     * 考试名称
     */
    private String examName;

    /**
     * 学生ID
     */
    private Long studentId;

    /**
     * 学生姓名
     */
    private String studentName;

    /**
     * 学号
     */
    private String studentNo;

    /**
     * 科目ID
     */
    private Long subjectId;

    /**
     * 科目名称
     */
    private String subjectName;

    /**
     * 原成绩
     */
    private BigDecimal oldScore;

    /**
     * 新成绩
     */
    private BigDecimal newScore;

    /**
     * 修改原因
     */
    private String changeReason;

    /**
     * 申请人ID
     */
    private Long applicantId;

    /**
     * 申请人姓名
     */
    private String applicantName;

    /**
     * 审批人ID
     */
    private Long approverId;

    /**
     * 审批人姓名
     */
    private String approverName;

    /**
     * 状态：0-待审批，1-已通过，2-已驳回
     */
    private Integer status;

    /**
     * 状态名称
     */
    private String statusName;

    /**
     * 申请时间
     */
    private LocalDateTime applyTime;

    /**
     * 审批时间
     */
    private LocalDateTime approveTime;

    /**
     * 驳回原因
     */
    private String rejectReason;

}
