package com.school.module.teacher.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.module.student.roster.RosterImportResult;
import com.school.module.subject.entity.Subject;
import com.school.module.subject.mapper.SubjectMapper;
import com.school.module.teacher.dto.TeacherExportExcel;
import com.school.module.teacher.dto.TeacherImportExcel;
import com.school.module.teacher.dto.TeacherQueryRequest;
import com.school.module.teacher.entity.Teacher;
import com.school.module.teacher.mapper.TeacherMapper;
import com.school.module.user.dto.UserCreateRequest;
import com.school.module.user.dto.UserResponse;
import com.school.module.user.entity.SysUser;
import com.school.module.user.mapper.SysUserMapper;
import com.school.module.user.service.UserService;
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 教师名册导入导出单元测试
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("教师名册导入导出测试")
class TeacherRosterServiceTest {

    @Mock
    private TeacherMapper teacherMapper;

    @Mock
    private SubjectMapper subjectMapper;

    @Mock
    private SchoolMapper schoolMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private UserService userService;

    @InjectMocks
    private TeacherRosterServiceImpl service;

    @BeforeEach
    void setUp() {
        TestAuth.asSchoolAdmin(1L);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    // ------------------------------------------------------------------ 导入：校验失败

    @Test
    @DisplayName("应该整批拒绝_当工号在文件内重复")
    void shouldRejectImport_WhenTeacherNoDuplicatedInFile_ThenNothingPersisted() {
        when(teacherMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());

        RosterImportResult result = service.importTeachers(
                excel(row("T001", "李老师", "女", null, "否"),
                        row("T001", "王老师", "男", null, "否")), null);

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getFailures().get(0).getRow()).isEqualTo(3);
        assertThat(result.getFailures().get(0).getTeacherNo()).isEqualTo("T001");
        assertThat(result.getFailures().get(0).getReason()).contains("重复");
        verify(teacherMapper, never()).insert(any(Teacher.class));
        verify(userService, never()).createUser(any());
    }

    @Test
    @DisplayName("应该整批拒绝_当工号已存在于本校")
    void shouldRejectImport_WhenTeacherNoAlreadyExists_ThenReasonSaysExists() {
        Teacher existing = new Teacher();
        existing.setId(9L);
        existing.setTeacherNo("T001");
        existing.setSchoolId(1L);
        when(teacherMapper.selectList(any())).thenReturn(List.of(existing));
        when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());

        RosterImportResult result = service.importTeachers(
                excel(row("T001", "李老师", "女", null, "否")), null);

        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getFailures().get(0).getReason()).isEqualTo("工号已存在");
        verify(teacherMapper, never()).insert(any(Teacher.class));
    }

    @Test
    @DisplayName("应该整批拒绝_当主教科目在本校不存在")
    void shouldRejectImport_WhenSubjectNotFound_ThenReasonNamesSubject() {
        when(teacherMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(subjectMapper.selectList(any())).thenReturn(Collections.emptyList());

        RosterImportResult result = service.importTeachers(
                excel(row("T001", "李老师", "女", "拉丁语", "否"),
                        row("T002", "王老师", "男", null, "否")), null);

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getFailures().get(0).getRow()).isEqualTo(2);
        assertThat(result.getFailures().get(0).getReason()).contains("拉丁语").contains("不存在");
        verify(teacherMapper, never()).insert(any(Teacher.class));
    }

    @Test
    @DisplayName("应该整批拒绝_当要开户的工号已被用户名占用")
    void shouldRejectImport_WhenUsernameTaken_ThenReasonSaysTaken() {
        SysUser taken = new SysUser();
        taken.setUsername("T001");
        when(teacherMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserMapper.selectList(any())).thenReturn(List.of(taken));

        RosterImportResult result = service.importTeachers(
                excel(row("T001", "李老师", "女", null, "是")), null);

        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getFailures().get(0).getReason()).contains("已被占用");
        verify(userService, never()).createUser(any());
    }

    @Test
    @DisplayName("应该抛出异常_当超管未指定学校")
    void shouldThrow_WhenSuperAdminWithoutSchoolId_ThenParamError() {
        TestAuth.asSuperAdmin();

        assertThatThrownBy(() -> service.importTeachers(
                excel(row("T001", "李老师", "女", null, "否")), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学校");
    }

    // ------------------------------------------------------------------ 导入：成功

    @Test
    @DisplayName("应该创建教师账号并回写user_id_当「是否开通账号」为是")
    void shouldCreateAccount_WhenOpenAccountYes_ThenUserBoundToTeacher() {
        Subject math = subject(5L, "数学");
        when(teacherMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(subjectMapper.selectList(any())).thenReturn(List.of(math));
        when(userService.createUser(any(UserCreateRequest.class)))
                .thenReturn(UserResponse.builder().id(600L).username("T001").build());

        RosterImportResult result = service.importTeachers(
                excel(row("T001", "李老师", "女", "数学", "是")), null);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getCreatedAccounts()).hasSize(1);
        assertThat(result.getCreatedAccounts().get(0).getUsername()).isEqualTo("T001");
        assertThat(result.getCreatedAccounts().get(0).getInitialPassword()).isEqualTo("123456");

        ArgumentCaptor<UserCreateRequest> userCaptor = ArgumentCaptor.forClass(UserCreateRequest.class);
        verify(userService).createUser(userCaptor.capture());
        assertThat(userCaptor.getValue().getUsername()).isEqualTo("T001");
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("123456");
        assertThat(userCaptor.getValue().getRole()).isEqualTo("teacher");
        assertThat(userCaptor.getValue().getSchoolId()).isEqualTo(1L);
        assertThat(userCaptor.getValue().getStatus()).isEqualTo(1);

        ArgumentCaptor<Teacher> teacherCaptor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherMapper).insert(teacherCaptor.capture());
        Teacher saved = teacherCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(600L);
        assertThat(saved.getSchoolId()).isEqualTo(1L);
        assertThat(saved.getTeacherNo()).isEqualTo("T001");
        assertThat(saved.getGender()).isEqualTo(2);
        assertThat(saved.getSubjectId()).isEqualTo(5L);
        assertThat(saved.getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该去掉数字单元格的.0尾巴_当工号按数字填写")
    void shouldStripDecimalSuffix_WhenTeacherNoIsNumericCell_ThenPlainCodeSaved() {
        when(teacherMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());

        RosterImportResult result = service.importTeachers(
                excel(row("1001.0", "李老师", "2.0", null, "否")), null);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherMapper).insert(captor.capture());
        assertThat(captor.getValue().getTeacherNo()).isEqualTo("1001");
        assertThat(captor.getValue().getGender()).isEqualTo(2);
    }

    @Test
    @DisplayName("应该选id最大的科目并提示_当科目名称在本校重名")
    void shouldPickLatestSubject_WhenSubjectNameDuplicated_ThenWarn() {
        when(teacherMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(subjectMapper.selectList(any())).thenReturn(Arrays.asList(subject(5L, "数学"), subject(8L, "数学")));

        RosterImportResult result = service.importTeachers(
                excel(row("T001", "李老师", null, "数学", "否")), null);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getWarnings()).hasSize(1);
        assertThat(result.getWarnings().get(0)).contains("数学").contains("8");
        ArgumentCaptor<Teacher> captor = ArgumentCaptor.forClass(Teacher.class);
        verify(teacherMapper).insert(captor.capture());
        assertThat(captor.getValue().getSubjectId()).isEqualTo(8L);
        assertThat(captor.getValue().getUserId()).isNull();
    }

    // ------------------------------------------------------------------ 导出

    @Test
    @DisplayName("应该导出含科目与账号用户名的xlsx_当校管理员按筛选导出")
    void shouldExportXlsx_WhenSchoolAdmin_ThenScopedToOwnSchool() throws Exception {
        Teacher t = new Teacher();
        t.setId(1L);
        t.setTeacherNo("T001");
        t.setName("李老师");
        t.setGender(2);
        t.setSchoolId(1L);
        t.setSubjectId(5L);
        t.setUserId(600L);
        t.setStatus(1);
        Page<Teacher> page = new Page<>();
        page.setRecords(List.of(t));
        when(teacherMapper.selectPageByQuery(any(), any())).thenReturn(page);

        School school = new School();
        school.setId(1L);
        school.setSchoolName("实验小学");
        when(schoolMapper.selectBatchIds(any())).thenReturn(List.of(school));
        when(subjectMapper.selectBatchIds(any())).thenReturn(List.of(subject(5L, "数学")));
        SysUser user = new SysUser();
        user.setId(600L);
        user.setUsername("T001");
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(user));

        TeacherQueryRequest request = new TeacherQueryRequest();
        request.setSchoolId(99L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.exportTeachers(request, response);

        assertThat(request.getSchoolId()).isEqualTo(1L);
        assertThat(response.getContentType()).contains("spreadsheetml");
        List<TeacherExportExcel> rows = EasyExcel.read(new ByteArrayInputStream(response.getContentAsByteArray()))
                .head(TeacherExportExcel.class).sheet().doReadSync();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getTeacherNo()).isEqualTo("T001");
        assertThat(rows.get(0).getSubjectName()).isEqualTo("数学");
        assertThat(rows.get(0).getUsername()).isEqualTo("T001");
        assertThat(rows.get(0).getSchoolName()).isEqualTo("实验小学");
        assertThat(rows.get(0).getGender()).isEqualTo("女");
        assertThat(rows.get(0).getStatus()).isEqualTo("在职");
    }

    @Test
    @DisplayName("应该抛出异常_当筛选条件下没有教师")
    void shouldThrow_WhenExportHasNoRows_ThenBusinessException() {
        when(teacherMapper.selectPageByQuery(any(), any())).thenReturn(new Page<>());

        assertThatThrownBy(() -> service.exportTeachers(new TeacherQueryRequest(), new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有教师数据");
    }

    // ------------------------------------------------------------------ helpers

    private static TeacherImportExcel row(String no, String name, String gender, String subjectName, String open) {
        TeacherImportExcel r = new TeacherImportExcel();
        r.setTeacherNo(no);
        r.setName(name);
        r.setGender(gender);
        r.setSubjectName(subjectName);
        r.setOpenAccount(open);
        return r;
    }

    private static MockMultipartFile excel(TeacherImportExcel... rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, TeacherImportExcel.class).sheet("教师导入").doWrite(Arrays.asList(rows));
        return new MockMultipartFile("file", "teachers.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

    private static Subject subject(Long id, String name) {
        Subject s = new Subject();
        s.setId(id);
        s.setSchoolId(1L);
        s.setSubjectName(name);
        return s;
    }

}
