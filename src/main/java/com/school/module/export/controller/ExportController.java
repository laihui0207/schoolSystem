package com.school.module.export.controller;

import com.school.common.result.Result;
import com.school.module.export.service.ExportService;
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
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

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
     * 导入成绩Excel
     */
    @PostMapping("/import-scores")
    public Result<Integer> importScores(
            @RequestParam Long examId,
            @RequestParam Long classId,
            @RequestParam Long schoolId,
            @RequestParam("file") MultipartFile file) {
        Integer count = exportService.importScores(examId, classId, schoolId, file);
        return Result.success(count);
    }

}
