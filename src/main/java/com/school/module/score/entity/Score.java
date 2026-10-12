package com.school.module.score.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.school.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成绩实体
 *
 * @author School System
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("score")
public class Score extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

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
     * 班级ID
     */
    private Long classId;

    /**
     * 学校ID
     */
    private Long schoolId;

    /**
     * 得分
     */
    private BigDecimal score;

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

    // ponytail: 契约 R——总分名次独立三列，rank_in_* 只表示该行所属科目的单科名次，不再被总分轮覆盖
    /**
     * 总分班级排名（同一学生同场考试所有行相同）
     */
    private Integer totalRankInClass;

    /**
     * 总分年级排名（同一学生同场考试所有行相同）
     */
    private Integer totalRankInGrade;

    /**
     * 总分校级排名（同一学生同场考试所有行相同）
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
     * 录入时间
     */
    private LocalDateTime recordedAt;

    /**
     * 审核时间
     */
    private LocalDateTime reviewedAt;

    /**
     * 备注
     */
    private String remark;

}
