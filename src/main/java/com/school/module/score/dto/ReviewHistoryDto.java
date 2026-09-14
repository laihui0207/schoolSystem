package com.school.module.score.dto;

import lombok.Data;

@Data
public class ReviewHistoryDto {
    private Long id;
    private String operator;
    private String operateTime;
    /** approve | reject */
    private String action;
    private String reason;
    private String remark;
}
