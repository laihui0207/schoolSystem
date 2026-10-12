package com.school.module.student.dto;

import com.alibaba.excel.annotation.ExcelProperty;
import com.alibaba.excel.annotation.write.style.ColumnWidth;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 学生档案导出行
 * <p>
 * 日期以 yyyy-MM-dd 字符串写出：EasyExcel 3.3.2 对 LocalDate 的写转换器支持不稳定，字符串最稳妥。
 *
 * @author School System
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ColumnWidth(16)
public class StudentExportExcel {

    @ExcelProperty("学号")
    private String studentNo;

    @ExcelProperty("姓名")
    private String name;

    @ExcelProperty("性别")
    private String gender;

    @ExcelProperty("出生日期")
    private String birthDate;

    @ExcelProperty("入学日期")
    private String enrollmentDate;

    @ExcelProperty("年级")
    private String gradeName;

    @ExcelProperty("班级")
    private String className;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("账号用户名")
    private String username;

    @ExcelProperty("学校")
    private String schoolName;

}
