package com.school.module.student.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 学生创建请求
 *
 * @author School System
 */
@Data
public class StudentCreateRequest {

    /**
     * 学号
     */
    @NotBlank(message = "学号不能为空")
    private String studentNo;

    /**
     * 学校ID
     */
    @NotNull(message = "学校ID不能为空")
    private Long schoolId;

    /**
     * 姓名
     */
    @NotBlank(message = "姓名不能为空")
    private String name;

    /**
     * 性别：1-男，2-女
     */
    private Integer gender;

    /**
     * 出生日期
     */
    private LocalDate birthDate;

    /**
     * 入学日期
     */
    private LocalDate enrollmentDate;

    /**
     * 关联用户ID（可选）
     */
    private Long userId;

}
