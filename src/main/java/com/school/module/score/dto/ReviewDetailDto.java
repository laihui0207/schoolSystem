package com.school.module.score.dto;

import lombok.Data;

import java.util.List;

@Data
public class ReviewDetailDto {

    @Data
    public static class ExamInfo {
        private Long examId;
        private String examName;
        private String examType;
        private String examDate;
    }

    @Data
    public static class ClassInfo {
        private Long classId;
        private String className;
    }

    @Data
    public static class SubjectInfo {
        private Long subjectId;
        private String subjectName;
        private double fullScore;
    }

    @Data
    public static class InputInfo {
        private String inputPerson;
        private String inputTime;
    }

    @Data
    public static class Statistics {
        private int totalCount;
        private int enteredCount;
        private double averageScore;
        private double highestScore;
        private double lowestScore;
        private double passRate;
        private double excellentRate;
    }

    private ExamInfo examInfo;
    private ClassInfo classInfo;
    private SubjectInfo subjectInfo;
    private InputInfo inputInfo;
    /** pending | approved | rejected */
    private String status;
    private List<ClassScoreItemDto> scores;
    private Statistics statistics;
}
