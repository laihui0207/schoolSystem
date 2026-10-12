package com.school.module.pdf.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 一名学生一场考试的成绩单数据（渲染层只认这个对象，不再碰数据库）
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreReportData {

    private String schoolName;

    private String examName;

    private String termName;

    private String examDate;

    private String studentName;

    private String studentNo;

    private String className;

    private String gradeName;

    private List<SubjectRow> subjectRows;

    private BigDecimal totalScore;

    private BigDecimal totalFullScore;

    /**
     * 总分三档名次（Score.totalRank*），历史数据未重算时为 null，PDF 显示「-」
     */
    private Integer totalRankInClass;

    private Integer totalRankInGrade;

    private Integer totalRankInSchool;

    /**
     * 班级总分均分 / 年级总分均分，无数据时为 null
     */
    private BigDecimal classAverage;

    private BigDecimal gradeAverage;

    private LocalDateTime generatedAt;

    /**
     * 单科一行
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SubjectRow {

        private Long subjectId;

        private String subjectName;

        private BigDecimal fullScore;

        /**
         * 得分；缺考或无记录时为 null
         */
        private BigDecimal score;

        /**
         * 缺考
         */
        private boolean absent;

        /**
         * 该生本场没有该科成绩记录
         */
        private boolean missing;

        private Integer rankInClass;

        private Integer rankInGrade;

        private Integer rankInSchool;

        private BigDecimal classAverage;

        private BigDecimal gradeAverage;

    }

}
