package com.school.module.student.service;

import com.alibaba.excel.EasyExcel;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.class_module.entity.SchoolClass;
import com.school.module.class_module.mapper.ClassMapper;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.module.student.dto.StudentClassRow;
import com.school.module.student.dto.StudentExportExcel;
import com.school.module.student.dto.StudentImportExcel;
import com.school.module.student.dto.StudentQueryRequest;
import com.school.module.student.entity.ClassStudent;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.ClassStudentMapper;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.student.roster.RosterImportResult;
import com.school.module.term.entity.AcademicTerm;
import com.school.module.term.mapper.AcademicTermMapper;
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
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学生名册导入导出单元测试
 * <p>
 * Excel 用 EasyExcel 真实写出再喂给服务，覆盖「按 String 读入 → 逐行解析」的完整路径。
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("学生名册导入导出测试")
class StudentRosterServiceTest {

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private ClassStudentMapper classStudentMapper;

    @Mock
    private ClassMapper classMapper;

    @Mock
    private AcademicTermMapper academicTermMapper;

    @Mock
    private SchoolMapper schoolMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private UserService userService;

    @InjectMocks
    private StudentRosterServiceImpl service;

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
    @DisplayName("应该整批拒绝_当学号在文件内重复")
    void shouldRejectImport_WhenStudentNoDuplicatedInFile_ThenNothingPersisted() {
        when(studentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());

        RosterImportResult result = service.importStudents(
                excel(row("S001", "张三", "男", null, null, null, "否"),
                        row("S001", "李四", "女", null, null, null, "否")), null);

        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getRow()).isEqualTo(3);
        assertThat(result.getFailures().get(0).getStudentNo()).isEqualTo("S001");
        assertThat(result.getFailures().get(0).getReason()).contains("重复");
        verify(studentMapper, never()).insert(any(Student.class));
        verify(userService, never()).createUser(any());
    }

    @Test
    @DisplayName("应该整批拒绝_当学号已存在于本校")
    void shouldRejectImport_WhenStudentNoAlreadyExists_ThenReasonSaysExists() {
        Student existing = new Student();
        existing.setId(9L);
        existing.setStudentNo("S001");
        existing.setSchoolId(1L);
        when(studentMapper.selectList(any())).thenReturn(List.of(existing));
        when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());

        RosterImportResult result = service.importStudents(
                excel(row("S001", "张三", "男", null, null, null, "否")), null);

        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getFailures().get(0).getReason()).isEqualTo("学号已存在");
        verify(studentMapper, never()).insert(any(Student.class));
    }

    @Test
    @DisplayName("应该整批拒绝_当班级名称在本校不存在")
    void shouldRejectImport_WhenClassNotFound_ThenReasonNamesClass() {
        when(studentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(classMapper.selectList(any())).thenReturn(Collections.emptyList());

        RosterImportResult result = service.importStudents(
                excel(row("S001", "张三", "男", null, null, "一年级9班", "否"),
                        row("S002", "李四", "女", null, null, null, "否")), null);

        // 第二行合法，但整批仍不落库
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getFailures().get(0).getRow()).isEqualTo(2);
        assertThat(result.getFailures().get(0).getReason()).contains("一年级9班").contains("不存在");
        verify(studentMapper, never()).insert(any(Student.class));
        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    @DisplayName("应该逐行报出格式错误_当性别和日期填写不规范")
    void shouldCollectRowErrors_WhenGenderOrDateInvalid_ThenEachRowReported() {
        when(studentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());

        RosterImportResult result = service.importStudents(
                excel(row("S001", "张三", "未知", null, null, null, "否"),
                        row("S002", "李四", "女", "2010年13月", null, null, "否"),
                        row("S003", "", "男", null, null, null, "否")), null);

        assertThat(result.getFailCount()).isEqualTo(3);
        assertThat(result.getFailures()).extracting(RosterImportResult.Failure::getReason)
                .anySatisfy(r -> assertThat(r).contains("性别"))
                .anySatisfy(r -> assertThat(r).contains("出生日期"))
                .anySatisfy(r -> assertThat(r).contains("姓名"));
    }

    @Test
    @DisplayName("应该整批拒绝_当要开户的学号已被用户名占用")
    void shouldRejectImport_WhenUsernameTaken_ThenReasonSaysTaken() {
        SysUser taken = new SysUser();
        taken.setUsername("S001");
        when(studentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserMapper.selectList(any())).thenReturn(List.of(taken));

        RosterImportResult result = service.importStudents(
                excel(row("S001", "张三", "男", null, null, null, "是")), null);

        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getFailures().get(0).getReason()).contains("已被占用");
        verify(userService, never()).createUser(any());
    }

    @Test
    @DisplayName("应该抛出异常_当超管未指定学校")
    void shouldThrow_WhenSuperAdminWithoutSchoolId_ThenParamError() {
        TestAuth.asSuperAdmin();

        assertThatThrownBy(() -> service.importStudents(
                excel(row("S001", "张三", "男", null, null, null, "否")), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学校");
    }

    @Test
    @DisplayName("应该抛出异常_当校管理员指定了别校")
    void shouldThrow_WhenSchoolAdminTargetsOtherSchool_ThenForbidden() {
        assertThatThrownBy(() -> service.importStudents(
                excel(row("S001", "张三", "男", null, null, null, "否")), 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("其他学校");
    }

    // ------------------------------------------------------------------ 导入：成功

    @Test
    @DisplayName("应该创建账号并回写user_id_当「是否开通账号」为是")
    void shouldCreateAccount_WhenOpenAccountYes_ThenUserBoundToStudent() {
        when(studentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(userService.createUser(any(UserCreateRequest.class)))
                .thenReturn(UserResponse.builder().id(500L).username("S001").build());

        RosterImportResult result = service.importStudents(
                excel(row("S001", "张三", "男", "2010/9/1", "2024-09-01", null, "是")), null);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailCount()).isZero();
        assertThat(result.getCreatedAccounts()).hasSize(1);
        assertThat(result.getCreatedAccounts().get(0).getUsername()).isEqualTo("S001");
        assertThat(result.getCreatedAccounts().get(0).getInitialPassword()).isEqualTo("123456");

        ArgumentCaptor<UserCreateRequest> userCaptor = ArgumentCaptor.forClass(UserCreateRequest.class);
        verify(userService).createUser(userCaptor.capture());
        UserCreateRequest userReq = userCaptor.getValue();
        assertThat(userReq.getUsername()).isEqualTo("S001");
        assertThat(userReq.getPassword()).isEqualTo("123456");
        assertThat(userReq.getRealName()).isEqualTo("张三");
        assertThat(userReq.getRole()).isEqualTo("student");
        assertThat(userReq.getSchoolId()).isEqualTo(1L);
        assertThat(userReq.getStatus()).isEqualTo(1);

        ArgumentCaptor<Student> studentCaptor = ArgumentCaptor.forClass(Student.class);
        verify(studentMapper).insert(studentCaptor.capture());
        Student saved = studentCaptor.getValue();
        assertThat(saved.getUserId()).isEqualTo(500L);
        assertThat(saved.getSchoolId()).isEqualTo(1L);
        assertThat(saved.getStudentNo()).isEqualTo("S001");
        assertThat(saved.getGender()).isEqualTo(1);
        assertThat(saved.getBirthDate()).isEqualTo(LocalDate.of(2010, 9, 1));
        assertThat(saved.getEnrollmentDate()).isEqualTo(LocalDate.of(2024, 9, 1));
        assertThat(saved.getStatus()).isEqualTo(1);
        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    @DisplayName("应该不开户只建档_当「是否开通账号」为空或否")
    void shouldImportWithoutAccount_WhenOpenAccountBlank_ThenUserIdNull() {
        when(studentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());

        RosterImportResult result = service.importStudents(
                excel(row("S001", "张三", "女", null, null, null, null),
                        row("S002", "李四", null, null, null, null, "否")), null);

        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getCreatedAccounts()).isEmpty();
        verify(userService, never()).createUser(any());
        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentMapper, times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(s -> assertThat(s.getUserId()).isNull());
    }

    @Test
    @DisplayName("应该选最新学期的班级并提示_当班级名称在本校重名")
    void shouldPickLatestTermClass_WhenClassNameDuplicated_ThenWarnAndBind() {
        SchoolClass older = schoolClass(10L, "一年级1班", 100L);
        SchoolClass newer = schoolClass(11L, "一年级1班", 101L);
        when(studentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(classMapper.selectList(any())).thenReturn(Arrays.asList(newer, older));
        when(academicTermMapper.selectBatchIds(any())).thenReturn(Arrays.asList(
                term(100L, "2023-2024", 1), term(101L, "2024-2025", 1)));
        doAnswer(inv -> {
            inv.getArgument(0, Student.class).setId(77L);
            return 1;
        }).when(studentMapper).insert(any(Student.class));

        RosterImportResult result = service.importStudents(
                excel(row("S001", "张三", "男", null, null, "一年级1班", "否")), null);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getWarnings()).hasSize(1);
        assertThat(result.getWarnings().get(0)).contains("一年级1班").contains("11");
        ArgumentCaptor<ClassStudent> captor = ArgumentCaptor.forClass(ClassStudent.class);
        verify(classStudentMapper).insert(captor.capture());
        assertThat(captor.getValue().getClassId()).isEqualTo(11L);
        assertThat(captor.getValue().getStudentId()).isEqualTo(77L);
    }

    @Test
    @DisplayName("应该去掉数字单元格的.0尾巴_当学号按数字填写")
    void shouldStripDecimalSuffix_WhenStudentNoIsNumericCell_ThenPlainCodeSaved() {
        when(studentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());

        RosterImportResult result = service.importStudents(
                excel(row("123.0", "张三", "1.0", null, null, null, "0.0")), null);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentMapper).insert(captor.capture());
        assertThat(captor.getValue().getStudentNo()).isEqualTo("123");
        assertThat(captor.getValue().getGender()).isEqualTo(1);
        assertThat(captor.getValue().getUserId()).isNull();
    }

    @Test
    @DisplayName("应该跳过空白行_当Excel尾部有格式行")
    void shouldSkipBlankRows_WhenTrailingEmptyRows_ThenOnlyRealRowsCounted() {
        when(studentMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(sysUserMapper.selectList(any())).thenReturn(Collections.emptyList());

        RosterImportResult result = service.importStudents(
                excel(row("S001", "张三", "男", null, null, null, "否"),
                        row(null, null, null, null, null, null, null)), null);

        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailCount()).isZero();
    }

    // ------------------------------------------------------------------ 导出

    @Test
    @DisplayName("应该导出含班级与账号用户名的xlsx_当校管理员按筛选导出")
    void shouldExportXlsx_WhenSchoolAdmin_ThenScopedToOwnSchoolWithClassAndUsername() throws Exception {
        Student s1 = new Student();
        s1.setId(1L);
        s1.setStudentNo("S001");
        s1.setName("张三");
        s1.setGender(1);
        s1.setSchoolId(1L);
        s1.setUserId(500L);
        s1.setStatus(1);
        s1.setBirthDate(LocalDate.of(2010, 9, 1));
        Page<Student> page = new Page<>();
        page.setRecords(List.of(s1));
        when(studentMapper.selectPageByQuery(any(), any())).thenReturn(page);

        StudentClassRow classRow = new StudentClassRow();
        classRow.setStudentId(1L);
        classRow.setClassId(11L);
        classRow.setClassName("一年级1班");
        classRow.setGradeName("一年级");
        when(classStudentMapper.selectClassRowsByStudentIds(any())).thenReturn(List.of(classRow));

        School school = new School();
        school.setId(1L);
        school.setSchoolName("实验小学");
        when(schoolMapper.selectBatchIds(any())).thenReturn(List.of(school));

        SysUser user = new SysUser();
        user.setId(500L);
        user.setUsername("S001");
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(user));

        StudentQueryRequest request = new StudentQueryRequest();
        request.setSchoolId(99L); // 校管理员传了别校也会被覆盖成本校
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.exportStudents(request, response);

        assertThat(request.getSchoolId()).isEqualTo(1L);
        assertThat(response.getContentType()).contains("spreadsheetml");
        assertThat(response.getHeader("Content-disposition")).contains("attachment");
        List<StudentExportExcel> rows = EasyExcel.read(new ByteArrayInputStream(response.getContentAsByteArray()))
                .head(StudentExportExcel.class).sheet().doReadSync();
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStudentNo()).isEqualTo("S001");
        assertThat(rows.get(0).getClassName()).isEqualTo("一年级1班");
        assertThat(rows.get(0).getGradeName()).isEqualTo("一年级");
        assertThat(rows.get(0).getUsername()).isEqualTo("S001");
        assertThat(rows.get(0).getSchoolName()).isEqualTo("实验小学");
        assertThat(rows.get(0).getBirthDate()).isEqualTo("2010-09-01");
        assertThat(rows.get(0).getGender()).isEqualTo("男");
        assertThat(rows.get(0).getStatus()).isEqualTo("在读");
    }

    @Test
    @DisplayName("应该抛出异常_当筛选条件下没有学生")
    void shouldThrow_WhenExportHasNoRows_ThenBusinessException() {
        when(studentMapper.selectPageByQuery(any(), any())).thenReturn(new Page<>());

        assertThatThrownBy(() -> service.exportStudents(new StudentQueryRequest(), new MockHttpServletResponse()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有学生数据");
    }

    // ------------------------------------------------------------------ helpers

    private static StudentImportExcel row(String no, String name, String gender, String birth,
                                          String enroll, String className, String open) {
        StudentImportExcel r = new StudentImportExcel();
        r.setStudentNo(no);
        r.setName(name);
        r.setGender(gender);
        r.setBirthDate(birth);
        r.setEnrollmentDate(enroll);
        r.setClassName(className);
        r.setOpenAccount(open);
        return r;
    }

    private static MockMultipartFile excel(StudentImportExcel... rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, StudentImportExcel.class).sheet("学生导入").doWrite(Arrays.asList(rows));
        return new MockMultipartFile("file", "students.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
    }

    private static SchoolClass schoolClass(Long id, String name, Long termId) {
        SchoolClass c = new SchoolClass();
        c.setId(id);
        c.setSchoolId(1L);
        c.setClassName(name);
        c.setTermId(termId);
        return c;
    }

    private static AcademicTerm term(Long id, String year, Integer termNo) {
        AcademicTerm t = new AcademicTerm();
        t.setId(id);
        t.setSchoolId(1L);
        t.setYear(year);
        t.setTerm(termNo);
        return t;
    }

}
