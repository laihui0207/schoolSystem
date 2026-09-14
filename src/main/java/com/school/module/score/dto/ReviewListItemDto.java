package com.school.module.score.dto;

import lombok.Data;

@Data
public class ReviewListItemDto {
    private Long id;
    private Long examId;
    private String examName;
    private Long classId;
    private String className;
    private Long subjectId;
    private String subjectName;
    private double fullScore;
    private String inputPerson;
    private String inputTime;
    /** pending | approved | rejected */
    private String status;
    private String reviewPerson;
    private String reviewTime;
}
