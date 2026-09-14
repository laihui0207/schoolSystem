package com.school.module.stat.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 学生成绩趋势响应
 *
 * @author School System
 */
@Data
@Builder
public class StudentTrendResponse {

    /**
     * 学生ID
     */
    private Long studentId;

    /**
     * 学生姓名
     */
    private String studentName;

    /**
     * 历次考试成绩趋势
     */
    private List<ExamTrend> examTrends;

    /**
     * 各科目雷达图数据
     */
    private List<SubjectRadar> subjectRadars;

    /**
     * 考试趋势
     */
    @Data
    @Builder
    public static class ExamTrend {
        private Long examId;
        private String examName;
        private LocalDate examDate;
        private BigDecimal totalScore;
        private BigDecimal averageScore;
        private Integer rankInClass;
        private Integer rankInGrade;
        private java.util.Map<String, BigDecimal> subjectScores;
    }

    /**
     * 科目雷达图数据
     */
    @Data
    @Builder
    public static class SubjectRadar {
        private Long subjectId;
        private String subjectName;
        private BigDecimal score;
        private BigDecimal fullScore;
        private BigDecimal percentage; // 得分率
    }

}
