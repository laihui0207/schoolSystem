package com.school.module.pdf.service;

import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfReader;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.class_module.entity.SchoolClass;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.mapper.ExamMapper;
import com.school.module.pdf.config.PdfProperties;
import com.school.module.pdf.dto.ReportContext;
import com.school.module.pdf.dto.ScoreReportData;
import com.school.module.pdf.font.PdfFontProvider;
import com.school.module.pdf.render.ScoreReportRenderer;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.user.enums.UserRole;
import com.school.security.model.LoginUser;
import com.school.security.service.TeacherScopeService;
import com.school.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PDF 成绩单：发布状态守卫、角色范围、真实渲染输出
 * <p>
 * 渲染器与字体提供者用真实对象（字体路径为空 -> 回退 font-asian 内置字体），数据装配用 Mock。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PDF成绩单服务测试")
class PdfServiceTest {

    @Mock
    private ExamMapper examMapper;

    @Mock
    private ScoreMapper scoreMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private TeacherScopeService teacherScopeService;

    @Mock
    private ScoreReportAssembler assembler;

    private PdfServiceImpl pdfService;

    private Exam publishedExam;

    @BeforeEach
    void setUp() {
        PdfProperties properties = new PdfProperties();
        properties.setFontPaths(List.of());
        ScoreReportRenderer renderer = new ScoreReportRenderer(new PdfFontProvider(properties));
        pdfService = new PdfServiceImpl(examMapper, scoreMapper, studentMapper, teacherScopeService, assembler, renderer);

        publishedExam = new Exam();
        publishedExam.setId(1L);
        publishedExam.setSchoolId(5L);
        publishedExam.setExamName("期中考试");
        publishedExam.setStatus(3);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    // ---------------------------------------------------------------- 发布状态

    @Test
    @DisplayName("应该拒绝生成成绩单_当考试未发布")
    void shouldRejectScoreReport_WhenExamNotPublished() {
        // Given: 管理员也不行——成绩单只认已发布
        TestAuth.asSchoolAdmin(5L);
        publishedExam.setStatus(2);
        when(examMapper.selectById(1L)).thenReturn(publishedExam);

        // When & Then
        assertThatThrownBy(() -> pdfService.generateScoreReport(1L, 11L, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("成绩未发布");
        verify(scoreMapper, never()).selectByExamId(any(), any(), any());
    }

    @Test
    @DisplayName("应该拒绝生成班级成绩单_当考试未发布")
    void shouldRejectClassReports_WhenExamNotPublished() {
        // Given
        TestAuth.asSchoolAdmin(5L);
        publishedExam.setStatus(1);
        when(examMapper.selectById(1L)).thenReturn(publishedExam);

        // When & Then
        assertThatThrownBy(() -> pdfService.generateClassScoreReports(1L, 2L, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("成绩未发布");
        verify(scoreMapper, never()).selectByExamId(any(), any(), any());
    }

    @Test
    @DisplayName("应该抛出考试不存在_当考试ID无效")
    void shouldThrowExamNotFound_WhenExamMissing() {
        // Given
        TestAuth.asSchoolAdmin(5L);
        when(examMapper.selectById(99L)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> pdfService.generateScoreReport(99L, 11L, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.EXAM_NOT_FOUND.getCode());
    }

    // ---------------------------------------------------------------- 角色范围

    @Test
    @DisplayName("应该拒绝_当学生下载他人成绩单")
    void shouldForbid_WhenStudentRequestsOthersReport() {
        // Given: 学生档案 4 请求学生 5
        loginAsStudent(4L);
        when(examMapper.selectById(1L)).thenReturn(publishedExam);

        // When & Then
        assertThatThrownBy(() -> pdfService.generateScoreReport(1L, 5L, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能下载本人")
                .extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(scoreMapper, never()).selectByExamId(any(), any(), any());
    }

    @Test
    @DisplayName("应该拒绝_当学生批量下载班级成绩单")
    void shouldForbid_WhenStudentRequestsClassReports() {
        // Given
        loginAsStudent(4L);
        when(examMapper.selectById(1L)).thenReturn(publishedExam);

        // When & Then
        assertThatThrownBy(() -> pdfService.generateClassScoreReports(1L, 2L, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());
    }

    @Test
    @DisplayName("应该拒绝_当教师下载非任教班级学生的成绩单")
    void shouldForbid_WhenTeacherOutOfScope() {
        // Given: 学生 11 的成绩在班级 2，教师只能访问班级 9
        loginAsTeacher(7L);
        when(examMapper.selectById(1L)).thenReturn(publishedExam);
        when(scoreMapper.selectByExamId(5L, 1L, null)).thenReturn(List.of(score(11L, 2L, 3L, "88")));
        when(teacherScopeService.currentAllowedClassIds()).thenReturn(Set.of(9L));

        // When & Then
        assertThatThrownBy(() -> pdfService.generateScoreReport(1L, 11L, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权下载")
                .extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(assembler, never()).buildContext(any(), any(), anyList());
    }

    @Test
    @DisplayName("应该抛出未找到成绩_当学生本场无成绩记录")
    void shouldThrow_WhenStudentHasNoScores() {
        // Given
        TestAuth.asSchoolAdmin(5L);
        when(examMapper.selectById(1L)).thenReturn(publishedExam);
        when(scoreMapper.selectByExamId(5L, 1L, null)).thenReturn(List.of(score(12L, 2L, 3L, "70")));

        // When & Then
        assertThatThrownBy(() -> pdfService.generateScoreReport(1L, 11L, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到学生成绩");
    }

    // ---------------------------------------------------------------- 真实输出

    @Test
    @DisplayName("应该输出PDF并设置下载头_当管理员下载已发布成绩单")
    void shouldWritePdf_WhenAdminRequestsPublishedReport() throws IOException {
        // Given
        TestAuth.asSchoolAdmin(5L);
        when(examMapper.selectById(1L)).thenReturn(publishedExam);
        List<Score> mine = List.of(score(11L, 2L, 3L, "88"), score(11L, 2L, 4L, "92"));
        when(scoreMapper.selectByExamId(5L, 1L, null)).thenReturn(mine);
        when(scoreMapper.selectByExamId(5L, 1L, 2L)).thenReturn(mine);
        when(studentMapper.selectById(11L)).thenReturn(student(11L, "20240001", "张三"));
        ReportContext context = context(mine);
        when(assembler.buildContext(eq(publishedExam), eq(2L), anyList())).thenReturn(context);
        when(assembler.buildStudentReport(eq(context), any(), anyList(), any())).thenReturn(report("20240001"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        pdfService.generateScoreReport(1L, 11L, response);

        // Then
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getHeader("Content-Disposition"))
                .contains("attachment")
                .contains("filename*=utf-8''")
                .contains("20240001.pdf");
        byte[] bytes = response.getContentAsByteArray();
        assertThat(new String(bytes, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(pageCount(bytes)).isEqualTo(1);
    }

    @Test
    @DisplayName("应该每人一页合成一个PDF_当下载全班成绩单")
    void shouldWriteOnePagePerStudent_WhenClassReports() throws IOException {
        // Given: 班级 2 有两名学生
        TestAuth.asSchoolAdmin(5L);
        when(examMapper.selectById(1L)).thenReturn(publishedExam);
        List<Score> classScores = List.of(
                score(11L, 2L, 3L, "88"), score(11L, 2L, 4L, "92"),
                score(12L, 2L, 3L, "75"), score(12L, 2L, 4L, "60"));
        when(scoreMapper.selectByExamId(5L, 1L, 2L)).thenReturn(classScores);
        when(studentMapper.selectBatchIds(any()))
                .thenReturn(List.of(student(11L, "20240001", "张三"), student(12L, "20240002", "李四")));
        ReportContext context = context(classScores);
        when(assembler.buildContext(eq(publishedExam), eq(2L), anyList())).thenReturn(context);
        when(assembler.buildStudentReport(eq(context), any(), anyList(), any()))
                .thenReturn(report("20240001"), report("20240002"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        pdfService.generateClassScoreReports(1L, 2L, response);

        // Then
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getHeader("Content-Disposition")).contains("filename*=utf-8''");
        assertThat(pageCount(response.getContentAsByteArray())).isEqualTo(2);
        verify(assembler, times(2)).buildStudentReport(eq(context), any(), anyList(), any());
        verify(teacherScopeService, never()).currentAllowedClassIds();
    }

    @Test
    @DisplayName("应该允许教师下载任教班级的成绩单_当班级在范围内")
    void shouldAllowTeacher_WhenClassInScope() throws IOException {
        // Given
        loginAsTeacher(7L);
        when(examMapper.selectById(1L)).thenReturn(publishedExam);
        List<Score> mine = List.of(score(11L, 2L, 3L, "88"));
        when(scoreMapper.selectByExamId(5L, 1L, null)).thenReturn(mine);
        when(scoreMapper.selectByExamId(5L, 1L, 2L)).thenReturn(mine);
        when(teacherScopeService.currentAllowedClassIds()).thenReturn(Set.of(2L));
        when(studentMapper.selectById(11L)).thenReturn(student(11L, "20240001", "张三"));
        ReportContext context = context(mine);
        when(assembler.buildContext(eq(publishedExam), eq(2L), anyList())).thenReturn(context);
        when(assembler.buildStudentReport(eq(context), any(), anyList(), any())).thenReturn(report("20240001"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        pdfService.generateScoreReport(1L, 11L, response);

        // Then
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(pageCount(response.getContentAsByteArray())).isEqualTo(1);
    }

    // ---------------------------------------------------------------- 测试数据

    private static int pageCount(byte[] pdfBytes) throws IOException {
        try (PdfDocument pdf = new PdfDocument(new PdfReader(new ByteArrayInputStream(pdfBytes)))) {
            return pdf.getNumberOfPages();
        }
    }

    private static Score score(Long studentId, Long classId, Long subjectId, String value) {
        Score score = new Score();
        score.setId(studentId * 100 + subjectId);
        score.setExamId(1L);
        score.setStudentId(studentId);
        score.setClassId(classId);
        score.setSubjectId(subjectId);
        score.setSchoolId(5L);
        score.setScore(new BigDecimal(value));
        score.setIsAbsent(0);
        score.setStatus(1);
        return score;
    }

    private static Student student(Long id, String studentNo, String name) {
        Student student = new Student();
        student.setId(id);
        student.setSchoolId(5L);
        student.setStudentNo(studentNo);
        student.setName(name);
        return student;
    }

    private ReportContext context(List<Score> classScores) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(2L);
        schoolClass.setClassName("高一(1)班");
        return new ReportContext(publishedExam, "示范中学", "2024-2025学年 第1学期", schoolClass, "高一",
                List.of(new ReportContext.SubjectMeta(3L, "语文", BigDecimal.valueOf(150)),
                        new ReportContext.SubjectMeta(4L, "数学", BigDecimal.valueOf(150))),
                classScores, ReportContext.GroupAverages.empty(), ReportContext.GroupAverages.empty());
    }

    private static ScoreReportData report(String studentNo) {
        return ScoreReportData.builder()
                .schoolName("示范中学")
                .examName("期中考试")
                .termName("2024-2025学年 第1学期")
                .examDate("2024-11-01")
                .studentName("学生" + studentNo)
                .studentNo(studentNo)
                .className("高一(1)班")
                .gradeName("高一")
                .subjectRows(List.of(
                        ScoreReportData.SubjectRow.builder().subjectId(3L).subjectName("语文")
                                .fullScore(BigDecimal.valueOf(150)).score(new BigDecimal("88")).rankInClass(3).build(),
                        ScoreReportData.SubjectRow.builder().subjectId(4L).subjectName("数学")
                                .fullScore(BigDecimal.valueOf(150)).absent(true).build()))
                .totalScore(new BigDecimal("88"))
                .totalFullScore(BigDecimal.valueOf(300))
                .classAverage(new BigDecimal("80.5"))
                .generatedAt(LocalDateTime.of(2024, 11, 2, 9, 0))
                .build();
    }

    private static void loginAsStudent(Long studentId) {
        TestAuth.authenticate(LoginUser.builder()
                .userId(500L)
                .username("student")
                .schoolId(5L)
                .userType(UserRole.STUDENT.getUserType())
                .studentId(studentId)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_STUDENT")))
                .build());
    }

    private static void loginAsTeacher(Long teacherId) {
        TestAuth.authenticate(LoginUser.builder()
                .userId(300L)
                .username("teacher")
                .schoolId(5L)
                .userType(UserRole.TEACHER.getUserType())
                .teacherId(teacherId)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_TEACHER")))
                .build());
    }

}
