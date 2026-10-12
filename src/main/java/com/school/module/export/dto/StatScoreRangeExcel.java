package com.school.module.export.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 统计报表 Sheet3「分数段」：班级 × 分数段 一行（分段按总分得分率，见 StatServiceImpl.calculateScoreRanges）
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatScoreRangeExcel {

    @ExcelProperty("班级")
    private String className;

    @ExcelProperty("分数段")
    private String range;

    @ExcelProperty("人数")
    private Integer count;

    @ExcelProperty("占比(%)")
    private BigDecimal percentage;

}
