package com.school.module.scorechange.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.school.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩变更日志实体
 *
 * @author School System
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("score_change_log")
public class ScoreChangeLog extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
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
     * 学生ID
     */
    private Long studentId;

    /**
     * 科目ID
     */
    private Long subjectId;

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
     * 审批人ID
     */
    private Long approverId;

    /**
     * 状态：0-待审批，1-已通过，2-已驳回
     */
    private Integer status;

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
