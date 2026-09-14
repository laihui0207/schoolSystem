package com.school.module.exam.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 考试科目关联实体
 *
 * @author School System
 */
@Data
@TableName("exam_subject")
public class ExamSubject implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 主键ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 考试ID
     */
    private Long examId;

    /**
     * 科目ID
     */
    private Long subjectId;

    /**
     * 满分
     */
    private BigDecimal fullScore;

    /**
     * 排序顺序
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;

    /**
     * 删除标志：0-未删除，1-已删除
     */
    private Integer deleted;

}
