package com.school.module.grade.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 年级响应
 *
 * @author School System
 */
@Data
@Builder
public class GradeResponse {

    private Long id;
    private Long schoolId;
    private String gradeName;
    private Integer gradeCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

}
