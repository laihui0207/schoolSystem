package com.school.module.pdf.controller;

import com.school.common.result.Result;
import com.school.module.pdf.service.PdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;

/**
 * PDF生成控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
public class PdfController {

    private final PdfService pdfService;

    /**
     * 生成学生成绩单PDF
     */
    @GetMapping("/score-report")
    public void generateScoreReport(
            @RequestParam Long examId,
            @RequestParam Long studentId,
            HttpServletResponse response) {
        pdfService.generateScoreReport(examId, studentId, response);
    }

    /**
     * 批量生成班级成绩单
     */
    @GetMapping("/class-reports")
    public void generateClassScoreReports(
            @RequestParam Long examId,
            @RequestParam Long classId,
            HttpServletResponse response) {
        pdfService.generateClassScoreReports(examId, classId, response);
    }

}
