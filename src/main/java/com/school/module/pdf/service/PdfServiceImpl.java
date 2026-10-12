package com.school.module.pdf.service;

import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.mapper.ExamMapper;
import com.school.module.pdf.dto.ReportContext;
import com.school.module.pdf.dto.ScoreReportData;
import com.school.module.pdf.render.ScoreReportRenderer;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.StudentMapper;
import com.school.security.service.TeacherScopeService;
import com.school.security.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * PDF 成绩单服务实现
 * <p>
 * 权限：学生只能下载本人成绩单；教师限任教/班主任班级；管理员不限（租户拦截器 + currentSchoolIdForQuery 限校）。
 * 只允许已发布考试（exam.status == 3）。
 *
 * @author School System
 */
@Service
@RequiredArgsConstructor
public class PdfServiceImpl implements PdfService {

    private static final int EXAM_STATUS_PUBLISHED = 3;

    private final ExamMapper examMapper;
    private final ScoreMapper scoreMapper;
    private final StudentMapper studentMapper;
    private final TeacherScopeService teacherScopeService;
    private final ScoreReportAssembler assembler;
    private final ScoreReportRenderer renderer;

    @Override
    public void generateScoreReport(Long examId, Long studentId, HttpServletResponse response) {
        Exam exam = publishedExam(examId);
        // ponytail: 学生 IDOR 守卫先于任何查询，与 StatService.assertStudentSelfOnly 一致
        if (SecurityUtils.isStudent() && !SecurityUtils.currentStudentId().equals(studentId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只能下载本人的成绩单");
        }

        List<Score> studentRows = scoreMapper.selectByExamId(SecurityUtils.currentSchoolIdForQuery(), examId, null)
                .stream()
                .filter(s -> studentId.equals(s.getStudentId()))
                .collect(Collectors.toList());
        if (studentRows.isEmpty()) {
            throw new BusinessException("未找到学生成绩");
        }
        // ponytail: 班级取成绩行上的 class_id（考试当时所在班），不取 class_student 现状——学生转班后历史成绩单仍归原班
        Long classId = studentRows.stream().map(Score::getClassId).filter(Objects::nonNull).findFirst().orElse(null);
        assertTeacherClassAllowed(classId, "无权下载该班级学生的成绩单");

        Student student = studentMapper.selectById(studentId);
        List<Score> classScores = classId == null ? studentRows
                : scoreMapper.selectByExamId(SecurityUtils.currentSchoolIdForQuery(), examId, classId);
        ReportContext context = assembler.buildContext(exam, classId, classScores);
        ScoreReportData report = assembler.buildStudentReport(context, student, studentRows, LocalDateTime.now());

        String label = student == null || student.getStudentNo() == null ? String.valueOf(studentId) : student.getStudentNo();
        writePdf(response, List.of(report), "成绩单_" + label);
    }

    @Override
    public void generateClassScoreReports(Long examId, Long classId, HttpServletResponse response) {
        Exam exam = publishedExam(examId);
        if (SecurityUtils.isStudent()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "学生账号无权批量下载成绩单");
        }
        if (classId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "班级ID不能为空");
        }
        assertTeacherClassAllowed(classId, "无权下载该班级的成绩单");

        List<Score> classScores = scoreMapper.selectByExamId(SecurityUtils.currentSchoolIdForQuery(), examId, classId);
        if (classScores.isEmpty()) {
            throw new BusinessException("未找到班级成绩");
        }
        ReportContext context = assembler.buildContext(exam, classId, classScores);

        Map<Long, List<Score>> rowsByStudent = classScores.stream()
                .filter(s -> s.getStudentId() != null)
                .collect(Collectors.groupingBy(Score::getStudentId));
        Set<Long> studentIds = rowsByStudent.keySet();
        Map<Long, Student> students = studentIds.isEmpty() ? Map.of()
                : studentMapper.selectBatchIds(studentIds).stream()
                        .collect(Collectors.toMap(Student::getId, s -> s, (a, b) -> a));

        // ponytail: 按学号升序出页，没有档案的学生按 ID 排在最后，便于老师核对纸质顺序
        List<Long> ordered = new ArrayList<>(studentIds);
        Comparator<Long> byStudentNo = Comparator.comparing(
                (Long id) -> students.get(id) == null ? null : students.get(id).getStudentNo(),
                Comparator.nullsLast(Comparator.<String>naturalOrder()));
        ordered.sort(byStudentNo.thenComparing(Comparator.<Long>naturalOrder()));

        LocalDateTime now = LocalDateTime.now();
        List<ScoreReportData> reports = ordered.stream()
                .map(id -> assembler.buildStudentReport(context, students.get(id), rowsByStudent.get(id), now))
                .collect(Collectors.toList());

        String label = context.className().isEmpty() ? String.valueOf(classId) : context.className();
        writePdf(response, reports, "成绩单_" + label);
    }

    // ---------------------------------------------------------------- 校验

    private Exam publishedExam(Long examId) {
        Exam exam = examMapper.selectById(examId);
        if (exam == null) {
            throw new BusinessException(ResultCode.EXAM_NOT_FOUND, "考试不存在");
        }
        // ponytail: 成绩单是对外凭证，任何角色（含管理员）都只能导出已发布考试，避免审核中的分数流出
        if (!Integer.valueOf(EXAM_STATUS_PUBLISHED).equals(exam.getStatus())) {
            throw new BusinessException(ResultCode.FORBIDDEN, "成绩未发布");
        }
        return exam;
    }

    private void assertTeacherClassAllowed(Long classId, String message) {
        if (!SecurityUtils.isTeacher()) {
            return;
        }
        if (classId == null || !teacherScopeService.currentAllowedClassIds().contains(classId)) {
            throw new BusinessException(ResultCode.FORBIDDEN, message);
        }
    }

    // ---------------------------------------------------------------- 输出

    /**
     * 先在内存里渲染完再写响应：渲染中途失败时响应尚未提交，GlobalExceptionHandler 仍能返回 JSON 错误
     */
    private void writePdf(HttpServletResponse response, List<ScoreReportData> reports, String baseName) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        renderer.render(reports, buffer);
        byte[] bytes = buffer.toByteArray();
        try {
            // ponytail: PDF 是二进制流，不能设 characterEncoding，否则 Content-Type 变成
            // application/pdf;charset=utf-8，部分浏览器内置预览与下载器会按文本处理
            response.setContentType("application/pdf");
            String fileName = URLEncoder.encode(baseName, StandardCharsets.UTF_8).replace("+", "%20");
            response.setHeader("Content-Disposition", "attachment; filename*=utf-8''" + fileName + ".pdf");
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (IOException e) {
            throw new BusinessException("生成PDF失败: " + e.getMessage());
        }
    }

}
