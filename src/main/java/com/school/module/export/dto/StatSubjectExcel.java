package com.school.module.export.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 统计报表 Sheet2「科目统计」：班级 × 科目 一行
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatSubjectExcel {

    @ExcelProperty("班级")
    private String className;

    @ExcelProperty("科目")
    private String subjectName;

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
