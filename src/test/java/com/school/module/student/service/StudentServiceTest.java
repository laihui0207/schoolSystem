package com.school.module.student.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.class_module.entity.SchoolClass;
import com.school.module.class_module.mapper.ClassMapper;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.module.student.dto.*;
import com.school.module.student.entity.ClassStudent;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.ClassStudentMapper;
import com.school.module.student.mapper.StudentMapper;
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

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Student Service Unit Tests
 * TDD Approach: Test First, Implementation Second
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("学生服务测试")
class StudentServiceTest {

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private ClassStudentMapper classStudentMapper;

    @Mock
    private SchoolMapper schoolMapper;

    // ponytail: StudentServiceImpl 新增 ClassMapper 依赖做班级存在/同校校验，不加这个 @Mock 会注入 null 直接 NPE
    @Mock
    private ClassMapper classMapper;

    @InjectMocks
    private StudentServiceImpl studentService;

    private StudentCreateRequest createRequest;
    private StudentUpdateRequest updateRequest;
    private Student mockStudent;

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @BeforeEach
    void setUp() {
        TestAuth.asSchoolAdmin(1L);

        // 准备创建请求
        createRequest = new StudentCreateRequest();
        createRequest.setStudentNo("2024001");
        createRequest.setSchoolId(1L);
        createRequest.setName("张三");
        createRequest.setGender(1);
        createRequest.setBirthDate(LocalDate.of(2010, 1, 1));
        createRequest.setEnrollmentDate(LocalDate.of(2024, 9, 1));

        // 准备更新请求
        updateRequest = new StudentUpdateRequest();
        updateRequest.setName("李四");
        updateRequest.setGender(2);
        updateRequest.setStatus(1);

        // 准备Mock学生
        mockStudent = new Student();
        mockStudent.setId(1L);
        mockStudent.setStudentNo("2024001");
        mockStudent.setSchoolId(1L);
        mockStudent.setName("张三");
        mockStudent.setGender(1);
        mockStudent.setBirthDate(LocalDate.of(2010, 1, 1));
        mockStudent.setEnrollmentDate(LocalDate.of(2024, 9, 1));
        mockStudent.setStatus(1);
        mockStudent.setDeleted(0);
    }

    private SchoolClass mockClass(Long id, Long schoolId, String className) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(id);
        schoolClass.setSchoolId(schoolId);
        schoolClass.setClassName(className);
        return schoolClass;
    }

    @Test
    @DisplayName("应该成功创建学生_当数据有效")
    void shouldCreateStudent_WhenValidData_ThenSuccess() {
        // Given
        when(studentMapper.selectByStudentNo("2024001")).thenReturn(null);
        when(studentMapper.insert(any(Student.class))).thenReturn(1);

        // When
        StudentResponse response = studentService.createStudent(createRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("张三");
        assertThat(response.getStudentNo()).isEqualTo("2024001");
        assertThat(response.getStatus()).isEqualTo(1); // 默认在读
        verify(studentMapper).selectByStudentNo("2024001");
        verify(studentMapper).insert(any(Student.class));
    }

    @Test
    @DisplayName("应该抛出异常_当学号重复")
    void shouldThrowException_WhenStudentNoExists_ThenDuplicate() {
        // Given
        when(studentMapper.selectByStudentNo("2024001")).thenReturn(mockStudent);

        // When & Then
        assertThatThrownBy(() -> studentService.createStudent(createRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学号已存在");

        verify(studentMapper).selectByStudentNo("2024001");
        verify(studentMapper, never()).insert(any(Student.class));
    }

    @Test
    @DisplayName("应该抛出异常_当性别值无效")
    void shouldThrowException_WhenInvalidGender_ThenValidationFailed() {
        // Given
        createRequest.setGender(99);
        when(studentMapper.selectByStudentNo("2024001")).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> studentService.createStudent(createRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("性别值无效");

        verify(studentMapper, never()).insert(any(Student.class));
    }

    @Test
    @DisplayName("应该成功更新学生信息_当学生存在")
    void shouldUpdateStudent_WhenStudentExists_ThenSuccess() {
        // Given
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);
        when(studentMapper.updateById(any(Student.class))).thenReturn(1);

        // When
        StudentResponse response = studentService.updateStudent(1L, updateRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("李四");
        assertThat(response.getGender()).isEqualTo(2);
        verify(studentMapper).selectById(1L);
        verify(studentMapper).updateById(any(Student.class));
    }

    @Test
    @DisplayName("应该抛出异常_当更新不存在的学生")
    void shouldThrowException_WhenUpdateNonexistentStudent_ThenNotFound() {
        // Given
        when(studentMapper.selectById(999L)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> studentService.updateStudent(999L, updateRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学生不存在");

        verify(studentMapper).selectById(999L);
        verify(studentMapper, never()).updateById(any(Student.class));
    }

    @Test
    @DisplayName("应该抛出异常_当更新时性别值无效")
    void shouldThrowException_WhenUpdateWithInvalidGender_ThenValidationFailed() {
        // Given
        updateRequest.setGender(99);
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);

        // When & Then
        assertThatThrownBy(() -> studentService.updateStudent(1L, updateRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("性别值无效");

        verify(studentMapper).selectById(1L);
        verify(studentMapper, never()).updateById(any(Student.class));
    }

    @Test
    @DisplayName("应该成功分班_当学生未在该班级")
    void shouldAssignToClass_WhenNotInClass_ThenSuccess() {
        // Given
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);
        when(classMapper.selectById(1L)).thenReturn(mockClass(1L, 1L, "一班"));
        when(classStudentMapper.selectClassIdsByStudentId(1L)).thenReturn(Collections.emptyList());
        when(classStudentMapper.insert(any(ClassStudent.class))).thenReturn(1);

        // When
        studentService.assignToClass(1L, 1L);

        // Then
        verify(studentMapper).selectById(1L);
        verify(classStudentMapper).selectClassIdsByStudentId(1L);
        verify(classStudentMapper).insert(any(ClassStudent.class));
    }

    @Test
    @DisplayName("应该抛出异常_当学生已在该班级")
    void shouldThrowException_WhenAlreadyInClass_ThenDuplicate() {
        // Given
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);
        when(classMapper.selectById(1L)).thenReturn(mockClass(1L, 1L, "一班"));
        when(classStudentMapper.selectClassIdsByStudentId(1L)).thenReturn(Arrays.asList(1L));

        // When & Then
        assertThatThrownBy(() -> studentService.assignToClass(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学生已在该班级中");

        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    @DisplayName("应该抛出异常_当分班目标班级不存在")
    void shouldThrowException_WhenAssignToNonexistentClass_ThenNotFound() {
        // Given
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);
        when(classMapper.selectById(9L)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> studentService.assignToClass(1L, 9L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("班级不存在");

        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    @DisplayName("应该抛出异常_当分班目标班级不同校")
    void shouldThrowException_WhenAssignToOtherSchoolClass_ThenForbidden() {
        // Given: 学生在学校 1，班级属于学校 2
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);
        when(classMapper.selectById(1L)).thenReturn(mockClass(1L, 2L, "别校一班"));

        // When & Then
        assertThatThrownBy(() -> studentService.assignToClass(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于同一学校");

        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    @DisplayName("应该提示转班_当学生已在其他班级")
    void shouldThrowException_WhenAlreadyInAnotherClass_ThenUseTransfer() {
        // Given: 学生已在 1 班，试图再分到 2 班
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);
        when(classMapper.selectById(2L)).thenReturn(mockClass(2L, 1L, "二班"));
        when(classStudentMapper.selectClassIdsByStudentId(1L)).thenReturn(Arrays.asList(1L));
        when(classMapper.selectById(1L)).thenReturn(mockClass(1L, 1L, "一班"));

        // When & Then
        assertThatThrownBy(() -> studentService.assignToClass(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("一班")
                .hasMessageContaining("请使用转班");

        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    @DisplayName("应该用班级ID兜底提示转班_当关联的旧班级已被逻辑删除")
    void shouldFallbackToClassId_WhenCurrentClassDeleted_ThenUseTransfer() {
        // ponytail: G-4 — 守卫里 classMapper.selectById(旧班) 因逻辑删除返回 null 的分支此前无覆盖；
        // 正常路径下 selectClassIdsByStudentId 已 JOIN class 过滤 deleted，这里只锁定服务层的 null 兜底文案
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);
        when(classMapper.selectById(2L)).thenReturn(mockClass(2L, 1L, "二班"));
        when(classStudentMapper.selectClassIdsByStudentId(1L)).thenReturn(Arrays.asList(5L));
        when(classMapper.selectById(5L)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> studentService.assignToClass(1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("5")
                .hasMessageContaining("请使用转班");

        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    @DisplayName("应该成功转班_当条件满足")
    void shouldTransferClass_WhenValid_ThenSuccess() {
        // Given
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);
        when(classMapper.selectById(2L)).thenReturn(mockClass(2L, 1L, "二班"));
        when(classStudentMapper.selectClassIdsByStudentId(1L)).thenReturn(Arrays.asList(1L));
        when(classStudentMapper.deleteByStudentIdAndClassId(1L, 1L)).thenReturn(1);
        when(classStudentMapper.insert(any(ClassStudent.class))).thenReturn(1);

        // When
        studentService.transferClass(1L, 1L, 2L);

        // Then
        verify(classStudentMapper).deleteByStudentIdAndClassId(1L, 1L);
        verify(classStudentMapper).insert(any(ClassStudent.class));
    }

    @Test
    @DisplayName("应该抛出异常_当学生不在原班级")
    void shouldThrowException_WhenNotInSourceClass_ThenInvalidTransfer() {
        // Given
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);
        when(classMapper.selectById(2L)).thenReturn(mockClass(2L, 1L, "二班"));
        when(classStudentMapper.selectClassIdsByStudentId(1L)).thenReturn(Arrays.asList(2L));

        // When & Then
        assertThatThrownBy(() -> studentService.transferClass(1L, 1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学生不在原班级中");

        verify(classStudentMapper, never()).deleteByStudentIdAndClassId(anyLong(), anyLong());
        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    @DisplayName("应该抛出异常_当学生已在目标班级")
    void shouldThrowException_WhenAlreadyInTargetClass_ThenInvalidTransfer() {
        // Given
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);
        when(classMapper.selectById(2L)).thenReturn(mockClass(2L, 1L, "二班"));
        when(classStudentMapper.selectClassIdsByStudentId(1L)).thenReturn(Arrays.asList(1L, 2L));

        // When & Then
        assertThatThrownBy(() -> studentService.transferClass(1L, 1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学生已在目标班级中");

        verify(classStudentMapper, never()).deleteByStudentIdAndClassId(anyLong(), anyLong());
        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    @DisplayName("应该抛出异常_当转班目标班级不同校")
    void shouldThrowException_WhenTransferToOtherSchoolClass_ThenForbidden() {
        // Given
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);
        when(classMapper.selectById(2L)).thenReturn(mockClass(2L, 2L, "别校二班"));

        // When & Then
        assertThatThrownBy(() -> studentService.transferClass(1L, 1L, 2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于同一学校");

        verify(classStudentMapper, never()).deleteByStudentIdAndClassId(anyLong(), anyLong());
        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    @DisplayName("应该查询班级学生列表")
    void shouldGetStudentsByClassId_WhenClassHasStudents_ThenSuccess() {
        // Given
        when(classStudentMapper.selectStudentIdsByClassId(1L)).thenReturn(Arrays.asList(1L, 2L));

        Student student1 = new Student();
        student1.setId(1L);
        student1.setStudentNo("2024001");
        student1.setName("张三");
        student1.setDeleted(0);

        Student student2 = new Student();
        student2.setId(2L);
        student2.setStudentNo("2024002");
        student2.setName("李四");
        student2.setDeleted(0);

        when(studentMapper.selectById(1L)).thenReturn(student1);
        when(studentMapper.selectById(2L)).thenReturn(student2);

        // When
        List<StudentResponse> responses = studentService.getStudentsByClassId(1L);

        // Then
        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).getName()).isEqualTo("张三");
        assertThat(responses.get(1).getName()).isEqualTo("李四");
        verify(classStudentMapper).selectStudentIdsByClassId(1L);
    }

    @Test
    @DisplayName("应该回填班级与学校名称_当学生已分班")
    void shouldFillClassesAndSchoolName_WhenStudentAssignedToClasses_ThenSuccess() {
        // Given: 学生 1 同时在两个班级（回归用例：此前 classes/schoolName 恒为空）
        mockStudent.setSchoolId(3L);
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);

        StudentClassRow row1 = new StudentClassRow();
        row1.setStudentId(1L);
        row1.setClassId(1L);
        row1.setClassName("Class3-1");
        row1.setGradeName("Grade3");

        StudentClassRow row2 = new StudentClassRow();
        row2.setStudentId(1L);
        row2.setClassId(3L);
        row2.setClassName("Class3-2");
        row2.setGradeName("Grade3");

        when(classStudentMapper.selectClassRowsByStudentIds(anyCollection()))
                .thenReturn(Arrays.asList(row1, row2));

        School school = new School();
        school.setId(3L);
        school.setSchoolName("实验中学");
        when(schoolMapper.selectBatchIds(anyCollection())).thenReturn(List.of(school));

        // When
        StudentResponse response = studentService.getStudentById(1L);

        // Then
        assertThat(response.getSchoolName()).isEqualTo("实验中学");
        assertThat(response.getClasses()).hasSize(2);
        assertThat(response.getClasses())
                .extracting(StudentResponse.ClassInfo::getClassName)
                .containsExactly("Class3-1", "Class3-2");
        assertThat(response.getClasses().get(0).getGradeName()).isEqualTo("Grade3");
    }

    @Test
    @DisplayName("应该返回空班级列表_当学生未分班")
    void shouldReturnEmptyClasses_WhenStudentHasNoClass_ThenSuccess() {
        // Given
        mockStudent.setSchoolId(3L);
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);
        when(classStudentMapper.selectClassRowsByStudentIds(anyCollection()))
                .thenReturn(Collections.emptyList());
        when(schoolMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

        // When
        StudentResponse response = studentService.getStudentById(1L);

        // Then: 空列表而非 null，前端 classes?.[0] 才不会炸
        assertThat(response.getClasses()).isNotNull().isEmpty();
        assertThat(response.getSchoolName()).isNull();
    }

    @Test
    @DisplayName("应该只查一次班级和学校_当分页返回多个学生")
    void shouldBatchQuery_WhenListingStudents_ThenNoNPlusOne() {
        // Given
        Student other = new Student();
        other.setId(2L);
        other.setStudentNo("2024002");
        other.setName("李四");
        other.setSchoolId(3L);
        mockStudent.setSchoolId(3L);

        Page<Student> resultPage = new Page<>(1, 10, 2);
        resultPage.setRecords(Arrays.asList(mockStudent, other));
        when(studentMapper.selectPageByQuery(any(Page.class), any(StudentQueryRequest.class)))
                .thenReturn(resultPage);
        when(classStudentMapper.selectClassRowsByStudentIds(anyCollection()))
                .thenReturn(Collections.emptyList());
        when(schoolMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

        // When
        studentService.listStudents(StudentQueryRequest.builder().build());

        // Then: 两个学生也只各查一次，避免 N+1
        verify(classStudentMapper, times(1)).selectClassRowsByStudentIds(anyCollection());
        verify(schoolMapper, times(1)).selectBatchIds(anyCollection());
    }

    @Test
    @DisplayName("应该删除学生_当学生存在")
    void shouldDeleteStudent_WhenStudentExists_ThenSuccess() {
        // Given
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);
        when(classStudentMapper.deleteByStudentId(1L)).thenReturn(1);
        when(studentMapper.deleteById(1L)).thenReturn(1);

        // When
        studentService.deleteStudent(1L);

        // Then
        verify(studentMapper).selectById(1L);
        verify(classStudentMapper).deleteByStudentId(1L);
        verify(studentMapper).deleteById(1L);
    }

    @Test
    @DisplayName("应该更新学生状态_当状态值有效")
    void shouldUpdateStudentStatus_WhenValidStatus_ThenSuccess() {
        // Given
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);
        when(studentMapper.updateById(any(Student.class))).thenReturn(1);

        // When
        studentService.updateStudentStatus(1L, 2); // 休学

        // Then
        ArgumentCaptor<Student> captor = ArgumentCaptor.forClass(Student.class);
        verify(studentMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(2);
    }

    @Test
    @DisplayName("应该抛出异常_当状态值无效")
    void shouldThrowException_WhenInvalidStatus_ThenValidationFailed() {
        // When & Then
        assertThatThrownBy(() -> studentService.updateStudentStatus(1L, 99))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态值无效");

        verify(studentMapper, never()).updateById(any(Student.class));
    }

    @Test
    @DisplayName("应该分页查询学生列表")
    void shouldListStudents_WhenPageQuery_ThenSuccess() {
        // Given
        StudentQueryRequest queryRequest = new StudentQueryRequest();
        queryRequest.setPageNum(1L);
        queryRequest.setPageSize(10L);

        Page<Student> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Arrays.asList(mockStudent));
        mockPage.setTotal(1);

        when(studentMapper.selectPageByQuery(any(Page.class), any(StudentQueryRequest.class)))
                .thenReturn(mockPage);

        // When
        Page<StudentResponse> result = studentService.listStudents(queryRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getName()).isEqualTo("张三");
        verify(studentMapper).selectPageByQuery(any(Page.class), any(StudentQueryRequest.class));
    }

    @Test
    @DisplayName("应该移除学生班级关联_当学生在该班级")
    void shouldRemoveFromClass_WhenStudentInClass_ThenSuccess() {
        // Given
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);
        when(classStudentMapper.deleteByStudentIdAndClassId(1L, 1L)).thenReturn(1);

        // When
        studentService.removeFromClass(1L, 1L);

        // Then
        verify(studentMapper).selectById(1L);
        verify(classStudentMapper).deleteByStudentIdAndClassId(1L, 1L);
    }

    @Test
    @DisplayName("应该抛出异常_当移除不在班级中的学生")
    void shouldThrowException_WhenRemoveNotInClass_ThenNotFound() {
        // Given
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);
        when(classStudentMapper.deleteByStudentIdAndClassId(1L, 1L)).thenReturn(0);

        // When & Then
        assertThatThrownBy(() -> studentService.removeFromClass(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学生不在该班级中");

        verify(studentMapper).selectById(1L);
        verify(classStudentMapper).deleteByStudentIdAndClassId(1L, 1L);
    }

    @Test
    @DisplayName("应该返回空列表_当班级没有学生")
    void shouldReturnEmptyList_WhenClassHasNoStudents_ThenEmpty() {
        // Given
        when(classStudentMapper.selectStudentIdsByClassId(1L)).thenReturn(Collections.emptyList());

        // When
        List<StudentResponse> responses = studentService.getStudentsByClassId(1L);

        // Then
        assertThat(responses).isEmpty();
        verify(classStudentMapper).selectStudentIdsByClassId(1L);
        verify(studentMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("应该查询学生详情_当学生存在")
    void shouldGetStudentById_WhenStudentExists_ThenSuccess() {
        // Given
        when(studentMapper.selectById(1L)).thenReturn(mockStudent);

        // When
        StudentResponse response = studentService.getStudentById(1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("张三");
        assertThat(response.getStudentNo()).isEqualTo("2024001");
        verify(studentMapper).selectById(1L);
    }

    @Test
    @DisplayName("应该抛出异常_当查询不存在的学生")
    void shouldThrowException_WhenGetNonexistentStudent_ThenNotFound() {
        // Given
        when(studentMapper.selectById(999L)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> studentService.getStudentById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学生不存在");

        verify(studentMapper).selectById(999L);
    }
}
