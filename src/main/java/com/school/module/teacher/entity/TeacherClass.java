package com.school.module.teacher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 教师班级关联（teacher_class 表）
 * <p>
 * 唯一索引：uk_teacher_class_subject(teacher_id, class_id, subject_id)
 *
 * @author School System
 */
@Data
@TableName("teacher_class")
public class TeacherClass implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teacherId;

    private Long classId;

    private Long subjectId;

    private LocalDateTime createdAt;

}
