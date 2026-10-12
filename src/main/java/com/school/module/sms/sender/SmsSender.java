package com.school.module.sms.sender;

import java.util.Map;

/**
 * 短信发送通道
 * <p>
 * 实现只负责"把模板消息送出去"，频控、验证码生成与校验都在 {@code SmsCodeService}。
 *
 * @author School System
 */
public interface SmsSender {

    /**
     * 发送模板短信
     *
     * @param phone        手机号
     * @param templateCode 模板编码（按通道解释，log 通道仅打印）
     * @param params       模板参数，验证码放在 key "code"
     */
    void send(String phone, String templateCode, Map<String, String> params);

}
