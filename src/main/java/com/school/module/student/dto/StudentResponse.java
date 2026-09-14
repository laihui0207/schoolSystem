package com.school.module.student.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生响应
 *
 * @author School System
 */
@Data
@Builder
public class StudentResponse {

    /**
     * 主键ID
     */
    private Long id;

    /**
     * 关联用户ID
     */
    private Long userId;

    /**
     * 学号
     */
    private String studentNo;

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
     * 状态名称
     */
    private String statusName;

    /**
     * 所在班级列表
     */
    private List<ClassInfo> classes;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 班级信息
     */
    @Data
    @Builder
    public static class ClassInfo {
        private Long classId;
        private String className;
        private String gradeName;
    }

}
