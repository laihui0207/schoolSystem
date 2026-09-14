package com.school.module.stat.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 个人成绩统计响应
 *
 * @author School System
 */
@Data
@Builder
public class PersonalStatResponse {

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
     * 考试ID
     */
    private Long examId;

    /**
     * 考试名称
     */
    private String examName;

    /**
     * 总分
     */
    private BigDecimal totalScore;

    /**
     * 满分
     */
    private BigDecimal totalFullScore;

    /**
     * 平均分
     */
    private BigDecimal averageScore;

    /**
     * 班级排名
     */
    private Integer rankInClass;

    /**
     * 年级排名
     */
    private Integer rankInGrade;

    /**
     * 各科目成绩
     */
    private List<SubjectScore> subjectScores;

    /**
     * 科目成绩
     */
    @Data
    @Builder
    public static class SubjectScore {
        private Long subjectId;
        private String subjectName;
        private BigDecimal score;
        private BigDecimal fullScore;
        private Integer rankInClass;
        private Integer rankInGrade;
        private Boolean isAbsent;
    }

}
