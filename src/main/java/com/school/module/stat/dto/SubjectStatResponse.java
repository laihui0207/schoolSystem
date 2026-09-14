package com.school.module.stat.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单科全校统计响应
 *
 * @author School System
 */
@Data
@Builder
public class SubjectStatResponse {

    private Long examId;
    private String examName;
    private Long subjectId;
    private String subjectName;

    /** 参与统计人数 */
    private Integer totalStudents;

    /** 平均分 */
    private BigDecimal averageScore;

    /** 最高分 */
    private BigDecimal maxScore;

    /** 最低分 */
    private BigDecimal minScore;

    /** 标准差 */
    private BigDecimal stdDev;

    /** 及格人数 */
    private Integer passCount;

    /** 及格率(%) */
    private BigDecimal passRate;

    /** 优秀人数（>=85分） */
    private Integer excellentCount;

    /** 优秀率(%) */
    private BigDecimal excellentRate;

    /** 各班平均分 */
    private List<ClassAvg> classAverages;

    /** 分数段分布 */
    private List<ClassStatResponse.ScoreRange> scoreRanges;

    @Data
    @Builder
    public static class ClassAvg {
        private Long classId;
        private String className;
        private BigDecimal averageScore;
    }

}
