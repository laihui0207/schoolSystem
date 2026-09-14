package com.school.module.grade.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 年级创建请求
 *
 * @author School System
 */
@Data
public class GradeCreateRequest {

    @NotNull(message = "学校ID不能为空")
    private Long schoolId;

    @NotBlank(message = "年级名称不能为空")
    private String gradeName;

    @NotNull(message = "年级编码不能为空")
    private Integer gradeCode;

}
