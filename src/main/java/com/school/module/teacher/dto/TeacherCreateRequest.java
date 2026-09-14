package com.school.module.teacher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 教师创建请求
 *
 * @author School System
 */
@Data
public class TeacherCreateRequest {

    /**
     * 工号
     */
    @NotBlank(message = "工号不能为空")
    private String teacherNo;

    /**
     * 学校ID
     */
    @NotNull(message = "学校ID不能为空")
    private Long schoolId;

    /**
     * 姓名
     */
    @NotBlank(message = "姓名不能为空")
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
     * 关联用户ID（可选）
     */
    private Long userId;

}
