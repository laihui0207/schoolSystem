package com.school.module.parent.service;

import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.dict.entity.DictData;
import com.school.module.dict.service.DictService;
import com.school.module.parent.dto.ChildResponse;
import com.school.module.parent.entity.ParentStudent;
import com.school.module.parent.mapper.ParentStudentMapper;
import com.school.module.student.dto.StudentClassRow;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.ClassStudentMapper;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.user.entity.SysUser;
import com.school.module.user.enums.UserRole;
import com.school.module.user.mapper.SysUserMapper;
import com.school.security.model.LoginUser;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 家长服务单元测试：绑定三条校验（家长身份 / 同校 / 不重复）、关系字典、孩子列表与通知扩散映射
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("家长服务测试")
class ParentServiceTest {

    @Mock
    private ParentStudentMapper parentStudentMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private ClassStudentMapper classStudentMapper;

    @Mock
    private DictService dictService;

    @InjectMocks
    private ParentServiceImpl parentService;

    private static final Long PARENT_USER_ID = 9L;
    private static final Long STUDENT_ID = 42L;

    @BeforeEach
    void setUp() {
        TestAuth.asSchoolAdmin(1L);
    }

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    // ------------------------------------------------------------------
    // bindChild
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该写入绑定并返回学生简要_当家长与学生同校且关系合法")
    void shouldBindChild_WhenValid_ThenInsertWithRelationship() {
        // Given
        when(sysUserMapper.selectById(PARENT_USER_ID)).thenReturn(user(PARENT_USER_ID, 1L, UserRole.PARENT));
        when(studentMapper.selectById(STUDENT_ID)).thenReturn(student(STUDENT_ID, 1L, "S001", "张三"));
        when(dictService.getDictDataByType("relationship")).thenReturn(relationshipDict());
        when(parentStudentMapper.selectCount(any())).thenReturn(0L);
        when(parentStudentMapper.insert(any(ParentStudent.class))).thenReturn(1);
        when(studentMapper.selectBatchIds(anyCollection())).thenReturn(List.of(student(STUDENT_ID, 1L, "S001", "张三")));
        when(classStudentMapper.selectClassRowsByStudentIds(anyCollection()))
                .thenReturn(List.of(classRow(STUDENT_ID, "高一(1)班", "高一")));

        // When
        ChildResponse response = parentService.bindChild(PARENT_USER_ID, STUDENT_ID, "father");

        // Then
        ArgumentCaptor<ParentStudent> captor = ArgumentCaptor.forClass(ParentStudent.class);
        verify(parentStudentMapper).insert(captor.capture());
        assertThat(captor.getValue().getParentUserId()).isEqualTo(PARENT_USER_ID);
        assertThat(captor.getValue().getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(captor.getValue().getRelationship()).isEqualTo("father");

        assertThat(response.getStudentId()).isEqualTo(STUDENT_ID);
        assertThat(response.getStudentNo()).isEqualTo("S001");
        assertThat(response.getName()).isEqualTo("张三");
        assertThat(response.getClassName()).isEqualTo("高一(1)班");
        assertThat(response.getGradeName()).isEqualTo("高一");
        assertThat(response.getRelationship()).isEqualTo("father");
        assertThat(response.getRelationshipName()).isEqualTo("父亲");
    }

    @Test
    @DisplayName("应该默认关系为other_当未传关系")
    void shouldDefaultRelationshipToOther_WhenBlank_ThenOther() {
        // Given
        when(sysUserMapper.selectById(PARENT_USER_ID)).thenReturn(user(PARENT_USER_ID, 1L, UserRole.PARENT));
        when(studentMapper.selectById(STUDENT_ID)).thenReturn(student(STUDENT_ID, 1L, "S001", "张三"));
        when(dictService.getDictDataByType("relationship")).thenReturn(relationshipDict());
        when(parentStudentMapper.selectCount(any())).thenReturn(0L);
        when(parentStudentMapper.insert(any(ParentStudent.class))).thenReturn(1);

        // When
        ChildResponse response = parentService.bindChild(PARENT_USER_ID, STUDENT_ID, "   ");

        // Then
        ArgumentCaptor<ParentStudent> captor = ArgumentCaptor.forClass(ParentStudent.class);
        verify(parentStudentMapper).insert(captor.capture());
        assertThat(captor.getValue().getRelationship()).isEqualTo("other");
        assertThat(response.getRelationshipName()).isEqualTo("其他");
    }

    @Test
    @DisplayName("应该拒绝绑定_当目标用户不是家长账号")
    void shouldRejectBind_WhenUserIsNotParent_ThenParamError() {
        // Given: 教师账号
        when(sysUserMapper.selectById(PARENT_USER_ID)).thenReturn(user(PARENT_USER_ID, 1L, UserRole.TEACHER));

        // When & Then
        assertThatThrownBy(() -> parentService.bindChild(PARENT_USER_ID, STUDENT_ID, "father"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不是家长账号");
        verify(parentStudentMapper, never()).insert(any(ParentStudent.class));
    }

    @Test
    @DisplayName("应该拒绝绑定_当家长与学生不同校")
    void shouldRejectBind_WhenStudentInOtherSchool_ThenParamError() {
        // Given: 家长在 1 校，学生在 2 校（超管场景下租户插件不拦，必须显式比对）
        TestAuth.asSuperAdmin();
        when(sysUserMapper.selectById(PARENT_USER_ID)).thenReturn(user(PARENT_USER_ID, 1L, UserRole.PARENT));
        when(studentMapper.selectById(STUDENT_ID)).thenReturn(student(STUDENT_ID, 2L, "S001", "张三"));

        // When & Then
        assertThatThrownBy(() -> parentService.bindChild(PARENT_USER_ID, STUDENT_ID, "father"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于同一学校");
        verify(parentStudentMapper, never()).insert(any(ParentStudent.class));
    }

    @Test
    @DisplayName("应该拒绝绑定_当该学生已绑定到此家长")
    void shouldRejectBind_WhenDuplicate_ThenDataAlreadyExists() {
        // Given
        when(sysUserMapper.selectById(PARENT_USER_ID)).thenReturn(user(PARENT_USER_ID, 1L, UserRole.PARENT));
        when(studentMapper.selectById(STUDENT_ID)).thenReturn(student(STUDENT_ID, 1L, "S001", "张三"));
        when(dictService.getDictDataByType("relationship")).thenReturn(relationshipDict());
        when(parentStudentMapper.selectCount(any())).thenReturn(1L);

        // When & Then
        assertThatThrownBy(() -> parentService.bindChild(PARENT_USER_ID, STUDENT_ID, "mother"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.DATA_ALREADY_EXISTS.getCode());
        verify(parentStudentMapper, never()).insert(any(ParentStudent.class));
    }

    @Test
    @DisplayName("应该拒绝绑定_当关系不在字典取值内")
    void shouldRejectBind_WhenRelationshipInvalid_ThenParamError() {
        // Given
        when(sysUserMapper.selectById(PARENT_USER_ID)).thenReturn(user(PARENT_USER_ID, 1L, UserRole.PARENT));
        when(studentMapper.selectById(STUDENT_ID)).thenReturn(student(STUDENT_ID, 1L, "S001", "张三"));
        when(dictService.getDictDataByType("relationship")).thenReturn(relationshipDict());

        // When & Then
        assertThatThrownBy(() -> parentService.bindChild(PARENT_USER_ID, STUDENT_ID, "uncle"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("关系取值无效");
        verify(parentStudentMapper, never()).insert(any(ParentStudent.class));
    }

    @Test
    @DisplayName("应该按不存在处理_当校管理员操作他校家长")
    void shouldRejectBind_WhenParentBelongsToOtherSchool_ThenUserNotFound() {
        // Given: 当前是 1 校管理员，家长账号归属 2 校
        when(sysUserMapper.selectById(PARENT_USER_ID)).thenReturn(user(PARENT_USER_ID, 2L, UserRole.PARENT));

        // When & Then: 不泄露 id 是否存在
        assertThatThrownBy(() -> parentService.bindChild(PARENT_USER_ID, STUDENT_ID, "father"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.USER_NOT_FOUND.getCode());
        verify(studentMapper, never()).selectById(any());
    }

    // ------------------------------------------------------------------
    // unbindChild
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该软删绑定_当关系存在")
    void shouldUnbind_WhenLinkExists_ThenDeleted() {
        // Given
        when(sysUserMapper.selectById(PARENT_USER_ID)).thenReturn(user(PARENT_USER_ID, 1L, UserRole.PARENT));
        when(parentStudentMapper.delete(any())).thenReturn(1);

        // When
        parentService.unbindChild(PARENT_USER_ID, STUDENT_ID);

        // Then
        verify(parentStudentMapper).delete(any());
    }

    @Test
    @DisplayName("应该报数据不存在_当解绑的关系不存在")
    void shouldRejectUnbind_WhenLinkMissing_ThenDataNotFound() {
        // Given
        when(sysUserMapper.selectById(PARENT_USER_ID)).thenReturn(user(PARENT_USER_ID, 1L, UserRole.PARENT));
        when(parentStudentMapper.delete(any())).thenReturn(0);

        // When & Then
        assertThatThrownBy(() -> parentService.unbindChild(PARENT_USER_ID, STUDENT_ID))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.DATA_NOT_FOUND.getCode());
    }

    // ------------------------------------------------------------------
    // listMyChildren / listChildren
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该拒绝我的孩子_当当前账号不是家长")
    void shouldForbidMyChildren_WhenNotParent_ThenForbidden() {
        // Given: 校管理员
        // When & Then
        assertThatThrownBy(() -> parentService.listMyChildren())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
        verify(parentStudentMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("应该返回自己绑定的孩子_当家长查看我的孩子")
    void shouldListMyChildren_WhenParent_ThenReturnChildren() {
        // Given
        loginAsParent(PARENT_USER_ID, List.of(STUDENT_ID, 43L));
        when(parentStudentMapper.selectList(any())).thenReturn(List.of(
                link(1L, PARENT_USER_ID, STUDENT_ID, "father"),
                link(2L, PARENT_USER_ID, 43L, "father")));
        when(dictService.getDictDataByType("relationship")).thenReturn(relationshipDict());
        when(studentMapper.selectBatchIds(anyCollection())).thenReturn(List.of(
                student(STUDENT_ID, 1L, "S001", "张三"),
                student(43L, 1L, "S002", "李四")));
        when(classStudentMapper.selectClassRowsByStudentIds(anyCollection()))
                .thenReturn(List.of(classRow(STUDENT_ID, "高一(1)班", "高一")));

        // When
        List<ChildResponse> children = parentService.listMyChildren();

        // Then: 未分班的孩子 className 为 null，但仍在列表里
        assertThat(children).extracting(ChildResponse::getStudentId).containsExactly(STUDENT_ID, 43L);
        assertThat(children.get(0).getClassName()).isEqualTo("高一(1)班");
        assertThat(children.get(1).getClassName()).isNull();
        assertThat(children.get(1).getName()).isEqualTo("李四");
        assertThat(children).extracting(ChildResponse::getRelationshipName).containsOnly("父亲");
    }

    @Test
    @DisplayName("应该返回空列表_当家长尚未绑定孩子")
    void shouldReturnEmpty_WhenParentHasNoChildren_ThenNoLookup() {
        // Given
        loginAsParent(PARENT_USER_ID, List.of());
        when(parentStudentMapper.selectList(any())).thenReturn(List.of());
        when(dictService.getDictDataByType("relationship")).thenReturn(relationshipDict());

        // When
        List<ChildResponse> children = parentService.listMyChildren();

        // Then: 不能对空集合调 selectBatchIds（IN () 会报 SQL 错）
        assertThat(children).isEmpty();
        verify(studentMapper, never()).selectBatchIds(anyCollection());
    }

    // ------------------------------------------------------------------
    // 通知扩散映射
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该按学生分组家长账号并去重_当查询通知收件人")
    void shouldGroupParentUserIdsByStudent_WhenLinksExist_ThenDistinctOrdered() {
        // Given: 学生 42 有家长 9、10；学生 43 有家长 9；家长 10 重复出现一次
        when(parentStudentMapper.selectByStudentIds(anyCollection())).thenReturn(List.of(
                link(1L, 9L, 42L, "father"),
                link(2L, 10L, 42L, "mother"),
                link(3L, 10L, 42L, "mother"),
                link(4L, 9L, 43L, "father")));

        // When
        Map<Long, List<Long>> grouped = parentService.parentUserIdsByStudentIds(List.of(42L, 43L));
        List<Long> flat = parentService.parentUserIdsOf(List.of(42L, 43L));

        // Then
        assertThat(grouped).containsOnlyKeys(42L, 43L);
        assertThat(grouped.get(42L)).containsExactly(9L, 10L);
        assertThat(grouped.get(43L)).containsExactly(9L);
        assertThat(flat).containsExactly(9L, 10L);
    }

    @Test
    @DisplayName("应该返回空映射且不查库_当学生集合为空")
    void shouldReturnEmptyMap_WhenNoStudentIds_ThenNoQuery() {
        // When
        Map<Long, List<Long>> grouped = parentService.parentUserIdsByStudentIds(List.of());

        // Then
        assertThat(grouped).isEmpty();
        assertThat(parentService.parentUserIdsOf(null)).isEmpty();
        verify(parentStudentMapper, never()).selectByStudentIds(anyCollection());
    }

    // ------------------------------------------------------------------
    // 测试数据
    // ------------------------------------------------------------------

    private static void loginAsParent(Long userId, List<Long> childStudentIds) {
        TestAuth.authenticate(LoginUser.builder()
                .userId(userId)
                .username("parent" + userId)
                .schoolId(1L)
                .userType(UserRole.PARENT.getUserType())
                .childStudentIds(childStudentIds)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_PARENT")))
                .build());
    }

    private static SysUser user(Long id, Long schoolId, UserRole role) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("user" + id);
        user.setSchoolId(schoolId);
        user.setUserType(role.getUserType());
        user.setStatus(1);
        return user;
    }

    private static Student student(Long id, Long schoolId, String studentNo, String name) {
        Student student = new Student();
        student.setId(id);
        student.setSchoolId(schoolId);
        student.setStudentNo(studentNo);
        student.setName(name);
        return student;
    }

    private static ParentStudent link(Long id, Long parentUserId, Long studentId, String relationship) {
        ParentStudent link = new ParentStudent();
        link.setId(id);
        link.setParentUserId(parentUserId);
        link.setStudentId(studentId);
        link.setRelationship(relationship);
        return link;
    }

    private static StudentClassRow classRow(Long studentId, String className, String gradeName) {
        StudentClassRow row = new StudentClassRow();
        row.setStudentId(studentId);
        row.setClassName(className);
        row.setGradeName(gradeName);
        return row;
    }

    private static DictData dict(String value, String label) {
        DictData data = new DictData();
        data.setDictValue(value);
        data.setDictLabel(label);
        return data;
    }

    private static List<DictData> relationshipDict() {
        return List.of(dict("father", "父亲"), dict("mother", "母亲"), dict("other", "其他"));
    }

}
