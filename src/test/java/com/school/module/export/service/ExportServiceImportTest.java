package com.school.module.export.service;

import com.alibaba.excel.EasyExcel;
import com.school.common.exception.BusinessException;
import com.school.module.class_module.mapper.ClassMapper;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.entity.ExamSubject;
import com.school.module.exam.mapper.ExamMapper;
import com.school.module.exam.mapper.ExamSubjectMapper;
import com.school.module.export.dto.ScoreImportExcel;
import com.school.module.score.dto.BatchInputResult;
import com.school.module.score.dto.ScoreBatchInputRequest;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.module.score.service.ScoreService;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.ClassStudentMapper;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.subject.mapper.SubjectMapper;
import com.school.module.user.enums.UserRole;
import com.school.security.model.LoginUser;
import com.school.security.service.TeacherScopeService;
import com.school.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 成绩 Excel 导入：模板契约往返 + 行校验
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("成绩Excel导入测试")
class ExportServiceImportTest {

    @Mock
    private ScoreMapper scoreMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private SubjectMapper subjectMapper;

    @Mock
    private ScoreService scoreService;

    @Mock
    private ExamMapper examMapper;

    @Mock
    private ClassMapper classMapper;

    @Mock
    private ExamSubjectMapper examSubjectMapper;

    @Mock
    private ClassStudentMapper classStudentMapper;

    @Mock
    private TeacherScopeService teacherScopeService;

    @InjectMocks
    private ExportServiceImpl exportService;

    private Exam recordingExam;

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @BeforeEach
    void setUp() {
        recordingExam = new Exam();
        recordingExam.setId(1L);
        recordingExam.setSchoolId(5L);
        recordingExam.setExamName("期中考试");
        recordingExam.setStatus(1);
    }

    @Test
    @DisplayName("应该按三元组组装批量录入请求_当Excel行合法")
    void shouldBuildBatchRequest_WhenRowsValid() {
        // Given: 一行有分数（带备注），一行缺考
        ScoreImportExcel scored = row("20240001", "张三", BigDecimal.valueOf(95.5), "否", "期中");
        ScoreImportExcel absent = row("20240002", "李四", null, "是", null);
        stubImportContext();
        stubStudents(student(11L, 5L, "20240001"), student(12L, 5L, "20240002"));
        when(scoreService.batchInputScores(any(ScoreBatchInputRequest.class)))
                .thenReturn(BatchInputResult.builder().successCount(2).failCount(0).failures(List.of()).build());

        // When
        BatchInputResult result = exportService.importScores(1L, 2L, 3L, excel(scored, absent));

        // Then
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailCount()).isZero();
        ArgumentCaptor<ScoreBatchInputRequest> captor = ArgumentCaptor.forClass(ScoreBatchInputRequest.class);
        verify(scoreService).batchInputScores(captor.capture());
        ScoreBatchInputRequest request = captor.getValue();
        assertThat(request.getExamId()).isEqualTo(1L);
        assertThat(request.getClassId()).isEqualTo(2L);
        assertThat(request.getSubjectId()).isEqualTo(3L);
        assertThat(request.getScores()).hasSize(2);

        ScoreBatchInputRequest.ScoreItem first = request.getScores().get(0);
        assertThat(first.getStudentId()).isEqualTo(11L);
        assertThat(first.getClassId()).isEqualTo(2L);
        assertThat(first.getSubjectId()).isEqualTo(3L);
        assertThat(first.getScore()).isEqualByComparingTo(BigDecimal.valueOf(95.5));
        assertThat(first.getIsAbsent()).isEqualTo(0);
        assertThat(first.getRemark()).isEqualTo("期中");

        ScoreBatchInputRequest.ScoreItem second = request.getScores().get(1);
        assertThat(second.getStudentId()).isEqualTo(12L);
        assertThat(second.getScore()).isNull();
        assertThat(second.getIsAbsent()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该整体拒绝并报出行号_当学号不存在")
    void shouldRejectAll_WhenStudentNoUnknown() {
        // Given
        ScoreImportExcel unknown = row("99999", "无名", BigDecimal.TEN, "否", null);
        stubImportContext();
        stubStudents();

        // When & Then
        assertThatThrownBy(() -> exportService.importScores(1L, 2L, 3L, excel(unknown)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第2行")
                .hasMessageContaining("99999 不存在");
        verify(scoreService, never()).batchInputScores(any());
    }

    @Test
    @DisplayName("应该整体拒绝并报出行号_当文件内学号重复")
    void shouldRejectAll_WhenStudentNoDuplicated() {
        // Given: 同一学号出现两次，第二次在第 3 行
        ScoreImportExcel first = row("20240001", "张三", BigDecimal.valueOf(80), "否", null);
        ScoreImportExcel dup = row("20240001", "张三", BigDecimal.valueOf(90), "否", null);
        stubImportContext();
        stubStudents(student(11L, 5L, "20240001"));

        // When & Then
        assertThatThrownBy(() -> exportService.importScores(1L, 2L, 3L, excel(first, dup)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第3行")
                .hasMessageContaining("重复");
        verify(scoreService, never()).batchInputScores(any());
    }

    @Test
    @DisplayName("应该整体拒绝并报出行号_当分数超出满分")
    void shouldRejectAll_WhenScoreOutOfRange() {
        // Given: 满分 100，录 120
        ScoreImportExcel over = row("20240001", "张三", BigDecimal.valueOf(120), "否", null);
        stubImportContext();
        stubStudents(student(11L, 5L, "20240001"));

        // When & Then
        assertThatThrownBy(() -> exportService.importScores(1L, 2L, 3L, excel(over)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第2行")
                .hasMessageContaining("分数超出范围");
        verify(scoreService, never()).batchInputScores(any());
    }

    @Test
    @DisplayName("应该整体拒绝并报出行号_当学生不在该班级")
    void shouldRejectAll_WhenStudentNotInClass() {
        // Given: 学号存在于本校，但不在班级 2 的名单里
        ScoreImportExcel outsider = row("20240003", "王五", BigDecimal.valueOf(70), "否", null);
        stubImportContext();
        stubStudents(student(13L, 5L, "20240003"));

        // When & Then
        assertThatThrownBy(() -> exportService.importScores(1L, 2L, 3L, excel(outsider)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第2行")
                .hasMessageContaining("不在该班级");
        verify(scoreService, never()).batchInputScores(any());
    }

    @Test
    @DisplayName("应该整体拒绝_当考试不在录入中状态")
    void shouldRejectAll_WhenExamNotRecording() {
        // Given: 考试已发布
        recordingExam.setStatus(3);
        when(examMapper.selectById(1L)).thenReturn(recordingExam);
        ScoreImportExcel valid = row("20240001", "张三", BigDecimal.valueOf(80), "否", null);

        // When & Then
        assertThatThrownBy(() -> exportService.importScores(1L, 2L, 3L, excel(valid)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("考试不在录入中状态");
        verify(scoreService, never()).batchInputScores(any());
    }

    @Test
    @DisplayName("应该整体拒绝并报出行号_当备注超过500字")
    void shouldRejectAll_WhenRemarkTooLong() {
        // Given: score.remark 是 VARCHAR(500)
        ScoreImportExcel longRemark = row("20240001", "张三", BigDecimal.valueOf(80), "否", "备".repeat(501));
        stubImportContext();
        stubStudents(student(11L, 5L, "20240001"));

        // When & Then
        assertThatThrownBy(() -> exportService.importScores(1L, 2L, 3L, excel(longRemark)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第2行")
                .hasMessageContaining("备注不能超过");
        verify(scoreService, never()).batchInputScores(any());
    }

    @Test
    @DisplayName("应该拒绝导出_当学生账号")
    void shouldForbidExport_WhenStudent() {
        // Given
        loginAsStudent(4L);
        recordingExam.setStatus(3);
        when(examMapper.selectById(1L)).thenReturn(recordingExam);

        // When & Then
        assertThatThrownBy(() -> exportService.exportScores(1L, 2L, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权导出");
        verify(scoreMapper, never()).selectByExamId(any(), any(), any());
    }

    @Test
    @DisplayName("应该拒绝导出_当教师未指定班级")
    void shouldForbidExport_WhenTeacherWithoutClass() {
        // Given
        loginAsTeacher(7L);
        recordingExam.setStatus(3);
        when(examMapper.selectById(1L)).thenReturn(recordingExam);

        // When & Then
        assertThatThrownBy(() -> exportService.exportScores(1L, null, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须指定班级");
        verify(scoreMapper, never()).selectByExamId(any(), any(), any());
    }

    @Test
    @DisplayName("应该拒绝导出_当教师导出非任教班级")
    void shouldForbidExport_WhenTeacherOutOfScope() {
        // Given: 教师只能访问班级 9
        loginAsTeacher(7L);
        recordingExam.setStatus(3);
        when(examMapper.selectById(1L)).thenReturn(recordingExam);
        when(teacherScopeService.currentAllowedClassIds()).thenReturn(Set.of(9L));

        // When & Then
        assertThatThrownBy(() -> exportService.exportScores(1L, 2L, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权导出该班级");
        verify(scoreMapper, never()).selectByExamId(any(), any(), any());
    }

    @Test
    @DisplayName("应该拒绝导出_当教师导出未发布考试")
    void shouldForbidExport_WhenTeacherExportsUnpublishedExam() {
        // Given: 考试仍在录入中
        loginAsTeacher(7L);
        when(examMapper.selectById(1L)).thenReturn(recordingExam);

        // When & Then
        assertThatThrownBy(() -> exportService.exportScores(1L, 2L, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未发布");
        verify(scoreMapper, never()).selectByExamId(any(), any(), any());
    }

    @Test
    @DisplayName("应该导出成绩_当校管理员导出未发布考试")
    void shouldExport_WhenSchoolAdminExportsUnpublishedExam() {
        // Given: 管理员不受发布状态与班级范围限制
        TestAuth.asSchoolAdmin(5L);
        when(examMapper.selectById(1L)).thenReturn(recordingExam);
        Score score = new Score();
        score.setId(1L);
        score.setStudentId(11L);
        score.setSubjectId(3L);
        score.setClassId(2L);
        score.setScore(BigDecimal.valueOf(88));
        score.setIsAbsent(0);
        when(scoreMapper.selectByExamId(5L, 1L, 2L)).thenReturn(List.of(score));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        exportService.exportScores(1L, 2L, response);

        // Then
        assertThat(response.getContentType()).contains("spreadsheetml");
        assertThat(response.getContentAsByteArray()).isNotEmpty();
        verify(teacherScopeService, never()).currentAllowedClassIds();
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

    /**
     * 导入前置桩：考试 1 录入中、科目 3 满分 100、班级 2 名单为学生 11/12
     */
    private void stubImportContext() {
        when(examMapper.selectById(1L)).thenReturn(recordingExam);
        ExamSubject examSubject = new ExamSubject();
        examSubject.setExamId(1L);
        examSubject.setSubjectId(3L);
        examSubject.setFullScore(BigDecimal.valueOf(100));
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(List.of(examSubject));
        when(classStudentMapper.selectStudentIdsByClassId(2L)).thenReturn(Arrays.asList(11L, 12L));
    }

    /**
     * 学号按 exam.school_id 范围一次性批量查找（LambdaQueryWrapper），这里统一返回给定学生
     */
    private void stubStudents(Student... students) {
        when(studentMapper.selectList(any())).thenReturn(Arrays.asList(students));
    }

    private static ScoreImportExcel row(String studentNo, String name, BigDecimal score, String absent, String remark) {
        ScoreImportExcel r = new ScoreImportExcel();
        r.setStudentNo(studentNo);
        r.setStudentName(name);
        r.setScore(score);
        r.setIsAbsent(absent);
        r.setRemark(remark);
        return r;
    }

    private static Student student(Long id, Long schoolId, String studentNo) {
        Student s = new Student();
        s.setId(id);
        s.setSchoolId(schoolId);
        s.setStudentNo(studentNo);
        return s;
    }

    /**
     * 用同一个 DTO 写出 xlsx，保证模板与读取契约一致
     */
    private static MockMultipartFile excel(ScoreImportExcel... rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<ScoreImportExcel> data = Arrays.asList(rows);
        EasyExcel.write(out, ScoreImportExcel.class).sheet("成绩").doWrite(data);
        return new MockMultipartFile("file", "scores.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

}
