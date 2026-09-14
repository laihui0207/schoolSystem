package com.school.module.class_module.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 班级创建请求
 *
 * @author School System
 */
@Data
public class ClassCreateRequest {

    /**
     * 学校ID
     */
    @NotNull(message = "学校ID不能为空")
    private Long schoolId;

    /**
     * 年级ID
     */
    @NotNull(message = "年级ID不能为空")
    private Long gradeId;

    /**
     * 班级名称
     */
    @NotBlank(message = "班级名称不能为空")
    private String className;

    /**
     * 班级编码
     */
    @NotNull(message = "班级编码不能为空")
    private Integer classCode;

    /**
     * 学期ID
     */
    @NotNull(message = "学期ID不能为空")
    private Long termId;

    /**
     * 班主任ID
     */
    private Long headTeacherId;

}
