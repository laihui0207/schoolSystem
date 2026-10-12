package com.school.module.score.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.score.dto.*;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.security.service.TeacherScopeService;
import com.school.module.user.mapper.SysUserMapper;
import com.school.module.subject.mapper.SubjectMapper;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.student.mapper.ClassStudentMapper;
import com.school.module.exam.mapper.ExamSubjectMapper;
import com.school.module.exam.mapper.ExamMapper;
import com.school.module.class_module.mapper.ClassMapper;
import com.school.module.class_module.entity.SchoolClass;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.entity.ExamSubject;
import com.school.module.student.entity.Student;
import com.school.module.subject.entity.Subject;
import com.school.module.user.entity.SysUser;
import com.school.module.user.enums.UserRole;
import com.school.security.model.LoginUser;
import com.school.support.TestAuth;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Score Service Unit Tests
 * TDD Approach: Test First, Implementation Second
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("成绩服务测试")
class ScoreServiceTest {

    @Mock
    private ScoreMapper scoreMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private ClassStudentMapper classStudentMapper;

    @Mock
    private ClassMapper classMapper;

    @Mock
    private ExamMapper examMapper;

    @Mock
    private ExamSubjectMapper examSubjectMapper;

    @Mock
    private SubjectMapper subjectMapper;

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private TeacherScopeService teacherScopeService;

    @InjectMocks
    private ScoreServiceImpl scoreService;

    private ScoreInputRequest inputRequest;
    private Score mockScore;
    private ScoreBatchInputRequest batchRequest;
    private Exam mockExam;
    private ExamSubject mockExamSubject;

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @BeforeEach
    void setUp() {
        TestAuth.asSchoolAdmin(1L);

        // 准备单个成绩录入请求
        inputRequest = new ScoreInputRequest();
        inputRequest.setExamId(1L);
        inputRequest.setStudentId(1L);
        inputRequest.setSubjectId(1L);
        inputRequest.setClassId(1L);
        inputRequest.setSchoolId(1L);
        inputRequest.setScore(BigDecimal.valueOf(95.0));
        inputRequest.setIsAbsent(0);

        // 准备Mock成绩
        mockScore = new Score();
        mockScore.setId(1L);
        mockScore.setExamId(1L);
        mockScore.setStudentId(1L);
        mockScore.setSubjectId(1L);
        mockScore.setClassId(1L);
        mockScore.setSchoolId(1L);
        mockScore.setScore(BigDecimal.valueOf(95.0));
        mockScore.setIsAbsent(0);
        mockScore.setStatus(0);
        mockScore.setRecordedAt(LocalDateTime.now());

        // 录入目标：考试 1 处于录入中，科目 1 在考试范围内且满分 100
        mockExam = new Exam();
        mockExam.setId(1L);
        mockExam.setSchoolId(1L);
        mockExam.setExamName("期中考试");
        mockExam.setStatus(1);

        mockExamSubject = new ExamSubject();
        mockExamSubject.setExamId(1L);
        mockExamSubject.setSubjectId(1L);
        mockExamSubject.setFullScore(BigDecimal.valueOf(100));
    }

    /**
     * inputScore 现在会校验 考试状态/科目范围/班级归属，录入类用例统一打这组桩
     */
    private void stubInputTargetValid() {
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(List.of(mockExamSubject));
        when(classStudentMapper.selectCount(any())).thenReturn(1L);
    }

    private static Student student(Long id, Long schoolId) {
        Student st = new Student();
        st.setId(id);
        st.setSchoolId(schoolId);
        return st;
    }

    private static void loginAsStudent(Long studentId) {
        TestAuth.authenticate(LoginUser.builder()
                .userId(500L)
                .username("student")
                .schoolId(1L)
                .userType(UserRole.STUDENT.getUserType())
                .studentId(studentId)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_STUDENT")))
                .build());
    }

    private static void loginAsTeacher(Long teacherId) {
        TestAuth.authenticate(LoginUser.builder()
                .userId(300L)
                .username("teacher")
                .schoolId(1L)
                .userType(UserRole.TEACHER.getUserType())
                .teacherId(teacherId)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_TEACHER")))
                .build());
    }

    /**
     * 考试 1 / 学校 1 的一行成绩；value 为 null 表示缺考
     */
    private static Score score(Long id, Long studentId, Long subjectId, Long classId, Double value) {
        Score s = new Score();
        s.setId(id);
        s.setExamId(1L);
        s.setStudentId(studentId);
        s.setSubjectId(subjectId);
        s.setClassId(classId);
        s.setSchoolId(1L);
        s.setScore(value != null ? BigDecimal.valueOf(value) : null);
        s.setIsAbsent(value != null ? 0 : 1);
        s.setStatus(1);
        return s;
    }

    @Test
    @DisplayName("应该成功录入单个成绩_当数据有效")
    void shouldInputScore_WhenValidData_ThenSuccess() {
        // Given
        stubInputTargetValid();
        when(scoreMapper.selectByExamStudentSubject(1L, 1L, 1L, 1L)).thenReturn(null);
        when(scoreMapper.insert(any(Score.class))).thenReturn(1);

        // When
        ScoreResponse response = scoreService.inputScore(inputRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getScore()).isEqualTo(BigDecimal.valueOf(95.0));
        assertThat(response.getStatus()).isEqualTo(0);
        verify(scoreMapper).selectByExamStudentSubject(1L, 1L, 1L, 1L);
        verify(scoreMapper).insert(any(Score.class));
    }

    @Test
    @DisplayName("应该覆盖原成绩并重置审核状态_当成绩已存在")
    void shouldUpdateScore_WhenScoreExists_ThenUpsert() {
        // Given: 成绩已存在。inputScore 是 upsert，前端保存/自动保存会重复调用同一端点
        mockScore.setStatus(1);
        mockScore.setReviewedAt(LocalDateTime.now());
        stubInputTargetValid();
        when(scoreMapper.selectByExamStudentSubject(1L, 1L, 1L, 1L)).thenReturn(mockScore);
        when(scoreMapper.updateById(any(Score.class))).thenReturn(1);
        inputRequest.setScore(BigDecimal.valueOf(88.0));

        // When
        ScoreResponse response = scoreService.inputScore(inputRequest);

        // Then: 走更新而非新增，且重新录入后审核状态回到待审核
        assertThat(response.getScore()).isEqualTo(BigDecimal.valueOf(88.0));
        assertThat(response.getStatus()).isEqualTo(0);
        assertThat(response.getReviewedAt()).isNull();
        verify(scoreMapper).updateById(any(Score.class));
        verify(scoreMapper, never()).insert(any(Score.class));
    }

    @Test
    @DisplayName("应该抛出异常_当分数为空且未标记缺考")
    void shouldThrowException_WhenScoreNullAndNotAbsent_ThenInvalidInput() {
        // Given
        inputRequest.setScore(null);
        inputRequest.setIsAbsent(0);
        // 校验在查库之前，故不需要 stub selectByExamStudentSubject

        // When & Then
        assertThatThrownBy(() -> scoreService.inputScore(inputRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未缺考时必须填写分数");

        verify(scoreMapper, never()).selectByExamStudentSubject(anyLong(), anyLong(), anyLong(), anyLong());

        verify(scoreMapper, never()).insert(any(Score.class));
    }

    @Test
    @DisplayName("应该成功录入_当学生缺考")
    void shouldInputScore_WhenStudentAbsent_ThenSuccess() {
        // Given
        inputRequest.setScore(null);
        inputRequest.setIsAbsent(1);
        stubInputTargetValid();
        when(scoreMapper.selectByExamStudentSubject(1L, 1L, 1L, 1L)).thenReturn(null);
        when(scoreMapper.insert(any(Score.class))).thenReturn(1);

        // When
        ScoreResponse response = scoreService.inputScore(inputRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getScore()).isNull();
        assertThat(response.getIsAbsent()).isEqualTo(1);
        verify(scoreMapper).insert(any(Score.class));
    }

    @Test
    @DisplayName("应该成功批量录入成绩_当数据有效")
    void shouldBatchInputScores_WhenValidData_ThenSuccess() {
        // Given
        batchRequest = new ScoreBatchInputRequest();
        batchRequest.setExamId(1L);

        List<ScoreBatchInputRequest.ScoreItem> items = new ArrayList<>();
        ScoreBatchInputRequest.ScoreItem item1 = new ScoreBatchInputRequest.ScoreItem();
        item1.setStudentId(1L);
        item1.setSubjectId(1L);
        item1.setClassId(1L);
        item1.setScore(BigDecimal.valueOf(90.0));
        items.add(item1);

        ScoreBatchInputRequest.ScoreItem item2 = new ScoreBatchInputRequest.ScoreItem();
        item2.setStudentId(2L);
        item2.setSubjectId(1L);
        item2.setClassId(1L);
        item2.setScore(BigDecimal.valueOf(85.0));
        items.add(item2);

        batchRequest.setScores(items);

        stubInputTargetValid();
        // 契约 1：schoolId 不再由请求携带，按学生档案解析
        when(studentMapper.selectById(anyLong())).thenReturn(student(1L, 1L));
        when(scoreMapper.selectByExamStudentSubject(eq(1L), eq(1L), anyLong(), eq(1L))).thenReturn(null);
        when(scoreMapper.insert(any(Score.class))).thenReturn(1);

        // When
        BatchInputResult result = scoreService.batchInputScores(batchRequest);

        // Then
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailCount()).isZero();
        assertThat(result.getFailures()).isEmpty();
        verify(scoreMapper, times(2)).insert(any(Score.class));
    }

    @Test
    @DisplayName("应该覆盖已存在的成绩_当批量录入时")
    void shouldUpsertExistingScores_WhenBatchInput_ThenAllSucceed() {
        // Given
        batchRequest = new ScoreBatchInputRequest();
        batchRequest.setExamId(1L);

        List<ScoreBatchInputRequest.ScoreItem> items = new ArrayList<>();
        ScoreBatchInputRequest.ScoreItem item1 = new ScoreBatchInputRequest.ScoreItem();
        item1.setStudentId(1L);
        item1.setSubjectId(1L);
        item1.setClassId(1L);
        item1.setScore(BigDecimal.valueOf(90.0));
        items.add(item1);

        ScoreBatchInputRequest.ScoreItem item2 = new ScoreBatchInputRequest.ScoreItem();
        item2.setStudentId(2L);
        item2.setSubjectId(1L);
        item2.setClassId(1L);
        item2.setScore(BigDecimal.valueOf(85.0));
        items.add(item2);

        batchRequest.setScores(items);

        stubInputTargetValid();
        when(studentMapper.selectById(anyLong())).thenReturn(student(1L, 1L));
        // 第一个学生已存在成绩
        when(scoreMapper.selectByExamStudentSubject(1L, 1L, 1L, 1L)).thenReturn(mockScore);
        when(scoreMapper.selectByExamStudentSubject(1L, 1L, 2L, 1L)).thenReturn(null);
        when(scoreMapper.updateById(any(Score.class))).thenReturn(1);
        when(scoreMapper.insert(any(Score.class))).thenReturn(1);

        // When
        BatchInputResult result = scoreService.batchInputScores(batchRequest);

        // Then: 已存在的走更新，不存在的走插入，两条都算成功
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailCount()).isZero();
        verify(scoreMapper, times(1)).updateById(any(Score.class));
        verify(scoreMapper, times(1)).insert(any(Score.class));
    }

    @Test
    @DisplayName("应该跳过失败项并继续_当批量录入中某条校验不通过")
    void shouldSkipInvalidItem_WhenBatchInput_ThenPartialSuccess() {
        // Given
        batchRequest = new ScoreBatchInputRequest();
        batchRequest.setExamId(1L);

        List<ScoreBatchInputRequest.ScoreItem> items = new ArrayList<>();
        // 非缺考但没有分数，单条校验会抛异常
        ScoreBatchInputRequest.ScoreItem invalid = new ScoreBatchInputRequest.ScoreItem();
        invalid.setStudentId(1L);
        invalid.setSubjectId(1L);
        invalid.setClassId(1L);
        invalid.setScore(null);
        invalid.setIsAbsent(0);
        items.add(invalid);

        ScoreBatchInputRequest.ScoreItem valid = new ScoreBatchInputRequest.ScoreItem();
        valid.setStudentId(2L);
        valid.setSubjectId(1L);
        valid.setClassId(1L);
        valid.setScore(BigDecimal.valueOf(85.0));
        items.add(valid);

        batchRequest.setScores(items);

        stubInputTargetValid();
        when(studentMapper.selectById(anyLong())).thenReturn(student(1L, 1L));
        when(scoreMapper.selectByExamStudentSubject(1L, 1L, 2L, 1L)).thenReturn(null);
        when(scoreMapper.insert(any(Score.class))).thenReturn(1);

        // When
        BatchInputResult result = scoreService.batchInputScores(batchRequest);

        // Then: 失败项记入 failures 而不是只写日志
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getStudentId()).isEqualTo(1L);
        assertThat(result.getFailures().get(0).getSubjectId()).isEqualTo(1L);
        assertThat(result.getFailures().get(0).getReason()).contains("未缺考时必须填写分数");
        verify(scoreMapper, times(1)).insert(any(Score.class));
    }

    @Test
    @DisplayName("应该记入失败明细且不落库_当批量录入的学生不存在")
    void shouldRecordFailure_WhenBatchStudentNotFound_ThenNotInserted() {
        // Given
        batchRequest = new ScoreBatchInputRequest();
        batchRequest.setExamId(1L);
        ScoreBatchInputRequest.ScoreItem ghost = new ScoreBatchInputRequest.ScoreItem();
        ghost.setStudentId(99L);
        ghost.setSubjectId(1L);
        ghost.setClassId(1L);
        ghost.setScore(BigDecimal.valueOf(60.0));
        batchRequest.setScores(List.of(ghost));

        when(studentMapper.selectById(99L)).thenReturn(null);

        // When
        BatchInputResult result = scoreService.batchInputScores(batchRequest);

        // Then
        assertThat(result.getSuccessCount()).isZero();
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getFailures().get(0).getStudentId()).isEqualTo(99L);
        assertThat(result.getFailures().get(0).getReason()).contains("学生不存在");
        verify(scoreMapper, never()).insert(any(Score.class));
    }

    @Test
    @DisplayName("应该回退到顶层默认班级和科目并透传备注_当单项未指定")
    void shouldUseTopLevelDefaults_WhenItemClassAndSubjectNull_ThenInputWithDefaults() {
        // Given: 顶层给默认 classId/subjectId，单项只有 studentId/score/remark
        batchRequest = new ScoreBatchInputRequest();
        batchRequest.setExamId(1L);
        batchRequest.setClassId(1L);
        batchRequest.setSubjectId(1L);
        ScoreBatchInputRequest.ScoreItem item = new ScoreBatchInputRequest.ScoreItem();
        item.setStudentId(2L);
        item.setScore(BigDecimal.valueOf(77.0));
        item.setRemark("补考");
        batchRequest.setScores(List.of(item));

        stubInputTargetValid();
        when(studentMapper.selectById(2L)).thenReturn(student(2L, 1L));
        when(scoreMapper.selectByExamStudentSubject(1L, 1L, 2L, 1L)).thenReturn(null);
        when(scoreMapper.insert(any(Score.class))).thenReturn(1);

        // When
        BatchInputResult result = scoreService.batchInputScores(batchRequest);

        // Then
        assertThat(result.getSuccessCount()).isEqualTo(1);
        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper).insert(captor.capture());
        Score inserted = captor.getValue();
        assertThat(inserted.getClassId()).isEqualTo(1L);
        assertThat(inserted.getSubjectId()).isEqualTo(1L);
        assertThat(inserted.getSchoolId()).isEqualTo(1L);
        assertThat(inserted.getIsAbsent()).isEqualTo(0);
        assertThat(inserted.getRemark()).isEqualTo("补考");
    }

    @Test
    @DisplayName("应该拒绝录入_当考试不在录入中状态")
    void shouldThrowException_WhenExamNotRecording_ThenRejected() {
        // Given: 考试已发布
        mockExam.setStatus(3);
        when(examMapper.selectById(1L)).thenReturn(mockExam);

        // When & Then
        assertThatThrownBy(() -> scoreService.inputScore(inputRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("考试不在录入中状态");
        verify(scoreMapper, never()).selectByExamStudentSubject(anyLong(), anyLong(), anyLong(), anyLong());
        verify(scoreMapper, never()).insert(any(Score.class));
    }

    @Test
    @DisplayName("应该拒绝录入_当科目不在考试范围内")
    void shouldThrowException_WhenSubjectNotInExam_ThenRejected() {
        // Given
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(List.of());

        // When & Then
        assertThatThrownBy(() -> scoreService.inputScore(inputRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该科目不在本次考试范围内");
        verify(scoreMapper, never()).insert(any(Score.class));
    }

    @Test
    @DisplayName("应该拒绝录入_当分数超出满分")
    void shouldThrowException_WhenScoreOutOfRange_ThenRejected() {
        // Given: 满分 100，录 120
        inputRequest.setScore(BigDecimal.valueOf(120.0));
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(List.of(mockExamSubject));

        // When & Then
        assertThatThrownBy(() -> scoreService.inputScore(inputRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("分数超出范围");
        verify(scoreMapper, never()).insert(any(Score.class));
    }

    @Test
    @DisplayName("应该拒绝录入_当学生不在该班级")
    void shouldThrowException_WhenStudentNotInClass_ThenRejected() {
        // Given
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(List.of(mockExamSubject));
        when(classStudentMapper.selectCount(any())).thenReturn(0L);

        // When & Then
        assertThatThrownBy(() -> scoreService.inputScore(inputRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学生不在该班级");
        verify(scoreMapper, never()).insert(any(Score.class));
    }

    @Test
    @DisplayName("应该查询成绩详情_当成绩存在")
    void shouldGetScoreById_WhenScoreExists_ThenSuccess() {
        // Given
        when(scoreMapper.selectById(1L)).thenReturn(mockScore);

        // When
        ScoreResponse response = scoreService.getScoreById(1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getScore()).isEqualTo(BigDecimal.valueOf(95.0));
        verify(scoreMapper).selectById(1L);
    }

    @Test
    @DisplayName("应该抛出异常_当查询不存在的成绩")
    void shouldThrowException_WhenScoreNotFound_ThenNotFound() {
        // Given
        when(scoreMapper.selectById(999L)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> scoreService.getScoreById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("成绩不存在");

        verify(scoreMapper).selectById(999L);
    }

    @Test
    @DisplayName("应该查询学生某次考试的所有成绩")
    void shouldGetStudentExamScores_WhenDataExists_ThenSuccess() {
        // Given
        List<Score> scores = Arrays.asList(mockScore);
        Page<Score> page = new Page<>(1, 100);
        page.setRecords(scores);
        page.setTotal(1);

        when(scoreMapper.selectPageByQuery(any(Page.class), any(ScoreQueryRequest.class)))
                .thenReturn(page);

        // When
        List<ScoreResponse> responses = scoreService.getStudentExamScores(1L, 1L);

        // Then
        assertThat(responses).isNotEmpty();
        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getExamId()).isEqualTo(1L);
        assertThat(responses.get(0).getStudentId()).isEqualTo(1L);
        verify(scoreMapper).selectPageByQuery(any(Page.class), any(ScoreQueryRequest.class));
    }

    @Test
    @DisplayName("应该拒绝_当学生查询他人某次考试的成绩")
    void shouldThrowException_WhenStudentQueriesOthersExamScores_ThenForbidden() {
        // Given: 学生档案 ID 为 42
        loginAsStudent(42L);

        // When & Then
        assertThatThrownBy(() -> scoreService.getStudentExamScores(1L, 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学生只能查询自己的成绩");
        verify(scoreMapper, never()).selectPageByQuery(any(Page.class), any(ScoreQueryRequest.class));
    }

    @Test
    @DisplayName("应该强制只看已发布考试_当学生查询自己某次考试的成绩")
    void shouldForcePublishedOnly_WhenStudentQueriesOwnExamScores() {
        // Given
        loginAsStudent(42L);
        Page<Score> page = new Page<>(1, 100);
        page.setRecords(new ArrayList<>());
        page.setTotal(0);
        when(scoreMapper.selectPageByQuery(any(Page.class), any(ScoreQueryRequest.class))).thenReturn(page);

        // When
        scoreService.getStudentExamScores(1L, 42L);

        // Then
        ArgumentCaptor<ScoreQueryRequest> captor = ArgumentCaptor.forClass(ScoreQueryRequest.class);
        verify(scoreMapper).selectPageByQuery(any(Page.class), captor.capture());
        assertThat(captor.getValue().getStudentId()).isEqualTo(42L);
        assertThat(captor.getValue().getPublishedOnly()).isTrue();
    }

    @Test
    @DisplayName("应该审核通过成绩_当状态为待审核")
    void shouldReviewScores_WhenStatusPending_ThenApproved() {
        // Given
        ScoreReviewRequest reviewRequest = new ScoreReviewRequest();
        reviewRequest.setScoreIds(Arrays.asList(1L));
        reviewRequest.setResult(1); // 通过

        when(scoreMapper.selectById(1L)).thenReturn(mockScore);
        when(scoreMapper.updateById(any(Score.class))).thenReturn(1);

        // When
        scoreService.reviewScores(reviewRequest);

        // Then
        ArgumentCaptor<Score> scoreCaptor = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper).updateById(scoreCaptor.capture());
        Score updated = scoreCaptor.getValue();
        assertThat(updated.getStatus()).isEqualTo(1);
        assertThat(updated.getReviewedAt()).isNotNull();
    }

    @Test
    @DisplayName("应该驳回成绩_当审核不通过")
    void shouldReviewScores_WhenReject_ThenRejected() {
        // Given
        ScoreReviewRequest reviewRequest = new ScoreReviewRequest();
        reviewRequest.setScoreIds(Arrays.asList(1L));
        reviewRequest.setResult(2); // 驳回
        reviewRequest.setRejectReason("分数录入错误");

        when(scoreMapper.selectById(1L)).thenReturn(mockScore);
        when(scoreMapper.updateById(any(Score.class))).thenReturn(1);

        // When
        scoreService.reviewScores(reviewRequest);

        // Then
        ArgumentCaptor<Score> scoreCaptor = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper).updateById(scoreCaptor.capture());
        Score updated = scoreCaptor.getValue();
        assertThat(updated.getStatus()).isEqualTo(2);
        // B-10：驳回原因以前缀写入 remark 留痕
        assertThat(updated.getRemark()).isEqualTo("【驳回】分数录入错误");
    }

    @Test
    @DisplayName("应该走scoreIds路径_当给了成绩ID列表_即使同时给了三元组")
    void shouldReviewByScoreIds_WhenIdsGiven_ThenSkipTripletLookup() {
        // Given
        ScoreReviewRequest reviewRequest = new ScoreReviewRequest();
        reviewRequest.setScoreIds(Arrays.asList(1L));
        reviewRequest.setExamId(1L);
        reviewRequest.setClassId(1L);
        reviewRequest.setSubjectId(1L);
        reviewRequest.setResult(1);

        when(scoreMapper.selectById(1L)).thenReturn(mockScore);
        when(scoreMapper.updateById(any(Score.class))).thenReturn(1);

        // When
        scoreService.reviewScores(reviewRequest);

        // Then
        verify(scoreMapper, never()).selectByExamId(anyLong(), anyLong(), anyLong());
        verify(scoreMapper).selectById(1L);
        verify(scoreMapper).updateById(any(Score.class));
    }

    @Test
    @DisplayName("应该按三元组解析待审核成绩_当未给scoreIds")
    void shouldReviewByTriplet_WhenNoScoreIds_ThenResolvePendingIds() {
        // Given: 同班同考试有 3 条成绩，只有 id=1 是目标科目且待审核
        Score pendingTarget = new Score();
        pendingTarget.setId(1L);
        pendingTarget.setSubjectId(1L);
        pendingTarget.setStatus(0);

        Score approvedTarget = new Score();
        approvedTarget.setId(2L);
        approvedTarget.setSubjectId(1L);
        approvedTarget.setStatus(1);

        Score pendingOtherSubject = new Score();
        pendingOtherSubject.setId(3L);
        pendingOtherSubject.setSubjectId(2L);
        pendingOtherSubject.setStatus(0);

        ScoreReviewRequest reviewRequest = new ScoreReviewRequest();
        reviewRequest.setExamId(1L);
        reviewRequest.setClassId(1L);
        reviewRequest.setSubjectId(1L);
        reviewRequest.setResult(2);
        reviewRequest.setRejectReason("分数异常");

        when(scoreMapper.selectByExamId(1L, 1L, 1L))
                .thenReturn(Arrays.asList(pendingTarget, approvedTarget, pendingOtherSubject));
        when(scoreMapper.selectById(1L)).thenReturn(mockScore);
        when(scoreMapper.updateById(any(Score.class))).thenReturn(1);

        // When
        scoreService.reviewScores(reviewRequest);

        // Then
        verify(scoreMapper).selectById(1L);
        verify(scoreMapper, never()).selectById(2L);
        verify(scoreMapper, never()).selectById(3L);
        ArgumentCaptor<Score> scoreCaptor = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper).updateById(scoreCaptor.capture());
        assertThat(scoreCaptor.getValue().getId()).isEqualTo(1L);
        assertThat(scoreCaptor.getValue().getStatus()).isEqualTo(2);
    }

    @Test
    @DisplayName("应该抛出异常_当三元组下没有待审核成绩")
    void shouldThrowException_WhenTripletHasNoPending_ThenBusinessError() {
        // Given
        ScoreReviewRequest reviewRequest = new ScoreReviewRequest();
        reviewRequest.setExamId(1L);
        reviewRequest.setClassId(1L);
        reviewRequest.setSubjectId(1L);
        reviewRequest.setResult(1);

        when(scoreMapper.selectByExamId(1L, 1L, 1L)).thenReturn(new ArrayList<>());

        // When & Then
        assertThatThrownBy(() -> scoreService.reviewScores(reviewRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有待审核的成绩");

        verify(scoreMapper, never()).updateById(any(Score.class));
    }

    @Test
    @DisplayName("应该抛出异常_当驳回时未填写原因")
    void shouldThrowException_WhenRejectWithoutReason_ThenInvalidInput() {
        // Given
        ScoreReviewRequest reviewRequest = new ScoreReviewRequest();
        reviewRequest.setScoreIds(Arrays.asList(1L));
        reviewRequest.setResult(2); // 驳回
        reviewRequest.setRejectReason(null);

        // When & Then
        assertThatThrownBy(() -> scoreService.reviewScores(reviewRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("驳回时必须填写原因");

        verify(scoreMapper, never()).updateById(any(Score.class));
    }

    @Test
    @DisplayName("应该抛出异常_当审核结果无效")
    void shouldThrowException_WhenInvalidReviewResult_ThenInvalidInput() {
        // Given
        ScoreReviewRequest reviewRequest = new ScoreReviewRequest();
        reviewRequest.setScoreIds(Arrays.asList(1L));
        reviewRequest.setResult(99); // 无效结果

        // When & Then
        assertThatThrownBy(() -> scoreService.reviewScores(reviewRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审核结果无效");

        verify(scoreMapper, never()).updateById(any(Score.class));
    }

    @Test
    @DisplayName("应该删除成绩_当状态为待审核")
    void shouldDeleteScore_WhenStatusPending_ThenSuccess() {
        // Given
        when(scoreMapper.selectById(1L)).thenReturn(mockScore);
        when(scoreMapper.deleteById(1L)).thenReturn(1);

        // When
        scoreService.deleteScore(1L);

        // Then
        verify(scoreMapper).selectById(1L);
        verify(scoreMapper).deleteById(1L);
    }

    @Test
    @DisplayName("应该抛出异常_当删除已审核通过的成绩")
    void shouldThrowException_WhenDeleteApprovedScore_ThenNotAllowed() {
        // Given
        mockScore.setStatus(1); // 已审核通过
        when(scoreMapper.selectById(1L)).thenReturn(mockScore);

        // When & Then
        assertThatThrownBy(() -> scoreService.deleteScore(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已审核通过的成绩不能删除");

        verify(scoreMapper).selectById(1L);
        verify(scoreMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("应该抛出异常_当删除已发布考试的成绩")
    void shouldThrowException_WhenDeleteScoreOfPublishedExam_ThenNotAllowed() {
        // Given: 成绩待审核，但所属考试已发布
        mockExam.setStatus(3);
        when(scoreMapper.selectById(1L)).thenReturn(mockScore);
        when(examMapper.selectById(1L)).thenReturn(mockExam);

        // When & Then
        assertThatThrownBy(() -> scoreService.deleteScore(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("考试已发布");
        verify(scoreMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("应该计算班级排名_当有多个成绩")
    void shouldCalculateRankings_WhenMultipleScores_ThenClassRankCorrect() {
        // Given: 同班同科目三人 95/90/85
        List<Score> scores = Arrays.asList(
                score(1L, 1L, 1L, 1L, 95.0), score(2L, 2L, 1L, 1L, 90.0), score(3L, 3L, 1L, 1L, 85.0));
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(scores);
        when(scoreMapper.updateById(any(Score.class))).thenReturn(1);

        // When
        scoreService.calculateRankings(1L, 1L);

        // Then: 每行只写一次，班级名次 1/2/3
        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper, times(3)).updateById(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Score::getId, Score::getRankInClass)
                .containsExactlyInAnyOrder(tuple(1L, 1), tuple(2L, 2), tuple(3L, 3));
    }

    @Test
    @DisplayName("应该正确处理并列排名_当分数相同")
    void shouldHandleTiedRankings_WhenSameScore_ThenSameRank() {
        // Given: 两人并列 95，第三人 85
        List<Score> scores = Arrays.asList(
                score(1L, 1L, 1L, 1L, 95.0), score(2L, 2L, 1L, 1L, 95.0), score(3L, 3L, 1L, 1L, 85.0));
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(scores);
        when(scoreMapper.updateById(any(Score.class))).thenReturn(1);

        // When
        scoreService.calculateRankings(1L, 1L);

        // Then: 并列同名次，其后跳号 (1,1,3)，班级/年级/校级口径一致
        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper, times(3)).updateById(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Score::getId, Score::getRankInClass, Score::getRankInGrade, Score::getRankInSchool)
                .containsExactlyInAnyOrder(tuple(1L, 1, 1, 1), tuple(2L, 1, 1, 1), tuple(3L, 3, 3, 3));
    }

    @Test
    @DisplayName("应该分页查询成绩列表")
    void shouldListScores_WhenPageQuery_ThenSuccess() {
        // Given
        ScoreQueryRequest queryRequest = ScoreQueryRequest.builder()
                .examId(1L)
                .pageNum(1L)
                .pageSize(10L)
                .build();

        Page<Score> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Arrays.asList(mockScore));
        mockPage.setTotal(1);

        when(scoreMapper.selectPageByQuery(any(Page.class), any(ScoreQueryRequest.class)))
                .thenReturn(mockPage);

        // When
        Page<ScoreResponse> result = scoreService.listScores(queryRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        verify(scoreMapper).selectPageByQuery(any(Page.class), any(ScoreQueryRequest.class));
    }

    @Test
    @DisplayName("应该用学生档案ID并强制只看已发布_当学生分页查询成绩")
    void shouldListOwnPublishedScores_WhenStudent_ThenUseStudentIdDomain() {
        // Given: sys_user.id=500，student.id=42；客户端试图传别人的 studentId 与 publishedOnly=false
        loginAsStudent(42L);
        ScoreQueryRequest queryRequest = ScoreQueryRequest.builder()
                .examId(1L)
                .studentId(7L)
                .publishedOnly(false)
                .pageNum(1L)
                .pageSize(10L)
                .build();
        Page<Score> mockPage = new Page<>(1, 10);
        mockPage.setRecords(new ArrayList<>());
        mockPage.setTotal(0);
        when(scoreMapper.selectPageByQuery(any(Page.class), any(ScoreQueryRequest.class))).thenReturn(mockPage);

        // When
        scoreService.listScores(queryRequest);

        // Then: 既不是 userId(500) 也不是客户端传的 7，而是 student.id 域的 42
        ArgumentCaptor<ScoreQueryRequest> captor = ArgumentCaptor.forClass(ScoreQueryRequest.class);
        verify(scoreMapper).selectPageByQuery(any(Page.class), captor.capture());
        assertThat(captor.getValue().getStudentId()).isEqualTo(42L);
        assertThat(captor.getValue().getPublishedOnly()).isTrue();
    }

    @Test
    @DisplayName("应该清掉客户端传入的publishedOnly_当非学生分页查询成绩")
    void shouldClearPublishedOnly_WhenNonStudent_ThenIgnoreClientFlag() {
        // Given
        ScoreQueryRequest queryRequest = ScoreQueryRequest.builder()
                .examId(1L)
                .publishedOnly(true)
                .pageNum(1L)
                .pageSize(10L)
                .build();
        Page<Score> mockPage = new Page<>(1, 10);
        mockPage.setRecords(new ArrayList<>());
        mockPage.setTotal(0);
        when(scoreMapper.selectPageByQuery(any(Page.class), any(ScoreQueryRequest.class))).thenReturn(mockPage);

        // When
        scoreService.listScores(queryRequest);

        // Then
        ArgumentCaptor<ScoreQueryRequest> captor = ArgumentCaptor.forClass(ScoreQueryRequest.class);
        verify(scoreMapper).selectPageByQuery(any(Page.class), captor.capture());
        assertThat(captor.getValue().getPublishedOnly()).isNull();
    }

    @Test
    @DisplayName("应该计算年级排名_当有多个班级")
    void shouldCalculateGradeRankings_WhenMultipleClasses_ThenGradeRankCorrect() {
        // Given: 两个班各一人，98 分在班级 2
        List<Score> scores = Arrays.asList(score(1L, 1L, 1L, 1L, 95.0), score(2L, 2L, 1L, 2L, 98.0));
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(scores);
        when(scoreMapper.updateById(any(Score.class))).thenReturn(1);
        // 两个班同属年级 10：年级排名应把两班学生放在一起比
        SchoolClass class1 = new SchoolClass();
        class1.setId(1L);
        class1.setGradeId(10L);
        SchoolClass class2 = new SchoolClass();
        class2.setId(2L);
        class2.setGradeId(10L);
        when(classMapper.selectBatchIds(any())).thenReturn(List.of(class1, class2));

        // When
        scoreService.calculateRankings(1L, 1L);

        // Then: 98 分（班级2）年级第 1，95 分（班级1）年级第 2；各自班内都是第 1
        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper, times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Score::getId, Score::getRankInClass, Score::getRankInGrade, Score::getRankInSchool)
                .containsExactlyInAnyOrder(tuple(2L, 1, 1, 1), tuple(1L, 1, 2, 2));
    }

    @Test
    @DisplayName("应该忽略缺考学生_当计算排名时")
    void shouldIgnoreAbsentStudents_WhenCalculateRankings_ThenOnlyValidScores() {
        // Given: 一人有效，一人缺考
        List<Score> scores = Arrays.asList(score(1L, 1L, 1L, 1L, 95.0), score(2L, 2L, 1L, 1L, null));
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(scores);
        when(scoreMapper.updateById(any(Score.class))).thenReturn(1);

        // When
        scoreService.calculateRankings(1L, 1L);

        // Then: 只有有效成绩那一行被写，且名次为 1
        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper, times(1)).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getRankInClass()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该只写本次计算的排名列_当成绩行已带旧排名")
    void shouldNotCopyStaleRanks_WhenScoresAlreadyRanked() {
        // Given: 旧单科排名全是 9、旧总分排名全是 7，单科重算不得把旧值带回更新对象（B-1）
        Score s1 = score(1L, 1L, 1L, 1L, 95.0);
        Score s2 = score(2L, 2L, 1L, 1L, 90.0);
        for (Score sc : List.of(s1, s2)) {
            sc.setRankInClass(9);
            sc.setRankInGrade(9);
            sc.setRankInSchool(9);
            sc.setTotalRankInClass(7);
            sc.setTotalRankInGrade(7);
            sc.setTotalRankInSchool(7);
        }
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(List.of(s1, s2));
        when(scoreMapper.updateById(any(Score.class))).thenReturn(1);

        // When
        scoreService.calculateRankings(1L, 1L);

        // Then: 单科三档为新算的值
        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper, times(2)).updateById(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Score::getId, Score::getRankInClass, Score::getRankInGrade, Score::getRankInSchool)
                .containsExactlyInAnyOrder(tuple(1L, 1, 1, 1), tuple(2L, 2, 2, 2));
        // 总分列与其它业务列不在本次写入范围，更新对象里必须是 null（NOT_NULL 策略下库里值保持不变）
        assertThat(captor.getAllValues()).allSatisfy(u -> {
            assertThat(u.getTotalRankInClass()).isNull();
            assertThat(u.getTotalRankInGrade()).isNull();
            assertThat(u.getTotalRankInSchool()).isNull();
            assertThat(u.getScore()).isNull();
            assertThat(u.getStatus()).isNull();
            assertThat(u.getStudentId()).isNull();
        });
    }

    @Test
    @DisplayName("应该只写总分排名_当未指定科目")
    void shouldWriteTotalRanksOnly_WhenSubjectIdNull() {
        // Given: 学生1 两科 90+80=170，学生2 两科 95+60=155，学生3 一科 100 另一科缺考（缺考不计入总分）
        List<Score> scores = Arrays.asList(
                score(1L, 1L, 1L, 1L, 90.0), score(2L, 1L, 2L, 1L, 80.0),
                score(3L, 2L, 1L, 1L, 95.0), score(4L, 2L, 2L, 1L, 60.0),
                score(5L, 3L, 1L, 1L, 100.0), score(6L, 3L, 2L, 1L, null));
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(scores);
        when(scoreMapper.updateById(any(Score.class))).thenReturn(1);

        // When
        scoreService.calculateRankings(1L, null);

        // Then: 6 行各写一次（缺考行也带该生总分名次），总分名次 170→1、155→2、100→3，同一学生所有行相同
        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper, times(6)).updateById(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(Score::getId, Score::getTotalRankInClass, Score::getTotalRankInGrade, Score::getTotalRankInSchool)
                .containsExactlyInAnyOrder(
                        tuple(1L, 1, 1, 1), tuple(2L, 1, 1, 1),
                        tuple(3L, 2, 2, 2), tuple(4L, 2, 2, 2),
                        tuple(5L, 3, 3, 3), tuple(6L, 3, 3, 3));
        assertThat(captor.getAllValues()).allSatisfy(u -> {
            assertThat(u.getRankInClass()).isNull();
            assertThat(u.getRankInGrade()).isNull();
            assertThat(u.getRankInSchool()).isNull();
        });
    }

    @Test
    @DisplayName("应该一次写入单科与总分六档排名_当计算全场排名")
    void shouldWriteSubjectAndTotalRanksOnce_WhenCalculateAllRankings() {
        // Given: 两班同年级、两科；学生1 在班 1 总分 170，学生2 在班 2 总分 155
        List<Score> scores = Arrays.asList(
                score(1L, 1L, 1L, 1L, 90.0), score(2L, 1L, 2L, 1L, 80.0),
                score(3L, 2L, 1L, 2L, 95.0), score(4L, 2L, 2L, 2L, 60.0));
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(scores);
        when(scoreMapper.updateById(any(Score.class))).thenReturn(1);
        SchoolClass class1 = new SchoolClass();
        class1.setId(1L);
        class1.setGradeId(10L);
        SchoolClass class2 = new SchoolClass();
        class2.setId(2L);
        class2.setGradeId(10L);
        when(classMapper.selectBatchIds(any())).thenReturn(List.of(class1, class2));

        // When
        scoreService.calculateAllRankings(1L);

        // Then: 4 行各写一次
        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper, times(4)).updateById(captor.capture());
        assertThat(captor.getAllValues()).extracting(Score::getId).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
        // 单科：科目1 95>90，科目2 80>60；每班各一人故班排都是 1
        assertThat(captor.getAllValues())
                .extracting(Score::getId, Score::getRankInClass, Score::getRankInGrade, Score::getRankInSchool)
                .containsExactlyInAnyOrder(tuple(1L, 1, 2, 2), tuple(2L, 1, 1, 1), tuple(3L, 1, 1, 1), tuple(4L, 1, 2, 2));
        // 总分：学生1 170 第 1，学生2 155 第 2；同一学生所有行相同
        assertThat(captor.getAllValues())
                .extracting(Score::getId, Score::getTotalRankInClass, Score::getTotalRankInGrade, Score::getTotalRankInSchool)
                .containsExactlyInAnyOrder(tuple(1L, 1, 1, 1), tuple(2L, 1, 1, 1), tuple(3L, 1, 2, 2), tuple(4L, 1, 2, 2));
    }

    @Test
    @DisplayName("应该按考试归属学校解析三元组_当超管审核")
    void shouldResolveTripletBySchoolOfExam_WhenSuperAdminReviews() {
        // Given: 超管无学校归属，三元组路径要按 exam.school_id 定位（B-2）
        TestAuth.asSuperAdmin();
        Score pending = new Score();
        pending.setId(1L);
        pending.setSubjectId(1L);
        pending.setStatus(0);
        ScoreReviewRequest reviewRequest = new ScoreReviewRequest();
        reviewRequest.setExamId(1L);
        reviewRequest.setClassId(1L);
        reviewRequest.setSubjectId(1L);
        reviewRequest.setResult(1);

        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(scoreMapper.selectByExamId(1L, 1L, 1L)).thenReturn(List.of(pending));
        when(scoreMapper.selectById(1L)).thenReturn(mockScore);
        when(scoreMapper.updateById(any(Score.class))).thenReturn(1);

        // When
        scoreService.reviewScores(reviewRequest);

        // Then
        ArgumentCaptor<Score> captor = ArgumentCaptor.forClass(Score.class);
        verify(scoreMapper).updateById(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该返回自己的成绩_当学生查询已发布考试的成绩详情")
    void shouldGetScoreById_WhenStudentOwnsPublishedScore() {
        // Given
        loginAsStudent(1L);
        mockExam.setStatus(3);
        when(scoreMapper.selectById(1L)).thenReturn(mockScore);
        when(examMapper.selectById(1L)).thenReturn(mockExam);

        // When
        ScoreResponse response = scoreService.getScoreById(1L);

        // Then
        assertThat(response.getStudentId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该拒绝_当学生按ID查询他人成绩")
    void shouldThrowException_WhenStudentReadsOthersScoreById() {
        // Given: 学生档案 42，成绩属于学生 1
        loginAsStudent(42L);
        when(scoreMapper.selectById(1L)).thenReturn(mockScore);

        // When & Then
        assertThatThrownBy(() -> scoreService.getScoreById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学生只能查询自己的成绩");
        verify(examMapper, never()).selectById(anyLong());
    }

    @Test
    @DisplayName("应该拒绝_当学生查询未发布考试的成绩详情")
    void shouldThrowException_WhenStudentReadsUnpublishedScoreById() {
        // Given: 考试 1 仍在录入中
        loginAsStudent(1L);
        when(scoreMapper.selectById(1L)).thenReturn(mockScore);
        when(examMapper.selectById(1L)).thenReturn(mockExam);

        // When & Then
        assertThatThrownBy(() -> scoreService.getScoreById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未发布");
    }

    @Test
    @DisplayName("应该拒绝_当教师按ID查询非任教班级科目的成绩")
    void shouldThrowException_WhenTeacherReadsScoreOutOfScopeById() {
        // Given
        loginAsTeacher(7L);
        when(scoreMapper.selectById(1L)).thenReturn(mockScore);
        when(teacherScopeService.isAllowedClassSubject(1L, 1L)).thenReturn(false);

        // When & Then
        assertThatThrownBy(() -> scoreService.getScoreById(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问该班级或科目的成绩");
    }

    @Test
    @DisplayName("应该直接上抛_当批量录入遇到非业务异常")
    void shouldPropagate_WhenBatchInputHitsNonBusinessException() {
        // Given: SQL 级异常不能被按条吞掉（B-7），否则 PG 事务已 aborted 还继续循环
        batchRequest = new ScoreBatchInputRequest();
        batchRequest.setExamId(1L);
        ScoreBatchInputRequest.ScoreItem item = new ScoreBatchInputRequest.ScoreItem();
        item.setStudentId(1L);
        item.setSubjectId(1L);
        item.setClassId(1L);
        item.setScore(BigDecimal.valueOf(60.0));
        batchRequest.setScores(List.of(item));

        stubInputTargetValid();
        when(studentMapper.selectById(1L)).thenReturn(student(1L, 1L));
        when(scoreMapper.selectByExamStudentSubject(1L, 1L, 1L, 1L)).thenReturn(null);
        when(scoreMapper.insert(any(Score.class))).thenThrow(new RuntimeException("value too long for type"));

        // When & Then
        assertThatThrownBy(() -> scoreService.batchInputScores(batchRequest))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("value too long");
    }

    @Test
    @DisplayName("应该返回全部科目的成绩项_当班级成绩列表未指定科目")
    void shouldReturnAllSubjects_WhenClassScoreListSubjectIdNull() {
        // Given: 学生 1 两科有成绩，学生 2 无成绩
        when(classStudentMapper.selectStudentIdsByClassId(1L)).thenReturn(List.of(1L, 2L));
        when(studentMapper.selectBatchIds(any())).thenReturn(List.of(student(1L, 1L), student(2L, 1L)));
        when(scoreMapper.selectByExamId(1L, 1L, 1L)).thenReturn(List.of(
                score(1L, 1L, 1L, 1L, 90.0), score(2L, 1L, 2L, 1L, 80.0)));
        Subject math = new Subject();
        math.setId(1L);
        math.setSubjectName("数学");
        Subject chinese = new Subject();
        chinese.setId(2L);
        chinese.setSubjectName("语文");
        when(subjectMapper.selectBatchIds(any())).thenReturn(List.of(math, chinese));

        // When
        List<ClassScoreItemDto> items = scoreService.getClassScoreList(1L, 1L, null);

        // Then: 学生 1 两科两行，学生 2 一行占位
        assertThat(items)
                .extracting(ClassScoreItemDto::getStudentId, ClassScoreItemDto::getSubjectId, ClassScoreItemDto::getSubjectName)
                .containsExactly(tuple(1L, 1L, "数学"), tuple(1L, 2L, "语文"), tuple(2L, null, null));
    }

    @Test
    @DisplayName("应该走SQL聚合分页_当查询审核列表")
    void shouldUseSqlAggregation_WhenGetReviewList() {
        // Given: 聚合 SQL 返回一组
        Map<String, Object> row = new HashMap<>();
        row.put("id", 11L);
        row.put("examId", 1L);
        row.put("examName", "期中考试");
        row.put("classId", 1L);
        row.put("className", "一班");
        row.put("subjectId", 1L);
        row.put("subjectName", "数学");
        row.put("fullScore", BigDecimal.valueOf(120));
        row.put("status", 0);
        row.put("enteredCount", 30L);
        row.put("recorderId", 9L);
        row.put("inputTime", Timestamp.valueOf("2026-03-01 09:00:00"));
        row.put("reviewTime", null);
        when(scoreMapper.selectReviewListCount(1L, null, null, 1L, 0)).thenReturn(1L);
        when(scoreMapper.selectReviewList(1L, null, null, 1L, 0, 10L, 0L)).thenReturn(List.of(row));
        SysUser recorder = new SysUser();
        recorder.setId(9L);
        recorder.setRealName("王老师");
        when(sysUserMapper.selectBatchIds(any())).thenReturn(List.of(recorder));

        // When
        Page<ReviewListItemDto> page = scoreService.getReviewList(1L, null, null, "pending", 1L, 10L);

        // Then: 不再经 selectPageByQuery（会被 maxLimit=1000 截断）
        assertThat(page.getTotal()).isEqualTo(1);
        ReviewListItemDto dto = page.getRecords().get(0);
        assertThat(dto.getId()).isEqualTo(11L);
        assertThat(dto.getStatus()).isEqualTo("pending");
        assertThat(dto.getFullScore()).isEqualTo(120.0);
        assertThat(dto.getInputPerson()).isEqualTo("王老师");
        assertThat(dto.getInputTime()).isEqualTo("2026-03-01 09:00:00");
        assertThat(dto.getReviewTime()).isNull();
        verify(scoreMapper, never()).selectPageByQuery(any(Page.class), any(ScoreQueryRequest.class));
    }
}
