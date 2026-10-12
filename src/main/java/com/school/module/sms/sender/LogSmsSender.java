package com.school.module.sms.sender;

import com.school.module.sms.config.SmsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 日志短信通道（默认）
 * <p>
 * 不真正发短信：验证码以 INFO 打到日志，并额外写入 Redis {@code sms:last:<phone>}，
 * 本地联调可以 {@code redis-cli GET sms:last:13800000000} 直接拿到最近一条验证码。
 *
 * @author School System
 */
@Slf4j
@RequiredArgsConstructor
public class LogSmsSender implements SmsSender {

    /**
     * 最近一次发送内容的 Redis Key 前缀：sms:last:&lt;phone&gt; -> 模板参数
     */
    public static final String LAST_SENT_KEY = "sms:last:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final SmsProperties smsProperties;

    @Override
    public void send(String phone, String templateCode, Map<String, String> params) {
        // ponytail: 验证码在 SmsCodeService 里已按场景存了一份，这里再按手机号存"最近一条"，
        // 是为了让联调的人不用知道场景名也能查到码；两份 TTL 同源
        log.info("[SMS-LOG] 模拟发送短信 phone={}, template={}, params={}", phone, templateCode, params);
        redisTemplate.opsForValue().set(LAST_SENT_KEY + phone, params,
                smsProperties.getCodeExpire(), TimeUnit.SECONDS);
    }

}
