package com.school.module.subject.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 科目响应
 *
 * @author School System
 */
@Data
@Builder
public class SubjectResponse {

    private Long id;
    private Long schoolId;
    private String subjectName;
    private String subjectCode;
    private BigDecimal fullScore;
    private BigDecimal passScore;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
