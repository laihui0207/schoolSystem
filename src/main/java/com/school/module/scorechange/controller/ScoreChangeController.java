package com.school.module.scorechange.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.scorechange.dto.*;
import com.school.module.scorechange.service.ScoreChangeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 成绩变更管理控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/api/score-changes")
@RequiredArgsConstructor
public class ScoreChangeController {

    private final ScoreChangeService scoreChangeService;

    /**
     * 申请成绩修改
     */
    @PostMapping("/apply")
    public Result<ScoreChangeLogResponse> applyScoreChange(
            @Valid @RequestBody ScoreChangeApplyRequest request,
            @RequestParam Long applicantId) {
        ScoreChangeLogResponse response = scoreChangeService.applyScoreChange(request, applicantId);
        return Result.success(response);
    }

    /**
     * 审批成绩修改
     */
    @PostMapping("/approve")
    public Result<Void> approveScoreChange(
            @Valid @RequestBody ScoreChangeApproveRequest request,
            @RequestParam Long approverId) {
        scoreChangeService.approveScoreChange(request, approverId);
        return Result.success();
    }

    /**
     * 查询变更日志详情
     */
    @GetMapping("/{id}")
    public Result<ScoreChangeLogResponse> getChangeLogById(@PathVariable Long id) {
        ScoreChangeLogResponse response = scoreChangeService.getChangeLogById(id);
        return Result.success(response);
    }

    /**
     * 分页查询变更日志列表
     */
    @GetMapping
    public Result<Page<ScoreChangeLogResponse>> listChangeLogs(ScoreChangeQueryRequest request) {
        Page<ScoreChangeLogResponse> page = scoreChangeService.listChangeLogs(request);
        return Result.success(page);
    }

    /**
     * 撤销申请
     */
    @DeleteMapping("/{id}/cancel")
    public Result<Void> cancelApply(
            @PathVariable Long id,
            @RequestParam Long applicantId) {
        scoreChangeService.cancelApply(id, applicantId);
        return Result.success();
    }

}
