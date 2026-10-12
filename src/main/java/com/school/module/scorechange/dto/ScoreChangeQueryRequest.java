package com.school.module.scorechange.dto;

import com.school.common.base.BasePageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Set;

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

    // ponytail: 下面两个是服务端范围字段，listChangeLogs 会先清空再按身份填入，客户端传入无效
    /**
     * 学校ID：校管理员查询时由 Service 填入本校ID
     */
    private Long schoolId;

    /**
     * 班级ID集合：教师查询时由 Service 填入任教 ∪ 班主任班级
     */
    private Set<Long> classIds;

    public ScoreChangeQueryRequest() {
        super();
    }

}
