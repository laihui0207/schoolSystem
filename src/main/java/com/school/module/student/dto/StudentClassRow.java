package com.school.module.student.dto;

import lombok.Data;

/**
 * 学生-班级关联的扁平查询行（批量查询用，避免 N+1）
 *
 * @author School System
 */
@Data
public class StudentClassRow {

    /**
     * 学生ID
     */
    private Long studentId;

    /**
     * 班级ID
     */
    private Long classId;

    /**
     * 班级名称
     */
    private String className;

    /**
     * 年级名称
     */
    private String gradeName;

}
