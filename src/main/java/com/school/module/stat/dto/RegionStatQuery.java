package com.school.module.stat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 区域（跨校）成绩对比查询条件
 * <p>
 * 考试是按学校建的，没有统一的考试 ID；跨校对比用 (year, term, examType) 对齐各校的考试。
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionStatQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 区域编码（school.region_code）
     */
    @NotBlank(message = "区域编码不能为空")
    @Size(max = 20, message = "区域编码长度不能超过20个字符")
    private String regionCode;

    /**
     * 学年，例如 2024-2025（academic_term.year）
     */
    @NotBlank(message = "学年不能为空")
    @Size(max = 20, message = "学年长度不能超过20个字符")
    private String year;

    /**
     * 学期：1-上学期，2-下学期
     */
    @NotNull(message = "学期不能为空")
    @Min(value = 1, message = "学期只能是1或2")
    @Max(value = 2, message = "学期只能是1或2")
    private Integer term;

    /**
     * 考试类型：1-月考，2-期中，3-期末，4-模拟，5-其他
     */
    @NotNull(message = "考试类型不能为空")
    @Min(value = 1, message = "考试类型取值为1-5")
    @Max(value = 5, message = "考试类型取值为1-5")
    private Integer examType;

}
