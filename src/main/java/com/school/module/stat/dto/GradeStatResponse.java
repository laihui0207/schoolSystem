package com.school.module.stat.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 年级成绩统计响应
 *
 * @author School System
 */
@Data
@Builder
public class GradeStatResponse {

    /**
     * 年级ID
     */
    private Long gradeId;

    /**
     * 年级名称
     */
    private String gradeName;

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
     * 年级平均分
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
     * 及格率(%)
     */
    private BigDecimal passRate;

    /**
     * 优秀率(%)
     */
    private BigDecimal excellentRate;

    /**
     * 各班级对比
     */
    private List<ClassComparison> classComparisons;

    /**
     * 各年级对比（按班级所属年级归并）
     */
    private List<GradeStat> gradeStats;

    /**
     * 各科目统计
     */
    private List<SubjectStat> subjectStats;

    /**
     * 班级对比
     */
    @Data
    @Builder
    public static class ClassComparison {
        private Long classId;
        private String className;
        private BigDecimal averageScore;
        private BigDecimal maxScore;
        private BigDecimal minScore;
        private BigDecimal passRate;
        private BigDecimal excellentRate;
        private Integer rank;
    }

    /**
     * 年级对比
     */
    @Data
    @Builder
    public static class GradeStat {
        private Long gradeId;
        private String gradeName;
        private BigDecimal averageScore;
    }

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
    }

}

