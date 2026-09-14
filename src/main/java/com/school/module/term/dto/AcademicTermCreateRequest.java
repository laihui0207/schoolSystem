package com.school.module.term.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 学期创建请求
 *
 * @author School System
 */
@Data
public class AcademicTermCreateRequest {

    @NotNull(message = "学校ID不能为空")
    private Long schoolId;

    @NotBlank(message = "学年不能为空")
    private String year;

    @NotNull(message = "学期不能为空")
    private Integer term;

    private LocalDate startDate;

    private LocalDate endDate;

    /** 状态：0-已结束，1-进行中 */
    private Integer status;

}
