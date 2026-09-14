package com.school.module.export.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 成绩导入Excel模板
 *
 * @author School System
 */
@Data
public class ScoreImportExcel {

    @ExcelProperty("学号")
    private String studentNo;

    @ExcelProperty("学生姓名")
    private String studentName;

    @ExcelProperty("科目名称")
    private String subjectName;

    @ExcelProperty("成绩")
    private BigDecimal score;

    @ExcelProperty("是否缺考(0-否,1-是)")
    private Integer isAbsent;

}
