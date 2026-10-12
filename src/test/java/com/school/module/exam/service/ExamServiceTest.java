package com.school.module.exam.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.school.common.exception.BusinessException;
import com.school.module.dict.entity.DictData;
import com.school.module.dict.service.DictService;
import com.school.module.exam.dto.*;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.entity.ExamSubject;
import com.school.module.exam.mapper.ExamMapper;
import com.school.module.exam.mapper.ExamSubjectMapper;
import com.school.module.grade.entity.Grade;
import com.school.module.grade.mapper.GradeMapper;
import com.school.module.notification.service.NotificationService;
import com.school.module.parent.service.ParentService;
import com.school.module.school.entity.School;
import com.school.module.school.mapper.SchoolMapper;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.module.score.service.ScoreService;
import com.school.module.student.entity.Student;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.subject.mapper.SubjectMapper;
import com.school.module.term.mapper.AcademicTermMapper;
import com.school.module.warning.service.ScoreWarningService;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exam Service Unit Tests
 * TDD Approach: Test First, Implementation Second
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("考试服务测试")
class ExamServiceTest {

    @Mock
    private ExamMapper examMapper;

    @Mock
    private ExamSubjectMapper examSubjectMapper;

    @Mock
    private SubjectMapper subjectMapper;

    @Mock
    private AcademicTermMapper termMapper;

    @Mock
    private ScoreMapper scoreMapper;

    // ponytail: ExamServiceImpl 构造器注入了下面 6 个依赖；@InjectMocks 找不到对应 @Mock 会传 null，
    // convertToResponse 里的 gradeMapper/schoolMapper 一调就 NPE。
    @Mock
    private GradeMapper gradeMapper;

    @Mock
    private SchoolMapper schoolMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private ScoreService scoreService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private ScoreWarningService scoreWarningService;

    // ponytail: getExamTypeName 改为优先读 exam_type 字典；未 stub 时 Mockito 返回空列表，自然走内置表回落
    @Mock
    private DictService dictService;

    // ponytail: notifyStudentsOfPublish 经 ParentService 扩散到家长；未 stub 时 Mockito 返回空列表，不影响既有断言
    @Mock
    private ParentService parentService;

    @InjectMocks
    private ExamServiceImpl examService;

    private ExamCreateRequest createRequest;
    private ExamUpdateRequest updateRequest;
    private Exam mockExam;
    private ExamSubject mockExamSubject;

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @BeforeEach
    void setUp() {
        TestAuth.asSchoolAdmin(1L);

        // 准备创建请求
        createRequest = new ExamCreateRequest();
        createRequest.setSchoolId(1L);
        createRequest.setTermId(1L);
        createRequest.setExamName("2024年春季期中考试");
        createRequest.setExamType(2); // 期中
        createRequest.setExamDate(LocalDate.of(2024, 4, 15));
        createRequest.setGradeIds(Arrays.asList(1L, 2L, 3L));

        ExamCreateRequest.ExamSubjectInfo subject1 = new ExamCreateRequest.ExamSubjectInfo();
        subject1.setSubjectId(1L);
        subject1.setFullScore(BigDecimal.valueOf(100));
        subject1.setSortOrder(1);

        ExamCreateRequest.ExamSubjectInfo subject2 = new ExamCreateRequest.ExamSubjectInfo();
        subject2.setSubjectId(2L);
        subject2.setFullScore(BigDecimal.valueOf(100));
        subject2.setSortOrder(2);

        createRequest.setSubjects(Arrays.asList(subject1, subject2));

        // 准备更新请求
        updateRequest = new ExamUpdateRequest();
        updateRequest.setExamName("2024年春季期中考试（修改）");
        updateRequest.setExamType(2);

        // 准备Mock考试
        mockExam = new Exam();
        mockExam.setId(1L);
        mockExam.setSchoolId(1L);
        mockExam.setTermId(1L);
        mockExam.setExamName("2024年春季期中考试");
        mockExam.setExamType(2);
        mockExam.setExamDate(LocalDate.of(2024, 4, 15));
        mockExam.setGradeIds("1,2,3");
        mockExam.setStatus(0);

        // 准备Mock考试科目
        mockExamSubject = new ExamSubject();
        mockExamSubject.setId(1L);
        mockExamSubject.setExamId(1L);
        mockExamSubject.setSubjectId(1L);
        mockExamSubject.setFullScore(BigDecimal.valueOf(100));
        mockExamSubject.setSortOrder(1);
    }

    @Test
    @DisplayName("应该成功创建考试_当数据有效")
    void shouldCreateExam_WhenValidData_ThenSuccess() {
        // Given
        when(examMapper.insert(any(Exam.class))).thenAnswer(invocation -> {
            Exam exam = invocation.getArgument(0);
            exam.setId(1L);
            return 1;
        });
        when(examSubjectMapper.insert(any(ExamSubject.class))).thenReturn(1);
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(Arrays.asList(mockExamSubject));

        // When
        ExamResponse response = examService.createExam(createRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getExamName()).isEqualTo("2024年春季期中考试");
        assertThat(response.getExamType()).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo(0);
        verify(examMapper).insert(any(Exam.class));
        verify(examSubjectMapper, times(2)).insert(any(ExamSubject.class));
    }

    @Test
    @DisplayName("应该抛出异常_当考试类型无效")
    void shouldThrowException_WhenInvalidExamType_ThenValidationFailed() {
        // Given
        createRequest.setExamType(99);

        // When & Then
        assertThatThrownBy(() -> examService.createExam(createRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("考试类型无效");

        verify(examMapper, never()).insert(any(Exam.class));
    }

    @Test
    @DisplayName("应该关联科目_当创建考试时")
    void shouldAssociateSubjects_WhenCreateExam_ThenSuccess() {
        // Given
        when(examMapper.insert(any(Exam.class))).thenAnswer(invocation -> {
            Exam exam = invocation.getArgument(0);
            exam.setId(1L);
            return 1;
        });
        when(examSubjectMapper.insert(any(ExamSubject.class))).thenReturn(1);
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(Arrays.asList(mockExamSubject));

        // When
        examService.createExam(createRequest);

        // Then
        ArgumentCaptor<ExamSubject> captor = ArgumentCaptor.forClass(ExamSubject.class);
        verify(examSubjectMapper, times(2)).insert(captor.capture());
        List<ExamSubject> captured = captor.getAllValues();
        assertThat(captured).hasSize(2);
        assertThat(captured.get(0).getSubjectId()).isEqualTo(1L);
        assertThat(captured.get(1).getSubjectId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("应该验证日期_当创建考试时")
    void shouldValidateDate_WhenCreateExam_ThenSuccess() {
        // Given
        createRequest.setExamDate(LocalDate.now().plusDays(30));
        when(examMapper.insert(any(Exam.class))).thenAnswer(invocation -> {
            Exam exam = invocation.getArgument(0);
            exam.setId(1L);
            return 1;
        });
        when(examSubjectMapper.insert(any(ExamSubject.class))).thenReturn(1);
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(Arrays.asList(mockExamSubject));

        // When
        ExamResponse response = examService.createExam(createRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getExamDate()).isAfter(LocalDate.now());
    }

    @Test
    @DisplayName("应该更新考试信息_当考试未开始")
    void shouldUpdateExam_WhenNotStarted_ThenSuccess() {
        // Given
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examMapper.updateById(any(Exam.class))).thenReturn(1);
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(Arrays.asList(mockExamSubject));

        // When
        ExamResponse response = examService.updateExam(1L, updateRequest);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getExamName()).isEqualTo("2024年春季期中考试（修改）");
        verify(examMapper).selectById(1L);
        verify(examMapper).updateById(any(Exam.class));
    }

    @Test
    @DisplayName("应该抛出异常_当更新已开始的考试")
    void shouldThrowException_WhenUpdateStartedExam_ThenNotAllowed() {
        // Given
        mockExam.setStatus(1); // 录入中
        when(examMapper.selectById(1L)).thenReturn(mockExam);

        // When & Then
        assertThatThrownBy(() -> examService.updateExam(1L, updateRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能修改未开始的考试");

        verify(examMapper, never()).updateById(any(Exam.class));
    }

    @Test
    @DisplayName("应该更新状态为录入中_当开始录入成绩")
    void shouldUpdateStatusToInputting_WhenStartScoreInput_ThenSuccess() {
        // Given
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examMapper.updateById(any(Exam.class))).thenReturn(1);

        // When
        examService.startScoreInput(1L);

        // Then
        ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
        verify(examMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该更新状态为审核中_当提交审核")
    void shouldUpdateStatusToReviewing_WhenSubmitForReview_ThenSuccess() {
        // Given
        mockExam.setStatus(1); // 录入中
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examMapper.updateById(any(Exam.class))).thenReturn(1);

        // When
        examService.submitForReview(1L);

        // Then
        ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
        verify(examMapper, atLeastOnce()).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(2);
    }

    @Test
    @DisplayName("应该更新状态为已发布_当发布成绩")
    void shouldUpdateStatusToPublished_WhenPublishExam_ThenSuccess() {
        // Given
        mockExam.setStatus(2); // 审核中
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examMapper.updateById(any(Exam.class))).thenReturn(1);

        // When
        examService.publishExam(1L);

        // Then
        ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
        verify(examMapper, atLeastOnce()).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(3);
    }

    @Test
    @DisplayName("应该拒绝非法状态转换_当状态不符合流程")
    void shouldRejectInvalidStatusTransition_WhenWrongState_ThenNotAllowed() {
        // Given - 考试状态为未开始，直接发布
        mockExam.setStatus(0);
        when(examMapper.selectById(1L)).thenReturn(mockExam);

        // When & Then
        assertThatThrownBy(() -> examService.publishExam(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能发布审核中的考试");

        verify(examMapper, never()).updateById(any(Exam.class));
    }

    @Test
    @DisplayName("应该检查前置条件_当开始录入成绩")
    void shouldCheckPreconditions_WhenStartScoreInput_ThenValidate() {
        // Given
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examMapper.updateById(any(Exam.class))).thenReturn(1);

        // When
        examService.startScoreInput(1L);

        // Then
        verify(examMapper).selectById(1L);
        verify(examMapper).updateById(any(Exam.class));
    }

    @Test
    @DisplayName("应该检查成绩完整性_当提交审核")
    void shouldCheckScoreCompleteness_WhenSubmitForReview_ThenValidate() {
        // Given
        mockExam.setStatus(1);
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examMapper.updateById(any(Exam.class))).thenReturn(1);

        // When
        examService.submitForReview(1L);

        // Then
        verify(examMapper, atLeastOnce()).selectById(1L);
        verify(examMapper, atLeastOnce()).updateById(any(Exam.class));
    }

    @Test
    @DisplayName("应该自动计算排名_当发布成绩时")
    void shouldCalculateRankings_WhenPublishExam_ThenAutoCalculate() {
        // Given
        mockExam.setStatus(2);
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examMapper.updateById(any(Exam.class))).thenReturn(1);

        // When
        examService.publishExam(1L);

        // Then
        verify(examMapper, atLeastOnce()).selectById(1L);
        verify(examMapper, atLeastOnce()).updateById(any(Exam.class));
    }

    @Test
    @DisplayName("应该删除考试_当考试未开始")
    void shouldDeleteExam_WhenNotStarted_ThenSuccess() {
        // Given
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examSubjectMapper.deleteByExamId(1L)).thenReturn(2);
        when(examMapper.deleteById(1L)).thenReturn(1);

        // When
        examService.deleteExam(1L);

        // Then
        verify(examMapper).selectById(1L);
        verify(examSubjectMapper).deleteByExamId(1L);
        verify(examMapper).deleteById(1L);
    }

    @Test
    @DisplayName("应该级联删除科目_当删除考试时")
    void shouldCascadeDeleteSubjects_WhenDeleteExam_ThenSuccess() {
        // Given
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examSubjectMapper.deleteByExamId(1L)).thenReturn(2);
        when(examMapper.deleteById(1L)).thenReturn(1);

        // When
        examService.deleteExam(1L);

        // Then
        verify(examSubjectMapper).deleteByExamId(1L);
        verify(examMapper).deleteById(1L);
    }

    @Test
    @DisplayName("应该抛出异常_当删除已开始的考试")
    void shouldThrowException_WhenDeleteStartedExam_ThenNotAllowed() {
        // Given
        mockExam.setStatus(1); // 录入中
        when(examMapper.selectById(1L)).thenReturn(mockExam);

        // When & Then
        assertThatThrownBy(() -> examService.deleteExam(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能删除未开始的考试");

        verify(examMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("应该分页查询考试列表")
    void shouldListExams_WhenPageQuery_ThenSuccess() {
        // Given
        ExamQueryRequest queryRequest = new ExamQueryRequest();
        queryRequest.setPageNum(1L);
        queryRequest.setPageSize(10L);

        Page<Exam> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Arrays.asList(mockExam));
        mockPage.setTotal(1);

        when(examMapper.selectPageByQuery(any(Page.class), any(ExamQueryRequest.class)))
                .thenReturn(mockPage);
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(Arrays.asList(mockExamSubject));

        // When
        Page<ExamResponse> result = examService.listExams(queryRequest);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords()).hasSize(1);
        verify(examMapper).selectPageByQuery(any(Page.class), any(ExamQueryRequest.class));
    }

    @Test
    @DisplayName("应该查询考试详情_当考试存在")
    void shouldGetExamById_WhenExamExists_ThenSuccess() {
        // Given
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(Arrays.asList(mockExamSubject));

        // When
        ExamResponse response = examService.getExamById(1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getExamName()).isEqualTo("2024年春季期中考试");
        verify(examMapper).selectById(1L);
        verify(examSubjectMapper).selectByExamId(1L);
    }

    @Test
    @DisplayName("应该抛出异常_当查询不存在的考试")
    void shouldThrowException_WhenGetNonexistentExam_ThenNotFound() {
        // Given
        when(examMapper.selectById(999L)).thenReturn(null);

        // When & Then
        assertThatThrownBy(() -> examService.getExamById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("考试不存在");

        verify(examMapper).selectById(999L);
    }

    @Test
    @DisplayName("应该优先用字典标签作为考试类型名_当 exam_type 字典含该值")
    void shouldUseDictLabel_WhenExamTypeDictHasValue_ThenPreferDict() {
        // Given
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(Arrays.asList(mockExamSubject));
        DictData midterm = new DictData();
        midterm.setDictType("exam_type");
        midterm.setDictValue("2");
        midterm.setDictLabel("期中考试");
        when(dictService.getDictDataByType("exam_type")).thenReturn(List.of(midterm));

        // When
        ExamResponse response = examService.getExamById(1L);

        // Then
        assertThat(response.getExamTypeName()).isEqualTo("期中考试");
        verify(dictService).getDictDataByType("exam_type");
    }

    @Test
    @DisplayName("应该回落内置名称_当 exam_type 字典没有该值")
    void shouldFallbackToBuiltinName_WhenExamTypeDictMissing_ThenUseSwitch() {
        // Given
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(Arrays.asList(mockExamSubject));
        when(dictService.getDictDataByType("exam_type")).thenReturn(Collections.emptyList());

        // When
        ExamResponse response = examService.getExamById(1L);

        // Then
        assertThat(response.getExamTypeName()).isEqualTo("期中");
    }

    @Test
    @DisplayName("应该更新考试状态_当状态值有效")
    void shouldUpdateExamStatus_WhenValidStatus_ThenSuccess() {
        // Given
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examMapper.updateById(any(Exam.class))).thenReturn(1);

        // When
        examService.updateExamStatus(1L, 1);

        // Then
        ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
        verify(examMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该抛出异常_当状态值无效")
    void shouldThrowException_WhenInvalidStatus_ThenValidationFailed() {
        // When & Then
        assertThatThrownBy(() -> examService.updateExamStatus(1L, 99))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态值无效");

        verify(examMapper, never()).updateById(any(Exam.class));
    }

    @Test
    @DisplayName("应该解析年级ID_当存储为JSON数组格式")
    void shouldParseGradeIds_WhenJsonArrayFormat_ThenSuccess() {
        // Given - 种子数据写入的是 [40,41,42] 而非 40,41,42
        mockExam.setGradeIds("[40,41,42]");
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(Arrays.asList(mockExamSubject));

        // When
        ExamResponse response = examService.getExamById(1L);

        // Then
        assertThat(response.getGradeIds()).containsExactly(40L, 41L, 42L);
    }

    @Test
    @DisplayName("应该返回空年级列表_当年级ID为空白")
    void shouldReturnEmptyGradeIds_WhenBlank_ThenNoError() {
        // Given
        mockExam.setGradeIds("");
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(Arrays.asList(mockExamSubject));

        // When
        ExamResponse response = examService.getExamById(1L);

        // Then
        assertThat(response.getGradeIds()).isEmpty();
    }

    @Test
    @DisplayName("应该抛出异常_当提交非录入中的考试")
    void shouldThrowException_WhenSubmitNonInputtingExam_ThenNotAllowed() {
        // Given
        mockExam.setStatus(0); // 未开始
        when(examMapper.selectById(1L)).thenReturn(mockExam);

        // When & Then
        assertThatThrownBy(() -> examService.submitForReview(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("只能提交录入中的考试");
    }

    // ---------- 发布闭环 ----------

    @Test
    @DisplayName("应该拒绝发布_当仍有待审核成绩")
    void shouldRejectPublish_WhenPendingScoresExist_ThenThrow() {
        // Given
        mockExam.setStatus(2);
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(List.of(score(10L, 0)));

        // When & Then
        assertThatThrownBy(() -> examService.publishExam(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待审核");

        verify(examMapper, never()).updateById(any(Exam.class));
        verify(scoreService, never()).calculateAllRankings(anyLong());
        verify(notificationService, never()).sendScorePublishNotification(anyLong(), anyList());
        verify(scoreWarningService, never()).checkScoreWarnings(anyLong());
    }

    @Test
    @DisplayName("应该拒绝发布_当仍有已驳回成绩")
    void shouldRejectPublish_WhenRejectedScoresExist_ThenThrow() {
        // Given - 1 条已通过、1 条已驳回、1 条待审核；文案须分别列出待审核与已驳回条数
        mockExam.setStatus(2);
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(scoreMapper.selectByExamId(1L, 1L, null))
                .thenReturn(List.of(score(10L, 1), score(11L, 2), score(12L, 0)));

        // When & Then
        assertThatThrownBy(() -> examService.publishExam(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("1 条成绩待审核")
                .hasMessageContaining("1 条成绩已驳回");

        verify(examMapper, never()).updateById(any(Exam.class));
        verify(scoreService, never()).calculateAllRankings(anyLong());
        verify(notificationService, never()).sendScorePublishNotification(anyLong(), anyList());
    }

    @Test
    @DisplayName("应该重算排名并通知预警_当发布成绩时")
    void shouldRankNotifyAndWarn_WhenPublishExam_ThenPipelineRuns() {
        // Given - 学生 11 未开通账号（userId 为空），不应出现在通知名单里
        mockExam.setStatus(2);
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examMapper.updateById(any(Exam.class))).thenReturn(1);
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(List.of(score(10L, 1), score(11L, 1)));
        when(studentMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(student(10L, 1001L), student(11L, null)));

        // When
        examService.publishExam(1L);

        // Then
        ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
        verify(examMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(3);
        // ponytail: 契约 R——发布改调一次性全量重算，不再按科目逐个调用 calculateRankings
        verify(scoreService).calculateAllRankings(1L);
        verify(scoreService, never()).calculateRankings(anyLong(), any());
        verify(notificationService).sendScorePublishNotification(1L, List.of(1001L));
        verify(scoreWarningService).checkScoreWarnings(1L);
    }

    @Test
    @DisplayName("应该同时通知家长_当发布成绩且学生已绑定家长时")
    void shouldNotifyParentsToo_WhenPublishExam_ThenFanOutToBoundParents() {
        // Given - 学生 10 的账号 1001、家长 2001；学生 11 未开通账号但有家长 2002；2001 同时也是某学生账号（去重）
        mockExam.setStatus(2);
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examMapper.updateById(any(Exam.class))).thenReturn(1);
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(List.of(score(10L, 1), score(11L, 1)));
        when(studentMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(student(10L, 1001L), student(11L, null)));
        when(parentService.parentUserIdsOf(anyCollection())).thenReturn(List.of(2001L, 2002L, 1001L));

        // When
        examService.publishExam(1L);

        // Then: 学生在前、家长在后，重复的 1001 只出现一次
        verify(notificationService).sendScorePublishNotification(1L, List.of(1001L, 2001L, 2002L));
        verify(scoreWarningService).checkScoreWarnings(1L);
    }

    // ---------- 状态流转 ----------

    @Test
    @DisplayName("应该允许管理员回退一步_当撤回发布")
    void shouldRollbackOneStep_WhenAdminWithdrawsPublish_ThenSuccess() {
        // Given
        mockExam.setStatus(3);
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examMapper.updateById(any(Exam.class))).thenReturn(1);

        // When
        examService.updateExamStatus(1L, 2);

        // Then
        ArgumentCaptor<Exam> captor = ArgumentCaptor.forClass(Exam.class);
        verify(examMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(2);
    }

    @Test
    @DisplayName("应该拒绝通过状态接口发布_当审核中直接改为已发布")
    void shouldRejectPublishViaStatusUpdate_WhenReviewing_ThenThrow() {
        // Given - 2→3 只能走 publishExam 闭环，PATCH /status 不放行
        mockExam.setStatus(2);
        when(examMapper.selectById(1L)).thenReturn(mockExam);

        // When & Then
        assertThatThrownBy(() -> examService.updateExamStatus(1L, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("发布接口");

        verify(examMapper, never()).updateById(any(Exam.class));
        verify(scoreService, never()).calculateAllRankings(anyLong());
    }

    @Test
    @DisplayName("应该拒绝跨级跳转_当已发布直接改为未开始")
    void shouldRejectStatusJump_WhenSkippingSteps_ThenThrow() {
        // Given
        mockExam.setStatus(3);
        when(examMapper.selectById(1L)).thenReturn(mockExam);

        // When & Then
        assertThatThrownBy(() -> examService.updateExamStatus(1L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许");

        verify(examMapper, never()).updateById(any(Exam.class));
    }

    @Test
    @DisplayName("应该拒绝回退到未开始_当录入中")
    void shouldRejectRollbackToNotStarted_WhenInputting_ThenThrow() {
        // Given
        mockExam.setStatus(1);
        when(examMapper.selectById(1L)).thenReturn(mockExam);

        // When & Then
        assertThatThrownBy(() -> examService.updateExamStatus(1L, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不允许");

        verify(examMapper, never()).updateById(any(Exam.class));
    }

    // ---------- 科目差集同步 ----------

    @Test
    @DisplayName("应该按差集同步科目_当更新考试科目")
    void shouldSyncSubjectsByDiff_WhenUpdateExamSubjects_ThenOnlyChangedRowsTouched() {
        // Given - 现有科目 1、2；请求保留 1（满分改 120）、移除 2、新增 3
        ExamSubject existing2 = new ExamSubject();
        existing2.setId(2L);
        existing2.setExamId(1L);
        existing2.setSubjectId(2L);
        existing2.setFullScore(BigDecimal.valueOf(100));
        existing2.setSortOrder(2);

        ExamUpdateRequest.ExamSubjectInfo keep1 = new ExamUpdateRequest.ExamSubjectInfo();
        keep1.setSubjectId(1L);
        keep1.setFullScore(BigDecimal.valueOf(120));
        keep1.setSortOrder(1);
        ExamUpdateRequest.ExamSubjectInfo add3 = new ExamUpdateRequest.ExamSubjectInfo();
        add3.setSubjectId(3L);
        add3.setFullScore(BigDecimal.valueOf(100));
        add3.setSortOrder(3);
        updateRequest.setSubjects(Arrays.asList(keep1, add3));

        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examMapper.updateById(any(Exam.class))).thenReturn(1);
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(Arrays.asList(mockExamSubject, existing2));

        // When
        examService.updateExam(1L, updateRequest);

        // Then - 不再整体软删重插
        verify(examSubjectMapper, never()).deleteByExamId(anyLong());

        ArgumentCaptor<ExamSubject> updatedCaptor = ArgumentCaptor.forClass(ExamSubject.class);
        verify(examSubjectMapper).updateById(updatedCaptor.capture());
        assertThat(updatedCaptor.getValue().getId()).isEqualTo(1L);
        assertThat(updatedCaptor.getValue().getFullScore()).isEqualByComparingTo(BigDecimal.valueOf(120));

        ArgumentCaptor<ExamSubject> insertedCaptor = ArgumentCaptor.forClass(ExamSubject.class);
        verify(examSubjectMapper).insert(insertedCaptor.capture());
        assertThat(insertedCaptor.getValue().getSubjectId()).isEqualTo(3L);

        verify(examSubjectMapper).deleteById(2L);
    }

    @Test
    @DisplayName("应该抛出异常_当更新请求中科目重复")
    void shouldThrowException_WhenDuplicateSubjectsInUpdate_ThenRejected() {
        // Given
        ExamUpdateRequest.ExamSubjectInfo dup1 = new ExamUpdateRequest.ExamSubjectInfo();
        dup1.setSubjectId(1L);
        ExamUpdateRequest.ExamSubjectInfo dup2 = new ExamUpdateRequest.ExamSubjectInfo();
        dup2.setSubjectId(1L);
        updateRequest.setSubjects(Arrays.asList(dup1, dup2));

        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examMapper.updateById(any(Exam.class))).thenReturn(1);
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(Arrays.asList(mockExamSubject));

        // When & Then
        assertThatThrownBy(() -> examService.updateExam(1L, updateRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("科目重复");

        verify(examSubjectMapper, never()).insert(any(ExamSubject.class));
    }

    // ---------- 响应回填 ----------

    @Test
    @DisplayName("应该回填年级名与学校名_当查询考试详情")
    void shouldFillGradeNamesAndSchoolName_WhenGetExamById_ThenPopulated() {
        // Given - 年级 2 已不存在，应跳过而非留 null；顺序按 gradeIds
        when(examMapper.selectById(1L)).thenReturn(mockExam);
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(Arrays.asList(mockExamSubject));
        when(gradeMapper.selectBatchIds(anyCollection()))
                .thenReturn(Arrays.asList(grade(3L, "三年级"), grade(1L, "一年级")));
        when(schoolMapper.selectBatchIds(anyCollection())).thenReturn(List.of(school(1L, "实验中学")));

        // When
        ExamResponse response = examService.getExamById(1L);

        // Then
        assertThat(response.getGradeNames()).containsExactly("一年级", "三年级");
        assertThat(response.getSchoolName()).isEqualTo("实验中学");
    }

    // ---------- 测试数据工厂 ----------

    private Score score(Long studentId, int status) {
        Score s = new Score();
        s.setExamId(1L);
        s.setStudentId(studentId);
        s.setSubjectId(1L);
        s.setStatus(status);
        return s;
    }

    private Student student(Long id, Long userId) {
        Student s = new Student();
        s.setId(id);
        s.setUserId(userId);
        return s;
    }

    private Grade grade(Long id, String name) {
        Grade g = new Grade();
        g.setId(id);
        g.setGradeName(name);
        return g;
    }

    private School school(Long id, String name) {
        School s = new School();
        s.setId(id);
        s.setSchoolName(name);
        return s;
    }
}
