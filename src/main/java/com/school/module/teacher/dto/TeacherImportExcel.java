package com.school.module.teacher.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import com.school.module.student.roster.RosterCells;
import lombok.Data;

/**
 * 教师导入模板行（下载模板与读取导入文件共用）
 * <p>
 * 列顺序：工号* | 姓名* | 性别(男/女) | 主教科目名称 | 是否开通账号(是/否)。
 * ponytail: 与 StudentImportExcel 同理全部按 String 读入，由 RosterCells 逐行解析。
 *
 * @author School System
 */
@Data
@ColumnWidth(18)
public class TeacherImportExcel {

    @ExcelProperty(value = "工号*", index = 0)
    private String teacherNo;

    @ExcelProperty(value = "姓名*", index = 1)
    private String name;

    @ExcelProperty(value = "性别(男/女)", index = 2)
    private String gender;

    @ExcelProperty(value = "主教科目名称", index = 3)
    private String subjectName;

    @ExcelProperty(value = "是否开通账号(是/否)", index = 4)
    private String openAccount;

    /**
     * 整行空白（Excel 尾部格式行）——跳过而不是报「工号不能为空」
     */
    public boolean isBlankRow() {
        return RosterCells.trimToNull(teacherNo) == null
                && RosterCells.trimToNull(name) == null
                && RosterCells.trimToNull(gender) == null
                && RosterCells.trimToNull(subjectName) == null
                && RosterCells.trimToNull(openAccount) == null;
    }

}
