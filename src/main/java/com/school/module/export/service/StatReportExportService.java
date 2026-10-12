package com.school.module.export.service;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 统计报表 Excel 导出服务接口
 *
 * @author School System
 */
public interface StatReportExportService {

    /**
     * 导出统计报表（三个 sheet：概览 / 科目统计 / 分数段）
     *
     * @param examId   考试ID
     * @param classId  班级ID（可选；为空时全校各班逐一列出并附全校合计）
     * @param response HTTP响应
     */
    void exportStatReport(Long examId, Long classId, HttpServletResponse response);

}
