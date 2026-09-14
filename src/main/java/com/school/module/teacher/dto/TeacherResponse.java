package com.school.module.teacher.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 教师响应
 *
 * @author School System
 */
@Data
@Builder
public class TeacherResponse {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 关联用户ID
     */
    private Long userId;

    /**
     * 工号
     */
    private String teacherNo;

    /**
     * 学校ID
     */
    private Long schoolId;

    /**
     * 学校名称
     */
    private String schoolName;

    /**
     * 姓名
     */
    private String name;

    /**
     * 性别：1-男，2-女
     */
    private Integer gender;

    /**
     * 性别名称
     */
    private String genderName;

    /**
     * 主教科目ID
     */
    private Long subjectId;

    /**
     * 主教科目名称
     */
    private String subjectName;

    /**
     * 状态：0-离职，1-在职
     */
    private Integer status;

    /**
     * 状态名称
     */
    private String statusName;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

}
