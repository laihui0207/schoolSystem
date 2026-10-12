package com.school.module.sms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 短信验证码配置（app.sms.*）
 * <p>
 * provider=log（默认）只把验证码打到日志并写 Redis，供本地联调；
 * provider=aliyun 需要 access-key 等，缺配置时启动即报错，避免线上静默发不出去。
 *
 * @author School System
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.sms")
public class SmsProperties {

    /**
     * 短信通道：log | aliyun
     */
    private String provider = "log";

    /**
     * 验证码有效期（秒）
     */
    private long codeExpire = 300;

    /**
     * 同一手机号两次发送的最小间隔（秒）
     */
    private long sendInterval = 60;

    /**
     * 同一手机号每日发送上限
     */
    private int dailyLimit = 10;

    /**
     * 同一来源 IP 每日发送上限（防对任意手机号的短信轰炸；0 表示不限制）
     */
    private int ipDailyLimit = 50;

    /**
     * 验证码连续错误多少次后作废
     */
    private int maxVerifyFailures = 5;

    /**
     * 是否开放学生/家长自助注册
     */
    private boolean registerEnabled = true;

    /**
     * 阿里云短信配置（provider=aliyun 时必填）
     */
    private Aliyun aliyun = new Aliyun();

    @Data
    public static class Aliyun {

        private String accessKeyId;

        private String accessKeySecret;

        /**
         * 短信签名
         */
        private String signName;

        /**
         * 区域节点，默认华东 1
         */
        private String region = "cn-hangzhou";

        /**
         * 场景 -> 模板编码（login / register / reset）；缺省时用场景名占位
         */
        private Map<String, String> templateCodes = new HashMap<>();
    }

}
