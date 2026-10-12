package com.school.module.sms.service;

import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.sms.config.SmsProperties;
import com.school.module.sms.sender.SmsSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 短信验证码服务单测：频控、过期、错误次数作废
 *
 * @author School System
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("短信验证码服务测试")
class SmsCodeServiceTest {

    private static final String PHONE = "13800138000";
    private static final String CODE_KEY = SmsCodeService.CODE_KEY + "login:" + PHONE;
    private static final String FAIL_KEY = SmsCodeService.FAIL_KEY + "login:" + PHONE;
    private static final String INTERVAL_KEY = SmsCodeService.INTERVAL_KEY + PHONE;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private SmsSender smsSender;

    private SmsProperties properties;
    private SmsCodeService service;

    @BeforeEach
    void setUp() {
        properties = new SmsProperties();
        properties.setCodeExpire(300);
        properties.setSendInterval(60);
        properties.setDailyLimit(10);
        properties.setIpDailyLimit(50);
        properties.setMaxVerifyFailures(5);
        // 部分用例在触达 Redis 之前就抛错，故用 lenient 避免严格桩报未使用
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new SmsCodeService(redisTemplate, smsSender, properties);
    }

    @Test
    @DisplayName("发送 - 手机号格式错误直接拒绝，不触达 Redis")
    void shouldRejectSend_WhenPhoneInvalid_ThenParamInvalid() {
        assertThatThrownBy(() -> service.sendCode("12345", "login", null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.PARAM_INVALID.getCode());
        verify(redisTemplate, never()).hasKey(anyString());
        verify(smsSender, never()).send(anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("发送 - 场景非法拒绝")
    void shouldRejectSend_WhenSceneUnknown_ThenParamInvalid() {
        assertThatThrownBy(() -> service.sendCode(PHONE, "hack", null))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.PARAM_INVALID.getCode());
        verify(smsSender, never()).send(anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("发送 - 60 秒内重复发送被频控，不消耗每日额度也不发短信")
    void shouldRejectSend_WhenWithinInterval_ThenNoSend() {
        when(redisTemplate.hasKey(INTERVAL_KEY)).thenReturn(true);

        assertThatThrownBy(() -> service.sendCode(PHONE, "login", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("频繁");

        verify(valueOperations, never()).increment(anyString());
        verify(smsSender, never()).send(anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("发送 - 超过每日上限拒绝")
    void shouldRejectSend_WhenDailyLimitExceeded_ThenNoSend() {
        when(redisTemplate.hasKey(INTERVAL_KEY)).thenReturn(false);
        when(valueOperations.increment(startsWith(SmsCodeService.DAILY_KEY + PHONE))).thenReturn(11L);

        assertThatThrownBy(() -> service.sendCode(PHONE, "login", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上限");

        verify(smsSender, never()).send(anyString(), anyString(), anyMap());
        verify(valueOperations, never()).set(eq(CODE_KEY), anyString(), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("发送 - 超过单 IP 每日上限拒绝")
    void shouldRejectSend_WhenIpLimitExceeded_ThenNoSend() {
        when(redisTemplate.hasKey(INTERVAL_KEY)).thenReturn(false);
        when(valueOperations.increment(startsWith(SmsCodeService.DAILY_KEY + PHONE))).thenReturn(1L);
        when(valueOperations.increment(startsWith(SmsCodeService.IP_DAILY_KEY + "10.0.0.1"))).thenReturn(51L);

        assertThatThrownBy(() -> service.sendCode(PHONE, "login", "10.0.0.1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上限");

        verify(smsSender, never()).send(anyString(), anyString(), anyMap());
    }

    @Test
    @DisplayName("发送 - 正常路径：6 位数字码写入 Redis、重置失败计数、设置间隔、调用通道")
    @SuppressWarnings("unchecked")
    void shouldSend_WhenAllowed_ThenStoreCodeAndNotify() {
        when(redisTemplate.hasKey(INTERVAL_KEY)).thenReturn(false);
        when(valueOperations.increment(startsWith(SmsCodeService.DAILY_KEY + PHONE))).thenReturn(1L);

        long expiresIn = service.sendCode(PHONE, "login", null);

        assertThat(expiresIn).isEqualTo(300L);
        // 首次计数要设置到当天结束的 TTL
        verify(redisTemplate).expire(startsWith(SmsCodeService.DAILY_KEY + PHONE), anyLong(), eq(TimeUnit.SECONDS));

        ArgumentCaptor<Object> codeCaptor = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations).set(eq(CODE_KEY), codeCaptor.capture(), eq(300L), eq(TimeUnit.SECONDS));
        String storedCode = codeCaptor.getValue().toString();
        assertThat(storedCode).matches("\\d{6}");

        verify(redisTemplate).delete(FAIL_KEY);
        verify(valueOperations).set(eq(INTERVAL_KEY), eq("1"), eq(60L), eq(TimeUnit.SECONDS));

        ArgumentCaptor<Map<String, String>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(smsSender).send(eq(PHONE), eq("SMS_LOGIN"), paramsCaptor.capture());
        assertThat(paramsCaptor.getValue()).containsEntry("code", storedCode);
    }

    @Test
    @DisplayName("发送 - 配置了阿里云模板编码时按场景取模板")
    void shouldUseConfiguredTemplate_WhenTemplateCodesSet() {
        properties.getAliyun().getTemplateCodes().put("register", "SMS_123");
        when(redisTemplate.hasKey(INTERVAL_KEY)).thenReturn(false);
        when(valueOperations.increment(anyString())).thenReturn(2L);

        service.sendCode(PHONE, "register", null);

        verify(smsSender).send(eq(PHONE), eq("SMS_123"), anyMap());
    }

    @Test
    @DisplayName("校验 - 验证码不存在或过期")
    void shouldRejectVerify_WhenCodeExpired_ThenCaptchaExpired() {
        when(valueOperations.get(CODE_KEY)).thenReturn(null);

        assertThatThrownBy(() -> service.verify(PHONE, "login", "123456"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.CAPTCHA_EXPIRED.getCode());
    }

    @Test
    @DisplayName("校验 - 正确则删除验证码与失败计数（一次性）")
    void shouldVerify_WhenCodeMatches_ThenDeleteKeys() {
        when(valueOperations.get(CODE_KEY)).thenReturn("654321");

        service.verify(PHONE, "login", " 654321 ");

        verify(redisTemplate).delete(CODE_KEY);
        verify(redisTemplate).delete(FAIL_KEY);
    }

    @Test
    @DisplayName("校验 - 错误时累计失败次数，首次设置 TTL，验证码仍保留")
    void shouldCountFailure_WhenCodeMismatch_ThenKeepCode() {
        when(valueOperations.get(CODE_KEY)).thenReturn("654321");
        when(valueOperations.increment(FAIL_KEY)).thenReturn(1L);

        assertThatThrownBy(() -> service.verify(PHONE, "login", "000000"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.CAPTCHA_ERROR.getCode());

        verify(redisTemplate).expire(FAIL_KEY, 300L, TimeUnit.SECONDS);
        verify(redisTemplate, never()).delete(CODE_KEY);
    }

    @Test
    @DisplayName("校验 - 连续错误 5 次后验证码作废")
    void shouldInvalidateCode_WhenFailuresReachLimit_ThenDeleteCode() {
        when(valueOperations.get(CODE_KEY)).thenReturn("654321");
        when(valueOperations.increment(FAIL_KEY)).thenReturn(5L);

        assertThatThrownBy(() -> service.verify(PHONE, "login", "000000"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.CAPTCHA_ERROR.getCode());

        verify(redisTemplate).delete(CODE_KEY);
        verify(redisTemplate).delete(FAIL_KEY);
        verify(redisTemplate, never()).expire(eq(FAIL_KEY), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("校验 - 场景隔离：注册码不能用于登录")
    void shouldRejectVerify_WhenSceneDiffers_ThenLookupOtherKey() {
        when(valueOperations.get(SmsCodeService.CODE_KEY + "register:" + PHONE)).thenReturn(null);

        assertThatThrownBy(() -> service.verify(PHONE, "register", "654321"))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ResultCode.CAPTCHA_EXPIRED.getCode());

        verify(valueOperations, never()).get(CODE_KEY);
    }

}
