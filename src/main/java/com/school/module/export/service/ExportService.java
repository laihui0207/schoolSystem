package com.school.module.export.service;

import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 导入导出服务接口
 *
 * @author School System
 */
public interface ExportService {

    /**
     * 导出成绩Excel
     *
     * @param examId 考试ID
     * @param classId 班级ID（可选）
     * @param response HTTP响应
     */
    void exportScores(Long examId, Long classId, HttpServletResponse response);

    /**
     * 下载成绩导入模板
     *
     * @param response HTTP响应
     */
    void downloadScoreTemplate(HttpServletResponse response);

    /**
     * 导入成绩Excel
     *
     * @param examId 考试ID
     * @param classId 班级ID
     * @param schoolId 学校ID
     * @param file Excel文件
     * @return 导入成功的数量
     */
    Integer importScores(Long examId, Long classId, Long schoolId, MultipartFile file);

}
