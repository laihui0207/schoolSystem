package com.school.module.term.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 更新学年学期请求DTO
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcademicTermUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 学年（格式：2024-2025）
     */
    @Pattern(regexp = "^\\d{4}-\\d{4}$", message = "学年格式不正确，应为：2024-2025")
    private String year;

    /**
     * 学期（1-第一学期，2-第二学期）
     */
    private Integer term;

    /**
     * 开始日期
     */
    private LocalDate startDate;

    /**
     * 结束日期
     */
    private LocalDate endDate;

}
