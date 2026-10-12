package com.school.module.pdf.controller;

import com.school.module.pdf.service.PdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;

/**
 * PDF生成控制器
 *
 * @author School System
 */
@RestController
@RequestMapping("/pdf")
@RequiredArgsConstructor
public class PdfController {

    private final PdfService pdfService;

    /**
     * 生成学生成绩单PDF（学生仅本人；教师限任教/班主任班级；只允许已发布考试）
     */
    @GetMapping("/score-report")
    public void generateScoreReport(
            @RequestParam Long examId,
            @RequestParam Long studentId,
            HttpServletResponse response) {
        pdfService.generateScoreReport(examId, studentId, response);
    }

    /**
     * 批量生成班级成绩单（全班每人一页合成一个 PDF）
     */
    // ponytail: 学生在方法级直接拒绝，服务层再做教师班级范围校验
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'SCHOOL_ADMIN', 'TEACHER')")
    @GetMapping("/class-reports")
    public void generateClassScoreReports(
            @RequestParam Long examId,
            @RequestParam Long classId,
            HttpServletResponse response) {
        pdfService.generateClassScoreReports(examId, classId, response);
    }

}
