package com.school.module.config.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScoreRuleService 单元测试：配置缺失 / 非法 / 正常 / 缓存
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("成绩规则配置服务测试")
class ScoreRuleServiceImplTest {

    @Mock
    private ConfigService configService;

    @InjectMocks
    private ScoreRuleServiceImpl scoreRuleService;

    // ------------------------------------------------------------------
    // 配置缺失回默认
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该返回默认值_当所有配置都缺失")
    void shouldReturnDefaults_WhenConfigMissing_ThenUseBuiltin() {
        when(configService.getConfigByKey(anyString())).thenReturn(null);

        assertThat(scoreRuleService.passRatio()).isEqualByComparingTo(new BigDecimal("0.60"));
        assertThat(scoreRuleService.excellentRatio()).isEqualByComparingTo(new BigDecimal("0.85"));
        assertThat(scoreRuleService.changeApprovalRequired()).isTrue();
        assertThat(scoreRuleService.maintenanceMode()).isFalse();
    }

    @Test
    @DisplayName("应该返回默认值_当配置值为空白")
    void shouldReturnDefaults_WhenConfigBlank_ThenUseBuiltin() {
        when(configService.getConfigByKey(anyString())).thenReturn("   ");

        assertThat(scoreRuleService.passRatio()).isEqualByComparingTo(new BigDecimal("0.60"));
        assertThat(scoreRuleService.changeApprovalRequired()).isTrue();
    }

    // ------------------------------------------------------------------
    // 非法值回默认
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该回落默认比例_当百分数不是数字")
    void shouldFallbackRatio_WhenNotNumeric_ThenDefault() {
        when(configService.getConfigByKey(ScoreRuleServiceImpl.KEY_PASS_LINE)).thenReturn("abc");

        assertThat(scoreRuleService.passRatio()).isEqualByComparingTo(new BigDecimal("0.60"));
    }

    @Test
    @DisplayName("应该回落默认比例_当百分数超出 0-100")
    void shouldFallbackRatio_WhenOutOfRange_ThenDefault() {
        when(configService.getConfigByKey(ScoreRuleServiceImpl.KEY_EXCELLENT_LINE)).thenReturn("150");

        assertThat(scoreRuleService.excellentRatio()).isEqualByComparingTo(new BigDecimal("0.85"));
    }

    @Test
    @DisplayName("应该回落默认开关_当布尔值不合法")
    void shouldFallbackFlag_WhenNotBoolean_ThenDefault() {
        when(configService.getConfigByKey(ScoreRuleServiceImpl.KEY_CHANGE_APPROVAL)).thenReturn("maybe");
        when(configService.getConfigByKey(ScoreRuleServiceImpl.KEY_MAINTENANCE)).thenReturn("2");

        assertThat(scoreRuleService.changeApprovalRequired()).isTrue();
        assertThat(scoreRuleService.maintenanceMode()).isFalse();
    }

    @Test
    @DisplayName("应该视为未配置_当读取配置表抛异常")
    void shouldReturnDefaults_WhenConfigServiceThrows_ThenNotPropagate() {
        when(configService.getConfigByKey(anyString())).thenThrow(new IllegalStateException("db down"));

        assertThat(scoreRuleService.maintenanceMode()).isFalse();
        assertThat(scoreRuleService.passRatio()).isEqualByComparingTo(new BigDecimal("0.60"));
    }

    // ------------------------------------------------------------------
    // 正常解析
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该把百分数换算成比例_当配置合法")
    void shouldParseRatio_WhenValidPercent_ThenDivideBy100() {
        when(configService.getConfigByKey(ScoreRuleServiceImpl.KEY_PASS_LINE)).thenReturn(" 70 ");
        when(configService.getConfigByKey(ScoreRuleServiceImpl.KEY_EXCELLENT_LINE)).thenReturn("92.5");

        assertThat(scoreRuleService.passRatio()).isEqualByComparingTo(new BigDecimal("0.70"));
        assertThat(scoreRuleService.excellentRatio()).isEqualByComparingTo(new BigDecimal("0.925"));
    }

    @Test
    @DisplayName("应该解析多种布尔写法_当配置合法")
    void shouldParseFlag_WhenValidBoolean_ThenMatch() {
        when(configService.getConfigByKey(ScoreRuleServiceImpl.KEY_CHANGE_APPROVAL)).thenReturn("FALSE");
        when(configService.getConfigByKey(ScoreRuleServiceImpl.KEY_MAINTENANCE)).thenReturn("1");

        assertThat(scoreRuleService.changeApprovalRequired()).isFalse();
        assertThat(scoreRuleService.maintenanceMode()).isTrue();
    }

    // ------------------------------------------------------------------
    // 缓存
    // ------------------------------------------------------------------

    @Test
    @DisplayName("应该只读一次配置表_当 60 秒内重复读取同一键")
    void shouldHitCache_WhenReadTwiceWithinTtl_ThenQueryOnce() {
        when(configService.getConfigByKey(ScoreRuleServiceImpl.KEY_MAINTENANCE)).thenReturn("true");

        assertThat(scoreRuleService.maintenanceMode()).isTrue();
        assertThat(scoreRuleService.maintenanceMode()).isTrue();

        verify(configService, times(1)).getConfigByKey(ScoreRuleServiceImpl.KEY_MAINTENANCE);
    }

    @Test
    @DisplayName("应该重新读库_当缓存被清空")
    void shouldReload_WhenCacheEvicted_ThenQueryAgain() {
        when(configService.getConfigByKey(ScoreRuleServiceImpl.KEY_MAINTENANCE)).thenReturn("false", "true");

        assertThat(scoreRuleService.maintenanceMode()).isFalse();
        scoreRuleService.evictCache();
        assertThat(scoreRuleService.maintenanceMode()).isTrue();

        verify(configService, times(2)).getConfigByKey(ScoreRuleServiceImpl.KEY_MAINTENANCE);
    }

}
