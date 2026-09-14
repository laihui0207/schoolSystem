package com.school.module.subject.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.school.common.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 科目实体
 *
 * @author School System
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("subject")
public class Subject extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 学校ID */
    private Long schoolId;

    /** 科目名称 */
    private String subjectName;

    /** 科目编码 */
    private String subjectCode;

    /** 满分 */
    private BigDecimal fullScore;

    /** 及格分 */
    private BigDecimal passScore;

}
