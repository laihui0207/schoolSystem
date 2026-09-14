package com.school.module.stat.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 班级成绩统计响应
 *
 * @author School System
 */
@Data
@Builder
public class ClassStatResponse {

    /**
     * 班级ID
     */
    private Long classId;

    /**
     * 班级名称
     */
    private String className;

    /**
     * 考试ID
     */
    private Long examId;

    /**
     * 考试名称
     */
    private String examName;

    /**
     * 参考人数
     */
    private Integer totalStudents;

    /**
     * 实际参考人数（不含缺考）
     */
    private Integer actualStudents;

    /**
     * 缺考人数
     */
    private Integer absentStudents;

    /**
     * 班级总分
     */
    private BigDecimal totalScore;

    /**
     * 班级平均分
     */
    private BigDecimal averageScore;

    /**
     * 最高分
     */
    private BigDecimal maxScore;

    /**
     * 最低分
     */
    private BigDecimal minScore;

    /**
     * 及格人数
     */
    private Integer passCount;

    /**
     * 及格率(%)
     */
    private BigDecimal passRate;

    /**
     * 优秀人数（>=85分）
     */
    private Integer excellentCount;

    /**
     * 优秀率(%)
     */
    private BigDecimal excellentRate;

    /**
     * 各科目统计
     */
    private List<SubjectStat> subjectStats;

    /**
     * 分数段分布
     */
    private List<ScoreRange> scoreRanges;

    /**
     * 科目统计
     */
    @Data
    @Builder
    public static class SubjectStat {
        private Long subjectId;
        private String subjectName;
        private BigDecimal averageScore;
        private BigDecimal maxScore;
        private BigDecimal minScore;
        private BigDecimal passRate;
        private BigDecimal excellentRate;
    }

    /**
     * 分数段
     */
    @Data
    @Builder
    public static class ScoreRange {
        private String range; // "0-60", "60-70", "70-80", "80-90", "90-100"
        private Integer count;
        private BigDecimal percentage;
    }

}
