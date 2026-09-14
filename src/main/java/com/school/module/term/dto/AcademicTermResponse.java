package com.school.module.term.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学期响应
 *
 * @author School System
 */
@Data
@Builder
public class AcademicTermResponse {

    private Long id;
    private Long schoolId;
    private String year;
    private Integer term;
    private String termName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer status;
    private String statusName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
