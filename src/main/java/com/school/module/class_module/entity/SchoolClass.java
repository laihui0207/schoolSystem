package com.school.module.class_module.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.school.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 班级实体
 *
 * @author School System
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("class")
public class SchoolClass extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 学校ID
     */
    private Long schoolId;

    /**
     * 年级ID
     */
    private Long gradeId;

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
     * 班主任ID
     */
    private Long headTeacherId;

}
