package com.school.module.teacher.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 教师响应
 */
@Data
@Builder
public class TeacherResponse {

    private Long id;
    private Long userId;
    private String teacherNo;
    private Long schoolId;
    private String schoolName;
    private String name;
    private Integer gender;
    private String genderName;
    private Long subjectId;
    private String subjectName;
    private Integer status;
    private String statusName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 是否班主任（任教班级中含班主任班级则为 true） */
    private Boolean headTeacher;

    /** 任教班级+科目列表 */
    private List<ClassAssignment> classes;

    @Data
    @Builder
    public static class ClassAssignment {
        private Long classId;
        private String className;
        private Long subjectId;
        private String subjectName;
        private Boolean isHeadTeacher;
    }
}

