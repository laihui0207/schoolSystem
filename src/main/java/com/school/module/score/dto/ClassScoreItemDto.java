package com.school.module.score.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ClassScoreItemDto {
    private Long id;
    private Long studentId;
    private String studentNo;
    private String studentName;
    private BigDecimal score;
    private Boolean isAbsent;
    private String remark;
}
