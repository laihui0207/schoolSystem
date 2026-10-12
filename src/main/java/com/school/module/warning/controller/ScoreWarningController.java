package com.school.module.warning.controller;

import com.school.common.result.Result;
import com.school.module.warning.service.ScoreWarningService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 成绩预警控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/warnings")
@RequiredArgsConstructor
public class ScoreWarningController {

    private final ScoreWarningService warningService;

    /**
     * 触发考试成绩预警检查
     */
    // ponytail: 触发检查会给全校学生批量发通知，学生/家长不能调用
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    @PostMapping("/check-exam")
    public Result<Void> checkExamWarnings(@RequestParam Long examId) {
        warningService.checkScoreWarnings(examId);
        return Result.success();
    }

    /**
     * 分析学生成绩趋势
     * <p>
     * 所有登录用户可调用；学生只能查本人，校验在 Service 内按 currentStudentId 比对。
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/trend/{studentId}")
    public Result<Map<String, Object>> analyzeScoreTrend(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long subjectId) {
        Map<String, Object> trend = warningService.analyzeScoreTrend(studentId, subjectId);
        return Result.success(trend);
    }

}
