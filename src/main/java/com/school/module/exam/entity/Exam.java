package com.school.module.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.school.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 考试实体
 *
 * @author School System
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("exam")
public class Exam extends BaseEntity {

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
     * 学期ID
     */
    private Long termId;

    /**
     * 考试名称
     */
    private String examName;

    /**
     * 考试类型：1-月考，2-期中，3-期末，4-模拟，5-其他
     */
    private Integer examType;

    /**
     * 考试日期
     */
    private LocalDate examDate;

    /**
     * 参与年级ID列表（逗号分隔）
     */
    private String gradeIds;

    /**
     * 状态：0-未开始，1-录入中，2-审核中，3-已发布
     */
    private Integer status;

}
