package com.school.module.stat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 区域（跨校）成绩对比响应
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionStatResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String regionCode;

    private Integer examType;

    private String year;

    private Integer term;

    /**
     * 各校聚合，已排名的在前（按均分降序），没有可比考试的学校排在最后且 rank 为空
     */
    private List<SchoolStat> schools;

    /**
     * 各科跨校对比；科目按名称跨校对齐，subjectId 只是首个遇到的学校的科目 ID
     */
    private List<SubjectCompare> subjects;

    /**
     * 单所学校的聚合
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchoolStat implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long schoolId;

        private String schoolName;

        /**
         * 该校该学期该类型 exam_date 最新的一场已发布考试；没有则为空
         */
        private Long examId;

        private String examName;

        /**
         * 参考人数（至少有一科有效成绩的学生数）
         */
        private Integer studentCount;

        /**
         * 学生总分均分
         */
        private BigDecimal averageScore;

        /**
         * 总分及格率(%)：总分 ≥ Σ各科及格线
         */
        private BigDecimal passRate;

        /**
         * 总分优秀率(%)：总分 ≥ Σ各科满分×优秀比例
         */
        private BigDecimal excellentRate;

        /**
         * 均分名次，并列同名次；没有可比成绩的学校为空
         */
        private Integer rank;
    }

    /**
     * 一门科目的跨校对比
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubjectCompare implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long subjectId;

        private String subjectName;

        private List<SchoolSubjectAverage> schools;
    }

    /**
     * 某校某科的均分
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SchoolSubjectAverage implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long schoolId;

        private BigDecimal averageScore;
    }

}
