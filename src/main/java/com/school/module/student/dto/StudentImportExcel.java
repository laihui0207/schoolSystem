package com.school.module.student.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.school.module.student.roster.RosterCells;
import lombok.Data;

/**
 * 学生导入模板行（下载模板与读取导入文件共用）
 * <p>
 * 列顺序：学号* | 姓名* | 性别(男/女) | 出生日期 | 入学日期 | 班级名称 | 是否开通账号(是/否)。
 * ponytail: 全部按 String 读入，由 RosterCells 逐行解析——EasyExcel 的类型转换失败会中断整次读取且报错不含行号。
 * 读取按 index 匹配，表头文字改动不影响解析。
 *
 * @author School System
 */
@Data
@ColumnWidth(18)
public class StudentImportExcel {

    @ExcelProperty(value = "学号*", index = 0)
    private String studentNo;

    @ExcelProperty(value = "姓名*", index = 1)
    private String name;

    @ExcelProperty(value = "性别(男/女)", index = 2)
    private String gender;

    @ExcelProperty(value = "出生日期", index = 3)
    private String birthDate;

    @ExcelProperty(value = "入学日期", index = 4)
    private String enrollmentDate;

    @ExcelProperty(value = "班级名称", index = 5)
    private String className;

    @ExcelProperty(value = "是否开通账号(是/否)", index = 6)
    private String openAccount;

    /**
     * 整行空白（Excel 尾部格式行）——跳过而不是报「学号不能为空」
     */
    public boolean isBlankRow() {
        return RosterCells.trimToNull(studentNo) == null
                && RosterCells.trimToNull(name) == null
                && RosterCells.trimToNull(gender) == null
                && RosterCells.trimToNull(birthDate) == null
                && RosterCells.trimToNull(enrollmentDate) == null
                && RosterCells.trimToNull(className) == null
                && RosterCells.trimToNull(openAccount) == null;
    }

}
