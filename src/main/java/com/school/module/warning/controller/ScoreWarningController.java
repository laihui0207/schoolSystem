package com.school.module.warning.controller;

import com.school.common.result.Result;
import com.school.module.warning.service.ScoreWarningService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 成绩预警控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/api/warnings")
@RequiredArgsConstructor
public class ScoreWarningController {

    private final ScoreWarningService warningService;

    /**
     * 触发考试成绩预警检查
     */
    @PostMapping("/check-exam")
    public Result<Void> checkExamWarnings(@RequestParam Long examId) {
        warningService.checkScoreWarnings(examId);
        return Result.success();
    }

    /**
     * 分析学生成绩趋势
     */
    @GetMapping("/trend/{studentId}")
    public Result<Map<String, Object>> analyzeScoreTrend(
            @PathVariable Long studentId,
            @RequestParam(required = false) Long subjectId) {
        Map<String, Object> trend = warningService.analyzeScoreTrend(studentId, subjectId);
        return Result.success(trend);
    }

}
