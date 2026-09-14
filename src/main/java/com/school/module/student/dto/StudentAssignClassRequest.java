package com.school.module.student.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 学生分班请求
 *
 * @author School System
 */
@Data
public class StudentAssignClassRequest {

    /**
     * 学生ID
     */
    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    /**
     * 班级ID
     */
    @NotNull(message = "班级ID不能为空")
    private Long classId;

}
