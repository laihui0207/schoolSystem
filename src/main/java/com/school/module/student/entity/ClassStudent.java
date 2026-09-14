package com.school.module.student.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 班级学生关联实体
 *
 * @author School System
 */
@Data
@TableName("class_student")
public class ClassStudent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 班级ID
     */
    private Long classId;

    /**
     * 学生ID
     */
    private Long studentId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

}
