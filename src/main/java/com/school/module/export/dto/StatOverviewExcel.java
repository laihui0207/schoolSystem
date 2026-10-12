package com.school.module.export.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 统计报表 Sheet1「概览」：一行一个班级（末行可为全校合计）
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatOverviewExcel {

    @ExcelProperty("班级")
    private String className;

    @ExcelProperty("人数")
    private Integer totalStudents;

    @ExcelProperty("实考人数")
    private Integer actualStudents;

    @ExcelProperty("平均分")
    private BigDecimal averageScore;

    @ExcelProperty("及格率(%)")
    private BigDecimal passRate;

    @ExcelProperty("优秀率(%)")
    private BigDecimal excellentRate;

    @ExcelProperty("最高分")
    private BigDecimal maxScore;

    @ExcelProperty("最低分")
    private BigDecimal minScore;

}
