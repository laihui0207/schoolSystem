package com.school.module.student.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 学生更新请求
 *
 * @author School System
 */
@Data
public class StudentUpdateRequest {

    /**
     * 姓名
     */
    private String name;

    /**
     * 性别：1-男，2-女
     */
    private Integer gender;

    /**
     * 出生日期
     */
    private LocalDate birthDate;

    /**
     * 入学日期
     */
    private LocalDate enrollmentDate;

    /**
     * 状态：0-离校，1-在读，2-休学
     */
    private Integer status;

}
