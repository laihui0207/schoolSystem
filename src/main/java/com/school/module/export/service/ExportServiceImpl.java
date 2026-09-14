package com.school.module.export.service;

import com.alibaba.excel.EasyExcel;
import com.school.common.exception.BusinessException;
import com.school.module.export.dto.ScoreExportExcel;
import com.school.module.export.dto.ScoreImportExcel;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.module.score.service.ScoreService;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.subject.entity.Subject;
import com.school.module.subject.mapper.SubjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 导入导出服务实现类
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class ExportServiceImpl implements ExportService {

    private final ScoreMapper scoreMapper;
    private final StudentMapper studentMapper;
    private final SubjectMapper subjectMapper;
    private final ScoreService scoreService;

    @Override
    public void exportScores(Long examId, Long classId, HttpServletResponse response) {
        // 查询成绩数据
        List<Score> scores = scoreMapper.selectByExamId(examId, classId);

        if (scores.isEmpty()) {
            throw new BusinessException("暂无成绩数据");
        }

        // 转换为导出格式
        List<ScoreExportExcel> exportData = scores.stream()
                .map(this::convertToExportExcel)
                .collect(Collectors.toList());

        // 设置响应头
        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("成绩导出_" + System.currentTimeMillis(), StandardCharsets.UTF_8);
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

            // 写入Excel
            EasyExcel.write(response.getOutputStream(), ScoreExportExcel.class)
                    .sheet("成绩单")
                    .doWrite(exportData);
        } catch (IOException e) {
            throw new BusinessException("导出失败: " + e.getMessage());
        }
    }

    @Override
    public void downloadScoreTemplate(HttpServletResponse response) {
        // 创建模板数据（示例）
        List<ScoreImportExcel> templateData = new ArrayList<>();
        ScoreImportExcel example = new ScoreImportExcel();
        example.setStudentNo("20240001");
        example.setStudentName("张三");
        example.setSubjectName("语文");
        example.setScore(BigDecimal.valueOf(95.5));
        example.setIsAbsent(0);
        templateData.add(example);

        try {
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            String fileName = URLEncoder.encode("成绩导入模板", StandardCharsets.UTF_8);
            response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");

            EasyExcel.write(response.getOutputStream(), ScoreImportExcel.class)
                    .sheet("成绩模板")
                    .doWrite(templateData);
        } catch (IOException e) {
            throw new BusinessException("下载模板失败: " + e.getMessage());
        }
    }

    @Override
    public Integer importScores(Long examId, Long classId, Long schoolId, MultipartFile file) {
        try {
            // 读取Excel
            List<ScoreImportExcel> dataList = EasyExcel.read(file.getInputStream())
                    .head(ScoreImportExcel.class)
                    .sheet()
                    .doReadSync();

            if (dataList == null || dataList.isEmpty()) {
                throw new BusinessException("Excel文件为空");
            }

            // 查询学生和科目映射
            List<Student> students = dataList.stream()
                    .map(d -> studentMapper.selectByStudentNo(d.getStudentNo()))
                    .collect(Collectors.toList());

            Map<String, Student> studentMap = students.stream()
                    .filter(s -> s != null)
                    .collect(Collectors.toMap(Student::getStudentNo, s -> s));

            // 批量导入
            int successCount = 0;
            for (ScoreImportExcel data : dataList) {
                try {
                    Student student = studentMap.get(data.getStudentNo());
                    if (student == null) {
                        continue; // 跳过找不到的学生
                    }

                    // 查询科目ID（简化处理，实际应该通过名称查询）
                    // 这里假设已经有科目ID，需要根据实际情况调整

                    // 检查是否已存在
                    Score existingScore = scoreMapper.selectByExamStudentSubject(
                            examId, student.getId(), 1L); // 科目ID需要实际查询

                    if (existingScore != null) {
                        continue;
                    }

                    // 创建成绩记录
                    Score score = new Score();
                    score.setExamId(examId);
                    score.setStudentId(student.getId());
                    score.setSubjectId(1L); // 科目ID需要实际查询
                    score.setClassId(classId);
                    score.setSchoolId(schoolId);
                    score.setScore(data.getScore());
                    score.setIsAbsent(data.getIsAbsent() != null ? data.getIsAbsent() : 0);
                    score.setStatus(0);
                    score.setRecordedAt(LocalDateTime.now());

                    scoreMapper.insert(score);
                    successCount++;
                } catch (Exception e) {
                    // 继续处理下一条
                    continue;
                }
            }

            return successCount;
        } catch (IOException e) {
            throw new BusinessException("读取Excel失败: " + e.getMessage());
        }
    }

    /**
     * 转换为导出Excel格式
     */
    private ScoreExportExcel convertToExportExcel(Score score) {
        // 这里简化处理，实际应该关联查询学生、科目等信息
        return ScoreExportExcel.builder()
                .examName("考试名称") // 需要关联查询
                .studentNo("学号") // 需要关联查询
                .studentName("学生姓名") // 需要关联查询
                .className("班级名称") // 需要关联查询
                .subjectName("科目名称") // 需要关联查询
                .score(score.getScore())
                .isAbsentText(score.getIsAbsent() == 1 ? "是" : "否")
                .rankInClass(score.getRankInClass())
                .rankInGrade(score.getRankInGrade())
                .build();
    }

}
