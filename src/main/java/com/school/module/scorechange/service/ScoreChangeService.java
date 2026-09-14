package com.school.module.scorechange.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.module.scorechange.dto.ScoreChangeApplyRequest;
import com.school.module.scorechange.dto.ScoreChangeApproveRequest;
import com.school.module.scorechange.dto.ScoreChangeLogResponse;
import com.school.module.scorechange.dto.ScoreChangeQueryRequest;

/**
 * 成绩变更服务接口
 *
 * @author School System
 */
public interface ScoreChangeService {

    /**
     * 申请成绩修改
     *
     * @param request 申请请求
     * @param applicantId 申请人ID
     * @return 变更日志响应
     */
    ScoreChangeLogResponse applyScoreChange(ScoreChangeApplyRequest request, Long applicantId);

    /**
     * 审批成绩修改
     *
     * @param request 审批请求
     * @param approverId 审批人ID
     */
    void approveScoreChange(ScoreChangeApproveRequest request, Long approverId);

    /**
     * 查询变更日志详情
     *
     * @param id 日志ID
     * @return 变更日志响应
     */
    ScoreChangeLogResponse getChangeLogById(Long id);

    /**
     * 分页查询变更日志列表
     *
     * @param request 查询请求
     * @return 分页结果
     */
    Page<ScoreChangeLogResponse> listChangeLogs(ScoreChangeQueryRequest request);

    /**
     * 撤销申请
     *
     * @param id 日志ID
     * @param applicantId 申请人ID
     */
    void cancelApply(Long id, Long applicantId);

}
