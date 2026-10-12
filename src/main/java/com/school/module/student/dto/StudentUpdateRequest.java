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

    /**
     * 绑定的登录账号 ID（sys_user.id）；null 不改动，0 解绑。
     * ponytail: 与 TeacherUpdateRequest.userId 同因——已存在的档案此前无法补绑账号，学生端自查全部失效。
     */
    private Long userId;

}
