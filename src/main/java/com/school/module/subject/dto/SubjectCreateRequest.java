package com.school.module.subject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 科目创建请求
 *
 * @author School System
 */
@Data
public class SubjectCreateRequest {

    @NotNull(message = "学校ID不能为空")
    private Long schoolId;

    @NotBlank(message = "科目名称不能为空")
    private String subjectName;

    @NotBlank(message = "科目编码不能为空")
    private String subjectCode;

    private BigDecimal fullScore;

    private BigDecimal passScore;

}
