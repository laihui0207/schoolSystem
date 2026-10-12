package com.school.module.export.service;

import com.school.module.score.dto.BatchInputResult;
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
     * 导入成绩Excel（按 考试/班级/科目 三元组，学校ID取自学生档案）
     *
     * @param examId 考试ID
     * @param classId 班级ID
     * @param subjectId 科目ID
     * @param file Excel文件
     * @return 成功/失败条数与逐条失败原因
     */
    BatchInputResult importScores(Long examId, Long classId, Long subjectId, MultipartFile file);

}
