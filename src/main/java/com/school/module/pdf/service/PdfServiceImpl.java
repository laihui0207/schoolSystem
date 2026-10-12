package com.school.module.pdf.service;

import com.school.common.exception.BusinessException;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * PDF生成服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class PdfServiceImpl implements PdfService {

    private final ScoreMapper scoreMapper;

    @Override
    public void generateScoreReport(Long examId, Long studentId, HttpServletResponse response) {
        // 查询学生成绩
        List<Score> scores = scoreMapper.selectByExamId(SecurityUtils.currentSchoolIdForQuery(), examId, null).stream()
                .filter(s -> s.getStudentId().equals(studentId))
                .toList();

        if (scores.isEmpty()) {
            throw new BusinessException("未找到学生成绩");
        }

        try {
            // 设置响应头
            response.setContentType("application/pdf");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("成绩单_" + studentId, StandardCharsets.UTF_8);
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".pdf");

            // TODO: 使用iText生成PDF
            // 这里简化处理，实际需要完整的PDF生成逻辑
            String content = "成绩单PDF生成功能 - 待完善\n\n";
            content += "学生ID: " + studentId + "\n";
            content += "考试ID: " + examId + "\n";
            content += "科目数量: " + scores.size();

            response.getOutputStream().write(content.getBytes(StandardCharsets.UTF_8));
            response.getOutputStream().flush();

        } catch (IOException e) {
            throw new BusinessException("生成PDF失败: " + e.getMessage());
        }
    }

    @Override
    public void generateClassScoreReports(Long examId, Long classId, HttpServletResponse response) {
        // TODO: 批量生成班级成绩单
        throw new BusinessException("批量生成功能开发中");
    }

}
