package com.school.module.export.controller;

import com.school.common.result.Result;
import com.school.module.export.service.ExportService;
import com.school.module.export.service.StatReportExportService;
import com.school.module.score.dto.BatchInputResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 导入导出控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;
    private final StatReportExportService statReportExportService;

    /**
     * 导出统计报表 Excel（概览 / 科目统计 / 分数段 三个 sheet；classId 为空时全校各班并附全校合计）
     */
    @GetMapping("/stat-report")
    public void exportStatReport(
            @RequestParam Long examId,
            @RequestParam(required = false) Long classId,
            HttpServletResponse response) {
        statReportExportService.exportStatReport(examId, classId, response);
    }

    /**
     * 导出成绩Excel
     */
    @GetMapping("/scores")
    public void exportScores(
            @RequestParam Long examId,
            @RequestParam(required = false) Long classId,
            HttpServletResponse response) {
        exportService.exportScores(examId, classId, response);
    }

    /**
     * 下载成绩导入模板
     */
    @GetMapping("/score-template")
    public void downloadScoreTemplate(HttpServletResponse response) {
        exportService.downloadScoreTemplate(response);
    }

    /**
     * 导入成绩Excel（按 考试/班级/科目 三元组）
     */
    @PostMapping("/import-scores")
    public Result<BatchInputResult> importScores(
            @RequestParam Long examId,
            @RequestParam Long classId,
            @RequestParam Long subjectId,
            @RequestParam("file") MultipartFile file) {
        // ponytail: 与 /scores/batch-input 同构，返回逐条失败明细（契约 1）
        BatchInputResult result = exportService.importScores(examId, classId, subjectId, file);
        return Result.success(result);
    }

}
