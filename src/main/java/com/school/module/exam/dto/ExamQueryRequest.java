package com.school.module.exam.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;

/**
 * 考试查询请求
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ExamQueryRequest extends BasePageQuery {

    /**
     * 学校ID
     */
    private Long schoolId;

    /**
     * 学期ID
     */
    private Long termId;

    /**
     * 年级ID
     */
    private Long gradeId;

    /**
     * 考试名称（模糊查询）
     */
    private String examName;

    /**
     * 考试类型
     */
    private Integer examType;

    /**
     * 状态
     */
    private Integer status;

    /**
     * 开始日期
     */
    private LocalDate startDate;

    /**
     * 结束日期
     */
    private LocalDate endDate;

    public ExamQueryRequest() {
        super();
    }

}
