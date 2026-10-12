package com.school.module.export.service;

import com.alibaba.excel.EasyExcel;
import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.class_module.entity.SchoolClass;
import com.school.module.class_module.mapper.ClassMapper;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.mapper.ExamMapper;
import com.school.module.stat.dto.ClassStatResponse;
import com.school.module.stat.dto.GradeStatResponse;
import com.school.module.stat.service.StatService;
import com.school.module.user.enums.UserRole;
import com.school.security.model.LoginUser;
import com.school.security.service.TeacherScopeService;
import com.school.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 统计报表 Excel：权限守卫 + 三 sheet 内容往返
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("统计报表导出测试")
class StatReportExportServiceTest {

    @Mock
    private ExamMapper examMapper;

    @Mock
    private ClassMapper classMapper;

    @Mock
    private StatService statService;

    @Mock
    private TeacherScopeService teacherScopeService;

    @InjectMocks
    private StatReportExportServiceImpl service;

    private Exam exam;

    @BeforeEach
    void setUp() {
        exam = new Exam();
        exam.setId(1L);
        exam.setSchoolId(5L);
        exam.setExamName("期中考试");
        exam.setStatus(3);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @Test
    @DisplayName("应该拒绝_当学生导出统计报表")
    void shouldForbid_WhenStudent() {
        // Given
        loginAsStudent(4L);
        when(examMapper.selectById(1L)).thenReturn(exam);

        // When & Then
        assertThatThrownBy(() -> service.exportStatReport(1L, 2L, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.FORBIDDEN.getCode());
        verify(statService, never()).getClassStat(anyLong(), anyLong());
    }

    @Test
    @DisplayName("应该拒绝_当教师未指定班级")
    void shouldForbid_WhenTeacherWithoutClass() {
        // Given
        loginAsTeacher(7L);
        when(examMapper.selectById(1L)).thenReturn(exam);

        // When & Then
        assertThatThrownBy(() -> service.exportStatReport(1L, null, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须指定班级");
        verify(statService, never()).getGradeStat(anyLong(), any());
    }

    @Test
    @DisplayName("应该拒绝_当教师导出未发布考试")
    void shouldForbid_WhenTeacherExportsUnpublishedExam() {
        // Given
        loginAsTeacher(7L);
        exam.setStatus(2);
        when(examMapper.selectById(1L)).thenReturn(exam);

        // When & Then
        assertThatThrownBy(() -> service.exportStatReport(1L, 2L, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未发布");
    }

    @Test
    @DisplayName("应该拒绝_当教师导出非任教班级")
    void shouldForbid_WhenTeacherOutOfScope() {
        // Given
        loginAsTeacher(7L);
        when(examMapper.selectById(1L)).thenReturn(exam);
        when(teacherScopeService.currentAllowedClassIds()).thenReturn(Set.of(9L));

        // When & Then
        assertThatThrownBy(() -> service.exportStatReport(1L, 2L, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权导出该班级");
        verify(statService, never()).getClassStat(anyLong(), anyLong());
    }

    @Test
    @DisplayName("应该输出三个sheet_当管理员导出指定班级")
    void shouldWriteThreeSheets_WhenAdminExportsSingleClass() {
        // Given
        TestAuth.asSchoolAdmin(5L);
        when(examMapper.selectById(1L)).thenReturn(exam);
        when(classMapper.selectById(2L)).thenReturn(schoolClass(2L, "高一(1)班"));
        when(statService.getClassStat(1L, 2L)).thenReturn(classStat(2L, "80.50", 2));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        service.exportStatReport(1L, 2L, response);

        // Then
        assertThat(response.getContentType()).contains("spreadsheetml");
        assertThat(response.getHeader("Content-disposition")).contains("filename*=utf-8''");
        byte[] bytes = response.getContentAsByteArray();

        List<Map<Integer, String>> overview = readSheet(bytes, 0);
        assertThat(overview).hasSize(1);
        assertThat(overview.get(0).get(0)).isEqualTo("高一(1)班");
        // ponytail: 数值单元格读回是字符串且小数位不保留（80.50 -> "80.5"），按数值比较而不是字面量
        assertThat(new BigDecimal(overview.get(0).get(1))).isEqualByComparingTo("40");
        assertThat(new BigDecimal(overview.get(0).get(3))).isEqualByComparingTo("80.5");

        List<Map<Integer, String>> subjects = readSheet(bytes, 1);
        assertThat(subjects).hasSize(2);
        assertThat(subjects).extracting(row -> row.get(1)).containsExactly("语文", "数学");

        List<Map<Integer, String>> ranges = readSheet(bytes, 2);
        assertThat(ranges).hasSize(2);
        assertThat(ranges.get(0).get(1)).isEqualTo("0-60");
        verify(statService, never()).getGradeStat(anyLong(), any());
    }

    @Test
    @DisplayName("应该逐班列出并附全校合计_当未指定班级")
    void shouldListEveryClassWithSchoolTotal_WhenClassIdAbsent() {
        // Given: 全校总览给出两个班（按名次），再逐班取明细
        TestAuth.asSchoolAdmin(5L);
        when(examMapper.selectById(1L)).thenReturn(exam);
        GradeStatResponse overview = GradeStatResponse.builder()
                .examId(1L)
                .totalStudents(75)
                .averageScore(new BigDecimal("78.00"))
                .maxScore(new BigDecimal("99"))
                .minScore(new BigDecimal("12"))
                .passRate(new BigDecimal("70.00"))
                .excellentRate(new BigDecimal("20.00"))
                .classComparisons(List.of(
                        GradeStatResponse.ClassComparison.builder().classId(2L).className("高一(1)班")
                                .averageScore(new BigDecimal("80.50")).rank(1).build(),
                        GradeStatResponse.ClassComparison.builder().classId(3L).className("高一(2)班")
                                .averageScore(new BigDecimal("75.50")).rank(2).build()))
                .build();
        when(statService.getGradeStat(1L, null)).thenReturn(overview);
        when(statService.getClassStat(1L, 2L)).thenReturn(classStat(2L, "80.50", 2));
        when(statService.getClassStat(1L, 3L)).thenReturn(classStat(3L, "75.50", 1));
        MockHttpServletResponse response = new MockHttpServletResponse();

        // When
        service.exportStatReport(1L, null, response);

        // Then
        List<Map<Integer, String>> rows = readSheet(response.getContentAsByteArray(), 0);
        assertThat(rows).extracting(row -> row.get(0)).containsExactly("高一(1)班", "高一(2)班", "全校合计");
        assertThat(new BigDecimal(rows.get(2).get(3))).isEqualByComparingTo("78");
        verify(classMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("应该抛出暂无成绩_当全校没有任何班级有成绩")
    void shouldThrow_WhenNoClassHasScores() {
        // Given
        TestAuth.asSchoolAdmin(5L);
        when(examMapper.selectById(1L)).thenReturn(exam);
        when(statService.getGradeStat(1L, null)).thenReturn(GradeStatResponse.builder()
                .examId(1L).classComparisons(List.of()).build());

        // When & Then
        assertThatThrownBy(() -> service.exportStatReport(1L, null, new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("暂无成绩数据");
    }

    // ---------------------------------------------------------------- 测试数据

    private static List<Map<Integer, String>> readSheet(byte[] bytes, int index) {
        return EasyExcel.read(new ByteArrayInputStream(bytes)).sheet(index).doReadSync();
    }

    private static SchoolClass schoolClass(Long id, String name) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(id);
        schoolClass.setClassName(name);
        return schoolClass;
    }

    private static ClassStatResponse classStat(Long classId, String average, int subjectCount) {
        List<ClassStatResponse.SubjectStat> subjects = List.of(
                ClassStatResponse.SubjectStat.builder().subjectId(3L).subjectName("语文")
                        .averageScore(new BigDecimal("82.00")).maxScore(new BigDecimal("98")).minScore(new BigDecimal("40"))
                        .passRate(new BigDecimal("90.00")).excellentRate(new BigDecimal("30.00")).build(),
                ClassStatResponse.SubjectStat.builder().subjectId(4L).subjectName("数学")
                        .averageScore(new BigDecimal("79.00")).maxScore(new BigDecimal("100")).minScore(new BigDecimal("12"))
                        .passRate(new BigDecimal("80.00")).excellentRate(new BigDecimal("25.00")).build());
        return ClassStatResponse.builder()
                .classId(classId)
                .examId(1L)
                .totalStudents(40)
                .actualStudents(39)
                .absentStudents(1)
                .averageScore(new BigDecimal(average))
                .maxScore(new BigDecimal("99"))
                .minScore(new BigDecimal("30"))
                .passRate(new BigDecimal("85.00"))
                .excellentRate(new BigDecimal("28.00"))
                .subjectStats(subjects.subList(0, subjectCount))
                .scoreRanges(List.of(
                        ClassStatResponse.ScoreRange.builder().range("0-60").count(5).percentage(new BigDecimal("12.82")).build(),
                        ClassStatResponse.ScoreRange.builder().range("60-70").count(10).percentage(new BigDecimal("25.64")).build()))
                .topStudents(List.of())
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
