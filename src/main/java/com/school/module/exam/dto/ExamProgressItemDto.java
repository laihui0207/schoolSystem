package com.school.module.exam.dto;

import lombok.Data;

@Data
public class ExamProgressItemDto {
    private Long gradeId;
    private String gradeName;
    private Long classId;
    private String className;
    private Long subjectId;
    private String subjectName;
    private Integer enteredCount;
    private Integer totalCount;
}
