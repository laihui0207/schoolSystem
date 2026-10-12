package com.school.module.stat.service;

import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.school.module.class_module.entity.SchoolClass;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.entity.ExamSubject;
import com.school.module.exam.mapper.ExamSubjectMapper;
import com.school.module.grade.entity.Grade;
import com.school.module.score.entity.Score;
import com.school.module.score.mapper.ScoreMapper;
import com.school.module.student.entity.Student;
import com.school.module.subject.entity.Subject;
import com.school.module.teacher.entity.TeacherClass;
import com.school.module.user.enums.UserRole;
import com.school.security.model.LoginUser;
import com.school.security.service.TeacherScopeService;
import com.school.module.subject.mapper.SubjectMapper;
import com.school.module.student.mapper.StudentMapper;
import com.school.module.grade.mapper.GradeMapper;
import com.school.module.exam.mapper.ExamMapper;
import com.school.module.class_module.mapper.ClassMapper;
import com.school.module.stat.dto.*;
import com.school.support.TestAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Stat Service Unit Tests
 * TDD Approach: Test First, Implementation Second
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("统计服务测试")
class StatServiceTest {

    @Mock
    private ScoreMapper scoreMapper;

    @Mock
    private ClassMapper classMapper;

    @Mock
    private SubjectMapper subjectMapper;

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private ExamMapper examMapper;

    // ponytail: StatServiceImpl 新注入 ExamSubjectMapper（读 exam_subject.full_score），不加 @Mock 会被注入 null 导致 NPE
    @Mock
    private ExamSubjectMapper examSubjectMapper;

    @Mock
    private GradeMapper gradeMapper;

    @Mock
    private TeacherScopeService teacherScopeService;

    @InjectMocks
    private StatServiceImpl statService;

    private Score score1;
    private Score score2;
    private Score score3;
    private List<Score> mockScores;

    @AfterEach
    void tearDown() {
        TestAuth.clear();
    }

    @BeforeEach
    void setUp() {
        TestAuth.asSchoolAdmin(1L);

        // 准备学生1的成绩
        score1 = new Score();
        score1.setId(1L);
        score1.setExamId(1L);
        score1.setStudentId(1L);
        score1.setSubjectId(1L);
        score1.setClassId(1L);
        score1.setSchoolId(1L);
        score1.setScore(BigDecimal.valueOf(95.0));
        score1.setIsAbsent(0);
        score1.setRankInClass(1);
        score1.setRankInGrade(1);
        score1.setStatus(1);

        score2 = new Score();
        score2.setId(2L);
        score2.setExamId(1L);
        score2.setStudentId(1L);
        score2.setSubjectId(2L);
        score2.setClassId(1L);
        score2.setSchoolId(1L);
        score2.setScore(BigDecimal.valueOf(90.0));
        score2.setIsAbsent(0);
        score2.setRankInClass(2);
        score2.setRankInGrade(3);
        score2.setStatus(1);

        // 准备学生2的成绩
        score3 = new Score();
        score3.setId(3L);
        score3.setExamId(1L);
        score3.setStudentId(2L);
        score3.setSubjectId(1L);
        score3.setClassId(1L);
        score3.setSchoolId(1L);
        score3.setScore(BigDecimal.valueOf(85.0));
        score3.setIsAbsent(0);
        score3.setRankInClass(3);
        score3.setRankInGrade(5);
        score3.setStatus(1);

        mockScores = Arrays.asList(score1, score2, score3);
    }

    @Test
    @DisplayName("应该获取个人成绩统计_当成绩存在")
    void shouldGetPersonalStat_WhenScoresExist_ThenSuccess() {
        // Given
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(mockScores);

        // When
        PersonalStatResponse response = statService.getPersonalStat(1L, 1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStudentId()).isEqualTo(1L);
        assertThat(response.getExamId()).isEqualTo(1L);
        assertThat(response.getTotalScore()).isEqualTo(BigDecimal.valueOf(185.0));
        assertThat(response.getAverageScore()).isEqualByComparingTo("92.50");
        assertThat(response.getSubjectScores()).hasSize(2);
        verify(scoreMapper).selectByExamId(1L, 1L, null);
    }

    @Test
    @DisplayName("应该填充骨架字段并按总分现算名次_当个人统计时")
    void shouldFillSkeletonFieldsAndTotalRank_WhenPersonalStat_ThenComplete() {
        // Given
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(mockScores);
        Student student = new Student();
        student.setId(1L);
        student.setName("张三");
        student.setStudentNo("S001");
        when(studentMapper.selectById(1L)).thenReturn(student);
        Exam exam = new Exam();
        exam.setId(1L);
        exam.setExamName("期中考试");
        when(examMapper.selectById(1L)).thenReturn(exam);
        when(subjectMapper.selectBatchIds(anyCollection()))
                .thenReturn(Arrays.asList(subject(1L, "语文", 100, 60), subject(2L, "数学", 150, 90)));
        // exam_subject 对数学单独设定满分 120，应优先于 subject.full_score 的 150
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(Collections.singletonList(examSubject(2L, 120)));
        when(classMapper.selectBatchIds(anyCollection())).thenReturn(Collections.singletonList(schoolClass(1L, 1L)));

        // When
        PersonalStatResponse response = statService.getPersonalStat(1L, 1L);

        // Then
        assertThat(response.getStudentName()).isEqualTo("张三");
        assertThat(response.getStudentNo()).isEqualTo("S001");
        assertThat(response.getExamName()).isEqualTo("期中考试");
        assertThat(response.getTotalFullScore()).isEqualByComparingTo("220");
        assertThat(response.getSubjectScores()).extracting(PersonalStatResponse.SubjectScore::getSubjectName)
                .containsExactly("语文", "数学");
        assertThat(response.getSubjectScores().get(1).getFullScore()).isEqualByComparingTo("120");
        // 学生1 总分 185 > 学生2 总分 85，同班同年级，名次都是 1（不再取第一条科目行的单科名次）
        assertThat(response.getRankInClass()).isEqualTo(1);
        assertThat(response.getRankInGrade()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该抛出异常_当学生成绩不存在")
    void shouldThrowException_WhenNoScores_ThenNotFound() {
        // Given
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(Collections.emptyList());

        // When & Then
        assertThatThrownBy(() -> statService.getPersonalStat(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到学生成绩");

        verify(scoreMapper).selectByExamId(1L, 1L, null);
    }

    @Test
    @DisplayName("应该计算平均分准确性_当有多科成绩")
    void shouldCalculateAverageAccurately_WhenMultipleSubjects_ThenCorrect() {
        // Given
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(mockScores);

        // When
        PersonalStatResponse response = statService.getPersonalStat(1L, 1L);

        // Then
        assertThat(response.getTotalScore()).isEqualByComparingTo("185.00");
        assertThat(response.getAverageScore()).isEqualByComparingTo("92.50");
    }

    @Test
    @DisplayName("应该获取班级成绩统计_当班级有成绩")
    void shouldGetClassStat_WhenScoresExist_ThenSuccess() {
        // Given
        when(scoreMapper.selectByExamId(1L, 1L, 1L)).thenReturn(mockScores);

        // When
        ClassStatResponse response = statService.getClassStat(1L, 1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getClassId()).isEqualTo(1L);
        assertThat(response.getExamId()).isEqualTo(1L);
        assertThat(response.getTotalStudents()).isEqualTo(2);
        assertThat(response.getAverageScore()).isNotNull();
        verify(scoreMapper).selectByExamId(1L, 1L, 1L);
    }

    @Test
    @DisplayName("应该计算班级平均分和及格率_当有成绩数据")
    void shouldCalculateClassStats_WhenScoresExist_ThenAccurate() {
        // Given
        Score student1Subject1 = createScore(1L, 1L, 1L, 1L, 95.0);
        Score student1Subject2 = createScore(2L, 1L, 1L, 2L, 90.0);
        Score student2Subject1 = createScore(3L, 1L, 2L, 1L, 55.0);
        Score student2Subject2 = createScore(4L, 1L, 2L, 2L, 60.0);

        when(scoreMapper.selectByExamId(1L, 1L, 1L))
                .thenReturn(Arrays.asList(student1Subject1, student1Subject2, student2Subject1, student2Subject2));
        when(subjectMapper.selectBatchIds(anyCollection()))
                .thenReturn(Arrays.asList(subject(1L, "语文", 100, 60), subject(2L, "数学", 150, 90)));

        // When
        ClassStatResponse response = statService.getClassStat(1L, 1L);

        // Then
        assertThat(response.getTotalStudents()).isEqualTo(2);
        assertThat(response.getActualStudents()).isEqualTo(2);
        // ponytail: 口径从「总分≥60 即及格」改为「总分 ≥ Σ各科及格线」：语文 60 + 数学 90 = 150，
        // 学生1 总分 185 及格，学生2 总分 115 不及格；原断言 passCount=2 是把两科总分当百分制比的结果
        assertThat(response.getPassCount()).isEqualTo(1);
        assertThat(response.getPassRate()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("应该按科目配置的及格线和满分统计_当科目非百分制")
    void shouldUseSubjectLines_WhenSubjectNotHundredBased_ThenAccurate() {
        // Given: 单科满分 150、及格 90，三个学生 100 / 80 / 95
        Score s1 = createScore(1L, 1L, 1L, 1L, 100.0);
        Score s2 = createScore(2L, 1L, 2L, 1L, 80.0);
        Score s3 = createScore(3L, 1L, 3L, 1L, 95.0);
        when(scoreMapper.selectByExamId(1L, 1L, 1L)).thenReturn(Arrays.asList(s1, s2, s3));
        when(subjectMapper.selectBatchIds(anyCollection()))
                .thenReturn(Collections.singletonList(subject(1L, "数学", 150, 90)));

        // When
        ClassStatResponse response = statService.getClassStat(1L, 1L);

        // Then: 旧口径 60/85 会判 3 人及格 3 人优秀；新口径及格线 90、优秀线 127.5
        assertThat(response.getPassCount()).isEqualTo(2);
        assertThat(response.getPassRate()).isEqualByComparingTo("66.67");
        assertThat(response.getExcellentCount()).isEqualTo(0);
        assertThat(response.getSubjectStats()).hasSize(1);
        assertThat(response.getSubjectStats().get(0).getPassRate()).isEqualByComparingTo("66.67");
        // 分数段按得分率：100/150=66%、95/150=63% 落在 60-70，80/150=53% 落在 0-60
        assertThat(response.getScoreRanges()).extracting(ClassStatResponse.ScoreRange::getRange)
                .containsExactly("0-60", "60-70", "70-80", "80-90", "90-100");
        assertThat(response.getScoreRanges().get(0).getCount()).isEqualTo(1);
        assertThat(response.getScoreRanges().get(1).getCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("应该获取年级成绩统计_当有多个班级")
    void shouldGetGradeStat_WhenMultipleClasses_ThenSuccess() {
        // Given
        Score class1Score = createScore(1L, 1L, 1L, 1L, 95.0);
        class1Score.setClassId(1L);

        Score class2Score = createScore(2L, 1L, 2L, 1L, 85.0);
        class2Score.setClassId(2L);

        when(scoreMapper.selectByExamId(1L, 1L, null))
                .thenReturn(Arrays.asList(class1Score, class2Score));
        // gradeId 现在真的参与过滤：年级 1 下有班级 1、2
        when(classMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(schoolClass(1L, 1L), schoolClass(2L, 1L)));

        // When
        GradeStatResponse response = statService.getGradeStat(1L, 1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getGradeId()).isEqualTo(1L);
        assertThat(response.getExamId()).isEqualTo(1L);
        assertThat(response.getClassComparisons()).hasSize(2);
        verify(scoreMapper).selectByExamId(1L, 1L, null);
    }

    @Test
    @DisplayName("应该只统计该年级的班级_当指定年级时")
    void shouldFilterByGrade_WhenGradeIdGiven_ThenOnlyGradeClasses() {
        // Given: 班级 1 属于年级 1，班级 2 属于年级 2
        Score class1Score = createScore(1L, 1L, 1L, 1L, 95.0);
        class1Score.setClassId(1L);
        Score class2Score = createScore(2L, 1L, 2L, 1L, 85.0);
        class2Score.setClassId(2L);

        when(scoreMapper.selectByExamId(1L, 1L, null))
                .thenReturn(Arrays.asList(class1Score, class2Score));
        when(classMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.singletonList(schoolClass(1L, 1L)));
        Exam exam = new Exam();
        exam.setId(1L);
        exam.setExamName("期末考试");
        when(examMapper.selectById(1L)).thenReturn(exam);

        // When
        GradeStatResponse response = statService.getGradeStat(1L, 1L);

        // Then
        assertThat(response.getClassComparisons()).hasSize(1);
        assertThat(response.getClassComparisons().get(0).getClassId()).isEqualTo(1L);
        assertThat(response.getTotalStudents()).isEqualTo(1);
        assertThat(response.getExamName()).isEqualTo("期末考试");
    }

    @Test
    @DisplayName("应该返回全校总览_当不指定年级时")
    void shouldReturnSchoolOverview_WhenGradeIdNull_ThenAllClasses() {
        // Given
        Score class1Score = createScore(1L, 1L, 1L, 1L, 95.0);
        class1Score.setClassId(1L);
        Score class2Score = createScore(2L, 1L, 2L, 1L, 85.0);
        class2Score.setClassId(2L);
        when(scoreMapper.selectByExamId(1L, 1L, null))
                .thenReturn(Arrays.asList(class1Score, class2Score));

        // When
        GradeStatResponse response = statService.getGradeStat(1L, null);

        // Then
        assertThat(response.getGradeId()).isNull();
        assertThat(response.getGradeName()).isNull();
        assertThat(response.getClassComparisons()).hasSize(2);
        verify(classMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("应该限定在教师可见班级_当教师查询年级统计时")
    void shouldLimitToTeacherClasses_WhenTeacherQueriesGradeStat_ThenNotForbidden() {
        // Given
        asTeacher(7L);
        Score class1Score = createScore(1L, 1L, 1L, 1L, 95.0);
        class1Score.setClassId(1L);
        Score class2Score = createScore(2L, 1L, 2L, 1L, 85.0);
        class2Score.setClassId(2L);
        Score class3Score = createScore(3L, 1L, 3L, 1L, 75.0);
        class3Score.setClassId(3L);
        when(scoreMapper.selectByExamId(1L, 1L, null))
                .thenReturn(Arrays.asList(class1Score, class2Score, class3Score));
        when(teacherScopeService.currentAllowedClassIds()).thenReturn(Set.of(2L));

        // When: 原实现对教师一律 FORBIDDEN，现在返回其可见班级的统计
        GradeStatResponse response = statService.getGradeStat(1L, null);

        // Then
        assertThat(response.getClassComparisons()).hasSize(1);
        assertThat(response.getClassComparisons().get(0).getClassId()).isEqualTo(2L);
        assertThat(response.getTotalStudents()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该对比各班级成绩_当年级统计时")
    void shouldCompareClasses_WhenGradeStat_ThenRankedCorrectly() {
        // Given
        Score class1Student1 = createScore(1L, 1L, 1L, 1L, 95.0);
        class1Student1.setClassId(1L);

        Score class2Student1 = createScore(2L, 1L, 2L, 1L, 85.0);
        class2Student1.setClassId(2L);

        Score class3Student1 = createScore(3L, 1L, 3L, 1L, 90.0);
        class3Student1.setClassId(3L);

        when(scoreMapper.selectByExamId(1L, 1L, null))
                .thenReturn(Arrays.asList(class1Student1, class2Student1, class3Student1));
        when(classMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(schoolClass(1L, 1L), schoolClass(2L, 1L), schoolClass(3L, 1L)));

        // When
        GradeStatResponse response = statService.getGradeStat(1L, 1L);

        // Then
        assertThat(response.getClassComparisons()).hasSize(3);
        // 班级应该按平均分排序
        assertThat(response.getClassComparisons().get(0).getRank()).isEqualTo(1);
        assertThat(response.getClassComparisons().get(0).getClassId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("应该按任教班级过滤科目统计_当教师查询时")
    void shouldLimitSubjectStatToTeachingClasses_WhenTeacher_ThenFiltered() {
        // Given: 教师任教班级 1 的科目 1，并担任班级 3 的班主任；班级 2 不可见
        asTeacher(7L);
        Score class1 = createScore(1L, 1L, 1L, 1L, 95.0);
        class1.setClassId(1L);
        Score class2 = createScore(2L, 1L, 2L, 1L, 85.0);
        class2.setClassId(2L);
        Score class3 = createScore(3L, 1L, 3L, 1L, 75.0);
        class3.setClassId(3L);
        when(scoreMapper.selectByExamId(1L, 1L, null, 1L)).thenReturn(Arrays.asList(class1, class2, class3));
        TeacherClass assignment = new TeacherClass();
        assignment.setTeacherId(7L);
        assignment.setClassId(1L);
        assignment.setSubjectId(1L);
        when(teacherScopeService.currentTeachingAssignments()).thenReturn(Collections.singletonList(assignment));
        when(teacherScopeService.currentHeadClassIds()).thenReturn(Set.of(3L));

        // When
        SubjectStatResponse response = statService.getSubjectStat(1L, 1L);

        // Then
        assertThat(response.getTotalStudents()).isEqualTo(2);
        assertThat(response.getClassAverages()).extracting(SubjectStatResponse.ClassAverage::getClassId)
                .containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("应该获取成绩趋势_当学生有多次考试成绩")
    void shouldGetStudentTrend_WhenMultipleExams_ThenSuccess() {
        // Given
        Score exam1Score = createScore(1L, 1L, 1L, 1L, 95.0);
        Score exam2Score = createScore(2L, 2L, 1L, 1L, 90.0);
        Score exam3Score = createScore(3L, 3L, 1L, 1L, 92.0);

        when(scoreMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(exam1Score, exam2Score, exam3Score));

        // When
        StudentTrendResponse response = statService.getStudentTrend(1L, null);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStudentId()).isEqualTo(1L);
        assertThat(response.getExamTrends()).hasSize(3);
        assertThat(response.getSubjectRadars()).isNotEmpty();
        verify(scoreMapper).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("应该用考试科目真实满分算得分率_当构建雷达图时")
    void shouldUseExamSubjectFullScore_WhenBuildingRadar_ThenPercentageAccurate() {
        // Given: 最近一次考试（无日期时按 examId 升序，取 3）数学满分 150，得 92 分
        Score exam1Score = createScore(1L, 1L, 1L, 1L, 95.0);
        Score exam3Score = createScore(3L, 3L, 1L, 1L, 92.0);
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(exam1Score, exam3Score));
        when(examSubjectMapper.selectByExamId(3L)).thenReturn(Collections.singletonList(examSubject(1L, 150)));

        // When
        StudentTrendResponse response = statService.getStudentTrend(1L, null);

        // Then
        assertThat(response.getSubjectRadars()).hasSize(1);
        StudentTrendResponse.SubjectRadar radar = response.getSubjectRadars().get(0);
        assertThat(radar.getFullScore()).isEqualByComparingTo("150");
        assertThat(radar.getPercentage()).isEqualByComparingTo("61.3");
    }

    @Test
    @DisplayName("应该统计分数段分布_当计算班级统计时")
    void shouldCalculateScoreRanges_WhenClassStat_ThenAccurate() {
        // Given
        Score score1 = createScore(1L, 1L, 1L, 1L, 95.0);
        Score score2 = createScore(2L, 1L, 2L, 1L, 75.0);
        Score score3 = createScore(3L, 1L, 3L, 1L, 55.0);

        when(scoreMapper.selectByExamId(1L, 1L, 1L))
                .thenReturn(Arrays.asList(score1, score2, score3));

        // When
        ClassStatResponse response = statService.getClassStat(1L, 1L);

        // Then
        assertThat(response.getScoreRanges()).isNotEmpty();
        assertThat(response.getScoreRanges()).hasSize(5); // 5个分数段
    }

    @Test
    @DisplayName("应该返回空趋势_当学生无成绩记录")
    void shouldReturnEmptyTrend_WhenNoScores_ThenEmpty() {
        // Given
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        // When
        StudentTrendResponse response = statService.getStudentTrend(999L, null);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getStudentId()).isEqualTo(999L);
        assertThat(response.getExamTrends()).isEmpty();
        assertThat(response.getSubjectRadars()).isEmpty();
    }

    @Test
    @DisplayName("应该抛出异常_当班级无成绩")
    void shouldThrowException_WhenClassHasNoScores_ThenNotFound() {
        // Given
        when(scoreMapper.selectByExamId(1L, 1L, 1L)).thenReturn(Collections.emptyList());

        // When & Then
        assertThatThrownBy(() -> statService.getClassStat(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到班级成绩");

        verify(scoreMapper).selectByExamId(1L, 1L, 1L);
    }

    @Test
    @DisplayName("应该正确计算及格率_当有及格和不及格学生")
    void shouldCalculatePassRate_WhenMixedScores_ThenAccurate() {
        // Given
        Score pass1 = createScore(1L, 1L, 1L, 1L, 95.0);
        Score pass2 = createScore(2L, 1L, 2L, 1L, 70.0);
        Score fail = createScore(3L, 1L, 3L, 1L, 50.0);

        when(scoreMapper.selectByExamId(1L, 1L, 1L))
                .thenReturn(Arrays.asList(pass1, pass2, fail));

        // When
        ClassStatResponse response = statService.getClassStat(1L, 1L);

        // Then
        assertThat(response.getTotalStudents()).isEqualTo(3);
        assertThat(response.getPassCount()).isGreaterThanOrEqualTo(0);
        assertThat(response.getPassRate()).isNotNull();
    }

    @Test
    @DisplayName("应该正确计算优秀率_当有优秀学生")
    void shouldCalculateExcellentRate_WhenExcellentScores_ThenAccurate() {
        // Given
        Score excellent = createScore(1L, 1L, 1L, 1L, 95.0);
        Score good = createScore(2L, 1L, 2L, 1L, 75.0);
        Score pass = createScore(3L, 1L, 3L, 1L, 60.0);

        when(scoreMapper.selectByExamId(1L, 1L, 1L))
                .thenReturn(Arrays.asList(excellent, good, pass));

        // When
        ClassStatResponse response = statService.getClassStat(1L, 1L);

        // Then
        assertThat(response.getExcellentCount()).isGreaterThanOrEqualTo(0);
        assertThat(response.getExcellentRate()).isNotNull();
    }

    @Test
    @DisplayName("应该返回空年级统计_当没有成绩数据")
    void shouldReturnEmptyGradeStat_WhenNoScores_ThenEmpty() {
        // Given
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(Collections.emptyList());

        // When
        GradeStatResponse response = statService.getGradeStat(1L, 1L);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getGradeId()).isEqualTo(1L);
        assertThat(response.getClassComparisons()).isEmpty();
    }

    @Test
    @DisplayName("应该计算最高分和最低分_当班级统计时")
    void shouldCalculateMaxAndMin_WhenClassStat_ThenCorrect() {
        // Given
        Score high = createScore(1L, 1L, 1L, 1L, 95.0);
        Score medium = createScore(2L, 1L, 2L, 1L, 75.0);
        Score low = createScore(3L, 1L, 3L, 1L, 55.0);

        when(scoreMapper.selectByExamId(1L, 1L, 1L))
                .thenReturn(Arrays.asList(high, medium, low));

        // When
        ClassStatResponse response = statService.getClassStat(1L, 1L);

        // Then
        assertThat(response.getMaxScore()).isNotNull();
        assertThat(response.getMinScore()).isNotNull();
        assertThat(response.getMaxScore()).isGreaterThanOrEqualTo(response.getMinScore());
    }

    @Test
    @DisplayName("应该忽略缺考学生_当计算统计数据时")
    void shouldIgnoreAbsentStudents_WhenCalculateStats_ThenExclude() {
        // Given
        Score present = createScore(1L, 1L, 1L, 1L, 95.0);
        Score absent = createScore(2L, 1L, 2L, 1L, 0.0);
        absent.setIsAbsent(1);
        absent.setScore(null);

        when(scoreMapper.selectByExamId(1L, 1L, 1L))
                .thenReturn(Arrays.asList(present, absent));

        // When
        ClassStatResponse response = statService.getClassStat(1L, 1L);

        // Then
        assertThat(response.getTotalStudents()).isEqualTo(2);
        assertThat(response.getAbsentStudents()).isEqualTo(1);
        assertThat(response.getActualStudents()).isEqualTo(1);
    }

    @Test
    @DisplayName("应该包含科目成绩详情_当个人统计时")
    void shouldIncludeSubjectDetails_WhenPersonalStat_ThenComplete() {
        // Given
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(mockScores);

        // When
        PersonalStatResponse response = statService.getPersonalStat(1L, 1L);

        // Then
        assertThat(response.getSubjectScores()).isNotEmpty();
        assertThat(response.getSubjectScores().get(0).getSubjectId()).isNotNull();
        assertThat(response.getSubjectScores().get(0).getScore()).isNotNull();
    }

    // ---------------------------------------------------------------- ponytail: 本轮补的用例（C-stat-8 零覆盖区域）

    @Test
    @DisplayName("应该按年级聚合均分_当全校总览跨年级时")
    void shouldAggregateGradeStats_WhenClassesInDifferentGrades_ThenPerGradeAverage() {
        // Given: 班级 1 属年级 1（学生 1 得 95），班级 2 属年级 2（学生 2 得 85、学生 3 得 75）
        Score c1s1 = createScore(1L, 1L, 1L, 1L, 95.0);
        c1s1.setClassId(1L);
        Score c2s2 = createScore(2L, 1L, 2L, 1L, 85.0);
        c2s2.setClassId(2L);
        Score c2s3 = createScore(3L, 1L, 3L, 1L, 75.0);
        c2s3.setClassId(2L);
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(Arrays.asList(c1s1, c2s2, c2s3));
        // ponytail: 此前没有任何用例 stub classMapper.selectBatchIds，gradeIdOf 恒为 null，gradeStats 永远是空却从未断言
        when(classMapper.selectBatchIds(anyCollection()))
                .thenReturn(Arrays.asList(schoolClass(1L, 1L), schoolClass(2L, 2L)));
        when(gradeMapper.selectBatchIds(anyCollection()))
                .thenReturn(Arrays.asList(grade(1L, "高一"), grade(2L, "高二")));

        // When
        GradeStatResponse response = statService.getGradeStat(1L, null);

        // Then
        assertThat(response.getGradeStats()).hasSize(2);
        assertThat(response.getGradeStats()).extracting(GradeStatResponse.GradeStat::getGradeId)
                .containsExactly(1L, 2L);
        assertThat(response.getGradeStats().get(0).getGradeName()).isEqualTo("高一");
        assertThat(response.getGradeStats().get(0).getAverageScore()).isEqualByComparingTo("95.00");
        assertThat(response.getGradeStats().get(1).getAverageScore()).isEqualByComparingTo("80.00");
    }

    @Test
    @DisplayName("应该只保留该学期的考试_当趋势按学期筛选时")
    void shouldFilterByTerm_WhenTermIdGiven_ThenOnlyThatTerm() {
        // Given: 考试 1、3 属学期 10，考试 2 属学期 20
        Score exam1Score = createScore(1L, 1L, 1L, 1L, 95.0);
        Score exam2Score = createScore(2L, 2L, 1L, 1L, 90.0);
        Score exam3Score = createScore(3L, 3L, 1L, 1L, 92.0);
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(exam1Score, exam2Score, exam3Score));
        when(examMapper.selectBatchIds(anyCollection()))
                .thenReturn(Arrays.asList(exam(1L, 10L, 3), exam(2L, 20L, 3), exam(3L, 10L, 3)));

        // When
        StudentTrendResponse response = statService.getStudentTrend(1L, null, 10L);

        // Then
        assertThat(response.getExamTrends()).extracting(StudentTrendResponse.ExamTrend::getExamId)
                .containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("应该拒绝查看他人个人统计_当学生身份时")
    void shouldForbidOtherStudentPersonalStat_WhenStudent_ThenForbidden() {
        // Given
        asStudent(5L);

        // When & Then: 守卫在读任何成绩之前生效
        assertForbidden(() -> statService.getPersonalStat(1L, 6L));
        verifyNoInteractions(scoreMapper);
    }

    @Test
    @DisplayName("应该返回自己的个人统计_当学生查已发布考试时")
    void shouldReturnOwnPersonalStat_WhenStudentAndExamPublished_ThenSuccess() {
        // Given
        asStudent(1L);
        when(examMapper.selectById(1L)).thenReturn(exam(1L, 10L, 3));
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(mockScores);

        // When
        PersonalStatResponse response = statService.getPersonalStat(1L, 1L);

        // Then
        assertThat(response.getStudentId()).isEqualTo(1L);
        assertThat(response.getTotalScore()).isEqualByComparingTo("185.00");
    }

    @Test
    @DisplayName("应该拒绝查看未发布考试_当学生查个人统计时")
    void shouldForbidUnpublishedExam_WhenStudentPersonalStat_ThenForbidden() {
        // Given: 考试处于审核中（status=2），成绩行已存在
        asStudent(1L);
        when(examMapper.selectById(1L)).thenReturn(exam(1L, 10L, 2));

        // When & Then
        assertThatThrownBy(() -> statService.getPersonalStat(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("尚未发布");
        verifyNoInteractions(scoreMapper);
    }

    @Test
    @DisplayName("应该过滤未发布考试_当学生查看成绩趋势时")
    void shouldHideUnpublishedExams_WhenStudentTrend_ThenOnlyPublished() {
        // Given: 考试 1 已发布，考试 2 审核中
        asStudent(1L);
        Score exam1Score = createScore(1L, 1L, 1L, 1L, 95.0);
        Score exam2Score = createScore(2L, 2L, 1L, 1L, 90.0);
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Arrays.asList(exam1Score, exam2Score));
        when(examMapper.selectBatchIds(anyCollection()))
                .thenReturn(Arrays.asList(exam(1L, 10L, 3), exam(2L, 10L, 2)));

        // When
        StudentTrendResponse response = statService.getStudentTrend(1L, null);

        // Then: 未发布的考试 2 既不在趋势里，也不作为雷达图的「最近一次考试」
        assertThat(response.getExamTrends()).extracting(StudentTrendResponse.ExamTrend::getExamId)
                .containsExactly(1L);
        assertThat(response.getSubjectRadars()).extracting(StudentTrendResponse.SubjectRadar::getScore)
                .containsExactly(BigDecimal.valueOf(95.0));
    }

    @Test
    @DisplayName("应该拒绝班级年级科目统计_当学生身份时")
    void shouldForbidAggregateStats_WhenStudent_ThenForbidden() {
        // Given
        asStudent(5L);

        // When & Then: 三个聚合入口都在读成绩前被拒
        assertForbidden(() -> statService.getClassStat(1L, 1L));
        assertForbidden(() -> statService.getGradeStat(1L, null));
        assertForbidden(() -> statService.getSubjectStat(1L, 1L));
        verifyNoInteractions(scoreMapper);
    }

    @Test
    @DisplayName("应该按本场满分折算及格线_当考试满分覆盖科目满分时")
    void shouldScalePassLine_WhenExamFullScoreOverridesSubject_ThenConsistentWithExcellentLine() {
        // Given: 数学 subject 150/90，本场 exam_subject 满分 100；学生 1 得 85、学生 2 得 50
        Score s1 = createScore(1L, 1L, 1L, 2L, 85.0);
        Score s2 = createScore(2L, 1L, 2L, 2L, 50.0);
        when(scoreMapper.selectByExamId(1L, 1L, 1L)).thenReturn(Arrays.asList(s1, s2));
        when(subjectMapper.selectBatchIds(anyCollection()))
                .thenReturn(Collections.singletonList(subject(2L, "数学", 150, 90)));
        when(examSubjectMapper.selectByExamId(1L)).thenReturn(Collections.singletonList(examSubject(2L, 100)));

        // When
        ClassStatResponse response = statService.getClassStat(1L, 1L);

        // Then: 及格线 90×100/150 = 60、优秀线 85；85 分既优秀也及格（原实现 passLine=90 会判成「优秀但不及格」）
        assertThat(response.getPassCount()).isEqualTo(1);
        assertThat(response.getExcellentCount()).isEqualTo(1);
        assertThat(response.getPassRate()).isEqualByComparingTo("50.00");
        assertThat(response.getSubjectStats()).hasSize(1);
        assertThat(response.getSubjectStats().get(0).getPassRate()).isEqualByComparingTo("50.00");
        assertThat(response.getSubjectStats().get(0).getExcellentRate()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("应该用有有效成绩的学生数做分母_当学生部分缺考时")
    void shouldUseStudentsWithScoresAsDenominator_WhenPartiallyAbsent_ThenRatesBounded() {
        // Given: A 语文 95 + 数学 90；B 语文缺考、数学 60
        Score a1 = createScore(1L, 1L, 1L, 1L, 95.0);
        Score a2 = createScore(2L, 1L, 1L, 2L, 90.0);
        Score b1 = createScore(3L, 1L, 2L, 1L, 0.0);
        b1.setIsAbsent(1);
        b1.setScore(null);
        Score b2 = createScore(4L, 1L, 2L, 2L, 60.0);
        when(scoreMapper.selectByExamId(1L, 1L, 1L)).thenReturn(Arrays.asList(a1, a2, b1, b2));

        // When
        ClassStatResponse response = statService.getClassStat(1L, 1L);

        // Then: 两人都有有效成绩 → actual=2、absent=0；均分 (185+60)/2；及格线 120 → A 及格 B 不及格
        // （原分母 totalStudents − 任一科缺考人数 = 1，会得出 245 均分、200% 及格率）
        assertThat(response.getTotalStudents()).isEqualTo(2);
        assertThat(response.getActualStudents()).isEqualTo(2);
        assertThat(response.getAbsentStudents()).isEqualTo(0);
        assertThat(response.getAverageScore()).isEqualByComparingTo("122.50");
        assertThat(response.getPassRate()).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("应该读取存储的总分名次_当趋势为总分口径时")
    void shouldUseStoredTotalRank_WhenTrendWithoutSubject_ThenNotSubjectRank() {
        // Given: 考试 1 两科行：语文单科名次 20/30、数学单科名次 7/9；总分名次（各行相同）1/2
        Score chinese = createScore(1L, 1L, 1L, 1L, 95.0);
        chinese.setRankInClass(20);
        chinese.setRankInGrade(30);
        chinese.setTotalRankInClass(1);
        chinese.setTotalRankInGrade(2);
        Score math = createScore(2L, 1L, 1L, 2L, 90.0);
        math.setRankInClass(7);
        math.setRankInGrade(9);
        math.setTotalRankInClass(1);
        math.setTotalRankInGrade(2);
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(chinese, math));

        // When
        StudentTrendResponse response = statService.getStudentTrend(1L, null);

        // Then: 不再是第一条科目行的单科名次 20
        assertThat(response.getExamTrends()).hasSize(1);
        assertThat(response.getExamTrends().get(0).getRankInClass()).isEqualTo(1);
        assertThat(response.getExamTrends().get(0).getRankInGrade()).isEqualTo(2);
        // 有存储的总分名次时不为现算回查全场成绩
        verify(scoreMapper, never()).selectByExamId(any(), any(), any());
    }

    @Test
    @DisplayName("应该沿用单科名次_当趋势指定科目时")
    void shouldUseSubjectRank_WhenTrendWithSubject_ThenRowRank() {
        // Given
        Score chinese = createScore(1L, 1L, 1L, 1L, 95.0);
        chinese.setRankInClass(20);
        chinese.setRankInGrade(30);
        chinese.setTotalRankInClass(1);
        chinese.setTotalRankInGrade(2);
        when(scoreMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(chinese));

        // When
        StudentTrendResponse response = statService.getStudentTrend(1L, 1L);

        // Then
        assertThat(response.getExamTrends().get(0).getRankInClass()).isEqualTo(20);
        assertThat(response.getExamTrends().get(0).getRankInGrade()).isEqualTo(30);
    }

    @Test
    @DisplayName("应该优先读取存储的总分名次_当个人统计时")
    void shouldPreferStoredTotalRank_WhenPersonalStat_ThenNoRecalculation() {
        // Given: 学生 1 两行都带总分名次 3/8（现算会得出 1/1，因为 185 > 85）
        score1.setTotalRankInClass(3);
        score1.setTotalRankInGrade(8);
        score2.setTotalRankInClass(3);
        score2.setTotalRankInGrade(8);
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(mockScores);

        // When
        PersonalStatResponse response = statService.getPersonalStat(1L, 1L);

        // Then
        assertThat(response.getRankInClass()).isEqualTo(3);
        assertThat(response.getRankInGrade()).isEqualTo(8);
        verify(classMapper, never()).selectBatchIds(anyCollection());
    }

    @Test
    @DisplayName("应该把空的缺考标记当作未缺考_当成绩行is_absent为NULL时")
    void shouldTreatNullIsAbsentAsPresent_WhenLegacyRow_ThenNoNpe() {
        // Given: score.is_absent 在 DDL 里可空，历史/修数行可能是 NULL
        Score legacy = createScore(1L, 1L, 1L, 1L, 95.0);
        legacy.setIsAbsent(null);
        Score other = createScore(2L, 1L, 2L, 1L, 80.0);
        when(scoreMapper.selectByExamId(1L, 1L, null)).thenReturn(Arrays.asList(legacy, other));
        when(scoreMapper.selectByExamId(1L, 1L, 1L)).thenReturn(Arrays.asList(legacy, other));

        // When
        PersonalStatResponse personal = statService.getPersonalStat(1L, 1L);
        ClassStatResponse classStat = statService.getClassStat(1L, 1L);

        // Then
        assertThat(personal.getTotalScore()).isEqualByComparingTo("95.0");
        assertThat(personal.getSubjectScores().get(0).getIsAbsent()).isFalse();
        assertThat(classStat.getActualStudents()).isEqualTo(2);
        assertThat(classStat.getAbsentStudents()).isEqualTo(0);
    }

    /**
     * 辅助方法：创建成绩对象
     */
    private Score createScore(Long id, Long examId, Long studentId, Long subjectId, Double score) {
        Score s = new Score();
        s.setId(id);
        s.setExamId(examId);
        s.setStudentId(studentId);
        s.setSubjectId(subjectId);
        s.setClassId(1L);
        s.setSchoolId(1L);
        s.setScore(BigDecimal.valueOf(score));
        s.setIsAbsent(0);
        s.setRankInClass(1);
        s.setRankInGrade(1);
        s.setStatus(1);
        return s;
    }

    /**
     * 辅助方法：科目（满分 / 及格分）
     */
    private Subject subject(Long id, String name, int fullScore, int passScore) {
        Subject subject = new Subject();
        subject.setId(id);
        subject.setSchoolId(1L);
        subject.setSubjectName(name);
        subject.setFullScore(BigDecimal.valueOf(fullScore));
        subject.setPassScore(BigDecimal.valueOf(passScore));
        return subject;
    }

    /**
     * 辅助方法：考试科目配置（本次考试的满分）
     */
    private ExamSubject examSubject(Long subjectId, int fullScore) {
        ExamSubject examSubject = new ExamSubject();
        examSubject.setSubjectId(subjectId);
        examSubject.setFullScore(BigDecimal.valueOf(fullScore));
        return examSubject;
    }

    /**
     * 辅助方法：班级（所属年级）
     */
    private SchoolClass schoolClass(Long id, Long gradeId) {
        SchoolClass schoolClass = new SchoolClass();
        schoolClass.setId(id);
        schoolClass.setSchoolId(1L);
        schoolClass.setGradeId(gradeId);
        schoolClass.setClassName("班级" + id);
        return schoolClass;
    }

    /**
     * 辅助方法：以教师身份登录（TestAuth 没有教师桩）
     */
    private void asTeacher(Long teacherId) {
        TestAuth.authenticate(LoginUser.builder()
                .userId(200L)
                .username("teacher" + teacherId)
                .schoolId(1L)
                .userType(UserRole.TEACHER.getUserType())
                .teacherId(teacherId)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_TEACHER")))
                .build());
    }

    /**
     * 辅助方法：以学生身份登录（TestAuth 没有学生桩）
     */
    private void asStudent(Long studentId) {
        TestAuth.authenticate(LoginUser.builder()
                .userId(300L)
                .username("student" + studentId)
                .schoolId(1L)
                .userType(UserRole.STUDENT.getUserType())
                .studentId(studentId)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_STUDENT")))
                .build());
    }

    /**
     * 辅助方法：考试（所属学期 / 状态：2-审核中，3-已发布）
     */
    private Exam exam(Long id, Long termId, int status) {
        Exam exam = new Exam();
        exam.setId(id);
        exam.setSchoolId(1L);
        exam.setTermId(termId);
        exam.setExamName("考试" + id);
        exam.setStatus(status);
        return exam;
    }

    /**
     * 辅助方法：年级
     */
    private Grade grade(Long id, String name) {
        Grade grade = new Grade();
        grade.setId(id);
        grade.setSchoolId(1L);
        grade.setGradeName(name);
        return grade;
    }

    /**
     * 辅助方法：断言抛出 FORBIDDEN 业务异常
     */
    private void assertForbidden(ThrowingCallable call) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.FORBIDDEN.getCode());
    }
}
