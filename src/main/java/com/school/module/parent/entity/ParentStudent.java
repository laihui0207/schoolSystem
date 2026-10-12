package com.school.module.parent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.school.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 家长学生关联实体（parent_student）
 * <p>
 * ponytail: 表无 school_id，不在租户插件清单内；同校约束在 ParentService.bindChild 里按
 * 家长 sys_user.school_id = student.school_id 校验。V18 补了 deleted/updated_at 后才能继承 BaseEntity。
 *
 * @author School System
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("parent_student")
public class ParentStudent extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 家长用户ID（sys_user.id，user_type=5）
     */
    private Long parentUserId;

    /**
     * 学生ID（student.id）
     */
    private Long studentId;

    /**
     * 关系：字典 relationship 的 dict_value（father/mother/other）
     */
    private String relationship;

}
