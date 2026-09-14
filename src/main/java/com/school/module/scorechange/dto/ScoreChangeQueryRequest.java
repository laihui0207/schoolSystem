package com.school.module.scorechange.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * 成绩变更查询请求
 *
 * @author School System
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ScoreChangeQueryRequest extends BasePageQuery {

    /**
     * 考试ID
     */
    private Long examId;

    /**
     * 学生ID
     */
    private Long studentId;

    /**
     * 申请人ID
     */
    private Long applicantId;

    /**
     * 状态
     */
    private Integer status;

    public ScoreChangeQueryRequest() {
        super();
    }

}
