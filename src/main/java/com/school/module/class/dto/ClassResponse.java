package com.school.module.class_module.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 班级响应
 *
 * @author School System
 */
@Data
@Builder
public class ClassResponse {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 学校ID
     */
    private Long schoolId;

    /**
     * 学校名称
     */
    private String schoolName;

    /**
     * 年级ID
     */
    private Long gradeId;

    /**
     * 年级名称
     */
    private String gradeName;

    /**
     * 班级名称
     */
    private String className;

    /**
     * 班级编码
     */
    private Integer classCode;

    /**
     * 学期ID
     */
    private Long termId;

    /**
     * 学期名称
     */
    private String termName;

    /**
     * 班主任ID
     */
    private Long headTeacherId;

    /**
     * 班主任姓名
     */
    private String headTeacherName;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

}
