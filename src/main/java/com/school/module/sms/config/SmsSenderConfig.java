package com.school.module.sms.config;

import com.school.module.sms.sender.AliyunSmsSender;
import com.school.module.sms.sender.LogSmsSender;
import com.school.module.sms.sender.SmsSender;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

/**
 * 按 app.sms.provider 装配短信通道
 *
 * @author School System
 */
@Configuration
public class SmsSenderConfig {

    /**
     * ponytail: 用 @Bean 显式 switch 而不是两个 @ConditionalOnProperty 组件——
     * 拼错 provider 时后者只会报一个晦涩的 "No qualifying bean of type SmsSender"，
     * 这里能在启动日志里直接说出哪个值不被支持。
     */
    @Bean
    public SmsSender smsSender(SmsProperties smsProperties, RedisTemplate<String, Object> redisTemplate) {
        String provider = smsProperties.getProvider() == null ? "log" : smsProperties.getProvider().trim().toLowerCase();
        return switch (provider) {
            case "log" -> new LogSmsSender(redisTemplate, smsProperties);
            case "aliyun" -> new AliyunSmsSender(smsProperties.getAliyun());
            default -> throw new IllegalStateException(
                    "app.sms.provider 不支持的取值: " + smsProperties.getProvider() + "，可选 log | aliyun");
        };
    }

}
