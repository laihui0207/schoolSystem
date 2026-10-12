package com.school.module.teacher.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.school.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 教师实体
 *
 * @author School System
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("teacher")
public class Teacher extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
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

    /**
     * 学校名称（非数据库字段）
     */
    @TableField(exist = false)
    private String schoolName;

    /**
     * 主教科目名称（非数据库字段）
     */
    @TableField(exist = false)
    private String subjectName;

}

