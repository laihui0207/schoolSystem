package com.school.module.term.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 创建学年学期请求DTO
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcademicTermCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 学校ID
     */
    @NotNull(message = "学校ID不能为空")
    private Long schoolId;

    /**
     * 学年（格式：2024-2025）
     */
    @NotBlank(message = "学年不能为空")
    @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "学年格式不正确，应为：2024-2025")
    private String year;

    /**
     * 学期（1-第一学期，2-第二学期）
     */
    @NotNull(message = "学期不能为空")
    private Integer term;

    /**
     * 开始日期
     */
    @NotNull(message = "开始日期不能为空")
    private LocalDate startDate;

    /**
     * 结束日期
     */
    @NotNull(message = "结束日期不能为空")
    private LocalDate endDate;

}
