package com.school.module.sms.sender;

import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.sms.config.SmsProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * 阿里云短信通道（配置骨架）
 * <p>
 * 本轮不引入阿里云 SDK（pom 不在本模块职责内），这里只做两件事：
 * 1. 构造时校验 access-key-id / access-key-secret / sign-name 必填，缺配置直接让启动失败；
 * 2. send 抛出明确的"通道未接入"异常，而不是假装发送成功。
 * 接入 SDK 时只需替换 {@link #send} 的实现。
 *
 * @author School System
 */
@Slf4j
public class AliyunSmsSender implements SmsSender {

    private final SmsProperties.Aliyun config;

    public AliyunSmsSender(SmsProperties.Aliyun config) {
        // ponytail: provider=aliyun 却没配密钥，宁可启动失败也不要上线后每次发码都 500；
        // 错误信息直接给出配置键名，省得翻文档
        if (config == null
                || !StringUtils.hasText(config.getAccessKeyId())
                || !StringUtils.hasText(config.getAccessKeySecret())
                || !StringUtils.hasText(config.getSignName())) {
            throw new IllegalStateException("app.sms.provider=aliyun 时必须配置 "
                    + "app.sms.aliyun.access-key-id / access-key-secret / sign-name"
                    + "（可用环境变量 SMS_ALIYUN_ACCESS_KEY_ID / SMS_ALIYUN_ACCESS_KEY_SECRET / SMS_ALIYUN_SIGN_NAME）");
        }
        this.config = config;
        log.info("阿里云短信通道已配置: region={}, signName={}", config.getRegion(), config.getSignName());
    }

    @Override
    public void send(String phone, String templateCode, Map<String, String> params) {
        // ponytail: 不打印 params（含验证码）；明确抛错而非静默吞掉，否则用户以为短信在路上
        log.error("阿里云短信 SDK 尚未接入，无法发送: phone={}, template={}", phone, templateCode);
        throw new BusinessException(ResultCode.SERVICE_UNAVAILABLE, "短信通道尚未接入，请联系管理员");
    }

}
