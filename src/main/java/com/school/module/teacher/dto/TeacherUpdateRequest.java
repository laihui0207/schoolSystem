package com.school.module.teacher.dto;

import lombok.Data;

/**
 * 教师更新请求
 *
 * @author School System
 */
@Data
public class TeacherUpdateRequest {

    /**
     * 姓名
     */
    private String name;

    /**
     * 性别：1-男，2-女
     */
    private Integer gender;

    /**
     * 主教科目ID
     */
    private Long subjectId;

    /**
     * 状态：0-离职，1-在职
     */
    private Integer status;

}
