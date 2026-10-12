package com.school.module.stat.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 科目成绩统计响应（某次考试的单科维度）
 *
 * @author School System
 */
@Data
@Builder
public class SubjectStatResponse {

    /**
     * 科目ID
     */
    private Long subjectId;

    /**
     * 科目名称
     */
    private String subjectName;

    /**
     * 考试ID
     */
    private Long examId;

    /**
     * 参考人数（含缺考）
     */
    private Integer totalStudents;

    /**
     * 实际参考人数
     */
    private Integer actualStudents;

    /**
     * 平均分
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
     * 标准差（总体）
     */
    private BigDecimal standardDeviation;

    /**
     * 各班级平均分
     */
    private List<ClassAverage> classAverages;

    /**
     * 各年级分数分布（箱线图五数）
     */
    private List<GradeBox> gradeBoxes;

    /**
     * 分数段分布
     */
    private List<ClassStatResponse.ScoreRange> scoreRanges;

    /**
     * 班级平均分
     */
    @Data
    @Builder
    public static class ClassAverage {
        private Long classId;
        private String className;
        private BigDecimal averageScore;
    }

    /**
     * 年级箱线图数据
     */
    @Data
    @Builder
    public static class GradeBox {
        private Long gradeId;
        private String gradeName;
        private BigDecimal min;
        private BigDecimal q1;
        private BigDecimal median;
        private BigDecimal q3;
        private BigDecimal max;
    }

}

