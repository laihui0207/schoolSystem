package com.school.module.score.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩响应
 *
 * @author School System
 */
@Data
@Builder
public class ScoreResponse {

    /**
     * 主键ID
     */
    private Long id;

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
     * 班级ID
     */
    private Long classId;

    /**
     * 班级名称
     */
    private String className;

    /**
     * 学校ID
     */
    private Long schoolId;

    /**
     * 学校名称
     */
    private String schoolName;

    /**
     * 得分
     */
    private BigDecimal score;

    /**
     * 满分
     */
    private BigDecimal fullScore;

    /**
     * 是否缺考：0-否，1-是
     */
    private Integer isAbsent;

    /**
     * 班级排名
     */
    private Integer rankInClass;

    /**
     * 年级排名
     */
    private Integer rankInGrade;

    /**
     * 校级排名
     */
    private Integer rankInSchool;

    // ponytail: 契约 R——暴露总分三档名次供 C/F 组消费
    /**
     * 总分班级排名
     */
    private Integer totalRankInClass;

    /**
     * 总分年级排名
     */
    private Integer totalRankInGrade;

    /**
     * 总分校级排名
     */
    private Integer totalRankInSchool;

    /**
     * 录入人ID
     */
    private Long recorderId;

    /**
     * 状态：0-待审核，1-已通过，2-已驳回
     */
    private Integer status;

    /**
     * 状态名称
     */
    private String statusName;

    /**
     * 录入时间
     */
    private LocalDateTime recordedAt;

    /**
     * 审核时间
     */
    private LocalDateTime reviewedAt;

    // ponytail: 契约 K2——审核留痕三字段；reviewerName 由 Service 批量 selectBatchIds 回填
    /**
     * 审核人ID
     */
    private Long reviewerId;

    /**
     * 审核人姓名（realName 优先，否则 username）
     */
    private String reviewerName;

    /**
     * 驳回原因
     */
    private String rejectReason;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

}
