package com.school.module.teacher.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 教师档案导出行
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ColumnWidth(16)
public class TeacherExportExcel {

    @ExcelProperty("工号")
    private String teacherNo;

    @ExcelProperty("姓名")
    private String name;

    @ExcelProperty("性别")
    private String gender;

    @ExcelProperty("主教科目")
    private String subjectName;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("账号用户名")
    private String username;

    @ExcelProperty("学校")
    private String schoolName;

}
