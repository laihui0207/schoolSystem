package com.school.module.score.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ClassScoreItemDto {
    private Long id;
    private Long studentId;
    // ponytail: D-ai-5——subjectId 为空时一个学生多科多行，必须带上科目标识才能区分
    private Long subjectId;
    private String subjectName;
    private String studentNo;
    private String studentName;
    private BigDecimal score;
    private Boolean isAbsent;
    private String remark;
    private Integer rankInClass;
    private Integer rankInGrade;
    private Integer rankInSchool;
}
