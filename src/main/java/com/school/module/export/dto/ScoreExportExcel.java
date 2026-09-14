package com.school.module.export.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 成绩导出Excel
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreExportExcel {

    @ExcelProperty("考试名称")
    private String examName;

    @ExcelProperty("学号")
    private String studentNo;

    @ExcelProperty("学生姓名")
    private String studentName;

    @ExcelProperty("班级")
    private String className;

    @ExcelProperty("科目")
    private String subjectName;

    @ExcelProperty("成绩")
    private BigDecimal score;

    @ExcelProperty("是否缺考")
    private String isAbsentText;

    @ExcelProperty("班级排名")
    private Integer rankInClass;

    @ExcelProperty("年级排名")
    private Integer rankInGrade;

}
