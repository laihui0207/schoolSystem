package com.school.module.job.handlers;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.school.common.constant.ExamStatusConstants;
import com.school.common.exception.BusinessException;
import com.school.module.exam.entity.Exam;
import com.school.module.exam.mapper.ExamMapper;
import com.school.module.warning.service.ScoreWarningService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 成绩预警补扫任务单元测试：查询条件、逐场隔离失败、默认天数
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("成绩预警扫描任务测试")
class ScoreWarningScanHandlerTest {

    @Mock
    private ExamMapper examMapper;

    @Mock
    private ScoreWarningService scoreWarningService;

    @Captor
    private ArgumentCaptor<Wrapper<Exam>> wrapperCaptor;

    @InjectMocks
    private ScoreWarningScanHandler handler;

    @BeforeAll
    static void initTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), Exam.class);
    }

    private static Exam exam(Long id, LocalDate examDate) {
        Exam exam = new Exam();
        exam.setId(id);
        exam.setExamDate(examDate);
        exam.setStatus(ExamStatusConstants.PUBLISHED);
        return exam;
    }

    @Test
    @DisplayName("应该逐场检查并统计成败_当有已发布考试且其中一场失败")
    @SuppressWarnings("unchecked")
    void shouldScanEachExam_WhenOneFails_ThenSummaryCountsBoth() {
        LocalDate today = LocalDate.now();
        when(examMapper.selectList(any())).thenReturn(List.of(exam(1L, today.minusDays(1)), exam(2L, today.minusDays(2))));
        // ponytail: 严格桩下只对 2L 建桩会让 1L 的调用抛 PotentialStubbingProblem，而 handler 的 catch 会把它当作业务失败吞掉，
        // 结果变成「成功 0 失败 2」。用 anyLong() 一次性建桩，在 Answer 里按参数决定是否抛，才是真正的「一场失败一场成功」。
        doAnswer(invocation -> {
            if (Long.valueOf(2L).equals(invocation.getArgument(0))) {
                throw new BusinessException("通知写入失败");
            }
            return null;
        }).when(scoreWarningService).checkScoreWarnings(anyLong());

        String summary = handler.handle(Map.of("days", 3));

        verify(scoreWarningService).checkScoreWarnings(1L);
        verify(scoreWarningService).checkScoreWarnings(2L);
        assertThat(summary).contains("2 场").contains("成功 1").contains("失败 1").contains("2:通知写入失败");

        verify(examMapper).selectList(wrapperCaptor.capture());
        LambdaQueryWrapper<Exam> wrapper = (LambdaQueryWrapper<Exam>) wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("status").contains("exam_date");
        assertThat(wrapper.getParamNameValuePairs().values())
                .contains(ExamStatusConstants.PUBLISHED, today.minusDays(3));
    }

    @Test
    @DisplayName("应该默认回看 7 天_当未传 days")
    void shouldUseDefault7Days_WhenParamMissing_ThenNoExamNoCall() {
        when(examMapper.selectList(any())).thenReturn(Collections.emptyList());

        String summary = handler.handle(Collections.emptyMap());

        assertThat(summary).contains(LocalDate.now().minusDays(ScoreWarningScanHandler.DEFAULT_DAYS).toString())
                .contains("0 场");
        verify(scoreWarningService, never()).checkScoreWarnings(anyLong());
    }

    @Test
    @DisplayName("应该拒绝执行_当 days 不是正整数")
    void shouldReject_WhenDaysNotPositive_ThenNoQuery() {
        assertThatThrownBy(() -> handler.handle(Map.of("days", -1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("days");

        verify(examMapper, never()).selectList(any());
    }

}
