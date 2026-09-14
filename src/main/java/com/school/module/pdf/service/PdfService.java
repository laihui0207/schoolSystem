package com.school.module.pdf.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * PDF生成服务接口
 *
 * @author School System
 */
public interface PdfService {

    /**
     * 生成学生成绩单PDF
     *
     * @param examId 考试ID
     * @param studentId 学生ID
     * @param response HTTP响应
     */
    void generateScoreReport(Long examId, Long studentId, HttpServletResponse response);

    /**
     * 批量生成班级成绩单PDF
     *
     * @param examId 考试ID
     * @param classId 班级ID
     * @param response HTTP响应
     */
    void generateClassScoreReports(Long examId, Long classId, HttpServletResponse response);

}
