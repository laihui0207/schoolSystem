package com.school.module.scorechange.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.result.Result;
import com.school.module.scorechange.dto.*;
import com.school.module.scorechange.service.ScoreChangeService;
import com.school.security.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 成绩变更管理控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/score-changes")
@RequiredArgsConstructor
public class ScoreChangeController {

    private final ScoreChangeService scoreChangeService;

    /**
     * 申请成绩修改
     */
    @PostMapping("/apply")
    // ponytail: 角色粗筛放这里，"是否任教该班该科"的细粒度判定在 Service 里做（需要先查出 score 的班级/科目）
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Result<ScoreChangeLogResponse> applyScoreChange(
            @Valid @RequestBody ScoreChangeApplyRequest request) {
        ScoreChangeLogResponse response = scoreChangeService.applyScoreChange(request, SecurityUtils.currentUserId());
        return Result.success(response);
    }

    /**
     * 审批成绩修改
     */
    @PostMapping("/approve")
    @PreAuthorize("hasRole('SUPER_ADMIN') or hasRole('SCHOOL_ADMIN') or principal.headTeacher")
    public Result<Void> approveScoreChange(
            @Valid @RequestBody ScoreChangeApproveRequest request) {
        scoreChangeService.approveScoreChange(request, SecurityUtils.currentUserId());
        return Result.success();
    }

    /**
     * 查询变更日志详情
     */
    @GetMapping("/{id}")
    // ponytail: 读路径原先只要求 authenticated，学生可逐条翻看全校变更记录；角色粗筛在此，归属校验在 Service
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Result<ScoreChangeLogResponse> getChangeLogById(@PathVariable Long id) {
        ScoreChangeLogResponse response = scoreChangeService.getChangeLogById(id);
        return Result.success(response);
    }

    /**
     * 分页查询变更日志列表
     */
    @GetMapping
    // ponytail: 同上；教师按任教/班主任班级、校管理员按本校收窄的逻辑在 Service.listChangeLogs
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    public Result<Page<ScoreChangeLogResponse>> listChangeLogs(ScoreChangeQueryRequest request) {
        Page<ScoreChangeLogResponse> page = scoreChangeService.listChangeLogs(request);
        return Result.success(page);
    }

    /**
     * 撤销申请
     */
    @DeleteMapping("/{id}/cancel")
    public Result<Void> cancelApply(@PathVariable Long id) {
        scoreChangeService.cancelApply(id, SecurityUtils.currentUserId());
        return Result.success();
    }

}
