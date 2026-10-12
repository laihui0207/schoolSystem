package com.school.module.job.handlers;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.school.module.log.entity.OperationLog;
import com.school.module.log.mapper.OperationLogMapper;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 清理操作日志任务单元测试：截止时间计算、默认值、非法参数拒绝
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("清理操作日志任务测试")
class CleanOperationLogsHandlerTest {

    @Mock
    private OperationLogMapper operationLogMapper;

    @Captor
    private ArgumentCaptor<Wrapper<OperationLog>> wrapperCaptor;

    @InjectMocks
    private CleanOperationLogsHandler handler;

    @BeforeAll
    static void initTableInfo() {
        // ponytail: 没有 SqlSessionFactory 时 LambdaQueryWrapper 找不到 OperationLog::getCreatedAt 的列缓存，手动注册
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), OperationLog.class);
    }

    /**
     * 取出 delete 条件里的截止时间，并确认条件落在 created_at 上且是小于比较
     */
    @SuppressWarnings("unchecked")
    private LocalDateTime capturedCutoff() {
        verify(operationLogMapper).delete(wrapperCaptor.capture());
        LambdaQueryWrapper<OperationLog> wrapper = (LambdaQueryWrapper<OperationLog>) wrapperCaptor.getValue();
        assertThat(wrapper.getSqlSegment()).contains("created_at").contains("<");
        return wrapper.getParamNameValuePairs().values().stream()
                .filter(LocalDateTime.class::isInstance)
                .map(LocalDateTime.class::cast)
                .findFirst()
                .orElseThrow();
    }

    private static void assertCutoffDaysAgo(LocalDateTime cutoff, int days) {
        LocalDateTime expected = LocalDateTime.now().minusDays(days);
        assertThat(cutoff).isBetween(expected.minusMinutes(1), expected.plusMinutes(1));
    }

    @Test
    @DisplayName("应该删除截止时间之前的日志并报告条数_当指定 retainDays")
    void shouldDeleteBeforeCutoff_WhenRetainDaysGiven_ThenReportCount() {
        when(operationLogMapper.delete(any())).thenReturn(12);

        String summary = handler.handle(Map.of("retainDays", 30));

        assertCutoffDaysAgo(capturedCutoff(), 30);
        assertThat(summary).contains("30").contains("12");
    }

    @Test
    @DisplayName("应该默认保留 90 天_当未传 retainDays")
    void shouldUseDefault90Days_WhenParamMissing_ThenCutoff90DaysAgo() {
        when(operationLogMapper.delete(any())).thenReturn(0);

        String summary = handler.handle(Collections.emptyMap());

        assertCutoffDaysAgo(capturedCutoff(), CleanOperationLogsHandler.DEFAULT_RETAIN_DAYS);
        assertThat(summary).contains("90");
    }

    @Test
    @DisplayName("应该接受数字字符串_当 retainDays 以文本传入")
    void shouldAcceptNumericString_WhenRetainDaysIsText_ThenParse() {
        when(operationLogMapper.delete(any())).thenReturn(3);

        handler.handle(Map.of("retainDays", "45"));

        assertCutoffDaysAgo(capturedCutoff(), 45);
    }

    @Test
    @DisplayName("应该拒绝执行_当 retainDays 不是正整数")
    void shouldReject_WhenRetainDaysNotPositive_ThenNoDelete() {
        assertThatThrownBy(() -> handler.handle(Map.of("retainDays", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retainDays");

        verify(operationLogMapper, never()).delete(any());
    }

    @Test
    @DisplayName("应该拒绝执行_当 retainDays 不是数字")
    void shouldReject_WhenRetainDaysNotNumeric_ThenNoDelete() {
        assertThatThrownBy(() -> handler.handle(Map.of("retainDays", "abc")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("retainDays");

        verify(operationLogMapper, never()).delete(any());
    }

}
