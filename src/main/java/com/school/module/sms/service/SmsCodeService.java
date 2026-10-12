package com.school.module.sms.service;

import com.school.common.exception.BusinessException;
import com.school.common.result.ResultCode;
import com.school.module.sms.config.SmsProperties;
import com.school.module.sms.enums.SmsScene;
import com.school.module.sms.sender.SmsSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 短信验证码服务：频控、生成、存储、校验
 * <p>
 * Redis Key 约定：
 * <ul>
 *   <li>{@code sms:code:<scene>:<phone>} -> 6 位验证码，TTL = code-expire</li>
 *   <li>{@code sms:fail:<scene>:<phone>} -> 连续错误次数，TTL 与验证码同源</li>
 *   <li>{@code sms:interval:<phone>}     -> 发送间隔占位，TTL = send-interval</li>
 *   <li>{@code sms:daily:<phone>:<yyyyMMdd>} -> 当日发送次数，到当天 24:00 过期</li>
 *   <li>{@code sms:ip:<ip>:<yyyyMMdd>}   -> 单 IP 当日发送次数</li>
 * </ul>
 *
 * @author School System
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SmsCodeService {

    public static final String CODE_KEY = "sms:code:";
    public static final String FAIL_KEY = "sms:fail:";
    public static final String INTERVAL_KEY = "sms:interval:";
    public static final String DAILY_KEY = "sms:daily:";
    public static final String IP_DAILY_KEY = "sms:ip:";

    /**
     * 大陆手机号：1 开头，第二位 3-9，共 11 位
     */
    public static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int CODE_BOUND = 1_000_000;

    private final RedisTemplate<String, Object> redisTemplate;
    private final SmsSender smsSender;
    private final SmsProperties smsProperties;

    // ponytail: 验证码用 SecureRandom 而非 Random——6 位码本身熵就小，再可预测就等于没有
    private final SecureRandom random = new SecureRandom();

    /**
     * 发送验证码
     *
     * @param phone     手机号
     * @param sceneCode 场景：login | register | reset
     * @param clientIp  来源 IP（可为 null，为 null 时不做 IP 级限流）
     * @return 验证码有效期（秒），供前端展示
     */
    public long sendCode(String phone, String sceneCode, String clientIp) {
        String normalizedPhone = requireValidPhone(phone);
        SmsScene scene = requireScene(sceneCode);

        // 频控顺序：先查间隔再计日限，频繁点击不应消耗每日额度
        String intervalKey = INTERVAL_KEY + normalizedPhone;
        if (Boolean.TRUE.equals(redisTemplate.hasKey(intervalKey))) {
            throw new BusinessException(ResultCode.PARAM_ERROR,
                    "发送过于频繁，请 " + smsProperties.getSendInterval() + " 秒后再试");
        }
        enforceDailyLimit(DAILY_KEY + normalizedPhone + ":" + today(), smsProperties.getDailyLimit(),
                "该手机号今日发送次数已达上限");
        if (StringUtils.hasText(clientIp) && smsProperties.getIpDailyLimit() > 0) {
            enforceDailyLimit(IP_DAILY_KEY + clientIp + ":" + today(), smsProperties.getIpDailyLimit(),
                    "今日发送次数已达上限");
        }

        String code = generateCode();
        long expire = smsProperties.getCodeExpire();
        redisTemplate.opsForValue().set(codeKey(scene, normalizedPhone), code, expire, TimeUnit.SECONDS);
        // 新码重置错误计数，否则上一轮的 4 次错误会让新码只剩 1 次机会
        redisTemplate.delete(failKey(scene, normalizedPhone));
        redisTemplate.opsForValue().set(intervalKey, "1", smsProperties.getSendInterval(), TimeUnit.SECONDS);

        // ponytail: 先写 Redis 再发短信——发送抛异常时码已就位但用户收不到，只能等 TTL；
        // 反过来（先发后写）若写失败用户收到码却验不过，体验更差且更难排查
        smsSender.send(normalizedPhone, templateCodeFor(scene), Map.of("code", code));
        log.info("短信验证码已发送: phone={}, scene={}", maskPhone(normalizedPhone), scene.getCode());
        return expire;
    }

    /**
     * 校验验证码：成功即删除；失败累计，达到上限后作废该码
     */
    public void verify(String phone, String sceneCode, String code) {
        String normalizedPhone = requireValidPhone(phone);
        SmsScene scene = requireScene(sceneCode);
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ResultCode.CAPTCHA_ERROR, "请输入短信验证码");
        }

        String key = codeKey(scene, normalizedPhone);
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached == null) {
            throw new BusinessException(ResultCode.CAPTCHA_EXPIRED, "验证码已过期或尚未发送，请重新获取");
        }

        if (!Objects.equals(code.trim(), cached.toString())) {
            recordFailure(scene, normalizedPhone, key);
            throw new BusinessException(ResultCode.CAPTCHA_ERROR, "短信验证码错误");
        }

        // ponytail: 一次性——验证成功立刻删除，防止同一个码在有效期内被重放登录多次
        redisTemplate.delete(key);
        redisTemplate.delete(failKey(scene, normalizedPhone));
    }

    /**
     * 校验手机号格式并返回去空格后的值
     */
    public String requireValidPhone(String phone) {
        String trimmed = phone == null ? "" : phone.trim();
        if (!PHONE_PATTERN.matcher(trimmed).matches()) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "手机号格式不正确");
        }
        return trimmed;
    }

    private SmsScene requireScene(String sceneCode) {
        SmsScene scene = SmsScene.fromCode(sceneCode);
        if (scene == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID, "scene 无效，可选 login / register / reset");
        }
        return scene;
    }

    /**
     * 计数 +1 并在首次时设置到当天结束的 TTL；超限抛错
     */
    private void enforceDailyLimit(String key, int limit, String message) {
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, secondsUntilMidnight(), TimeUnit.SECONDS);
        }
        if (count != null && count > limit) {
            throw new BusinessException(ResultCode.PARAM_ERROR, message);
        }
    }

    private void recordFailure(SmsScene scene, String phone, String codeKey) {
        String failKey = failKey(scene, phone);
        Long failures = redisTemplate.opsForValue().increment(failKey);
        if (failures != null && failures == 1L) {
            redisTemplate.expire(failKey, smsProperties.getCodeExpire(), TimeUnit.SECONDS);
        }
        if (failures != null && failures >= smsProperties.getMaxVerifyFailures()) {
            // ponytail: 6 位数字码最多 100 万种，不设上限就能在 5 分钟内暴力穷举；作废后必须重新发送
            redisTemplate.delete(codeKey);
            redisTemplate.delete(failKey);
            log.warn("短信验证码连续错误 {} 次已作废: phone={}, scene={}", failures, maskPhone(phone), scene.getCode());
        }
    }

    private String generateCode() {
        return String.format("%06d", random.nextInt(CODE_BOUND));
    }

    private String templateCodeFor(SmsScene scene) {
        Map<String, String> templates = smsProperties.getAliyun().getTemplateCodes();
        String configured = templates == null ? null : templates.get(scene.getCode());
        return StringUtils.hasText(configured) ? configured : "SMS_" + scene.name();
    }

    static String codeKey(SmsScene scene, String phone) {
        return CODE_KEY + scene.getCode() + ":" + phone;
    }

    static String failKey(SmsScene scene, String phone) {
        return FAIL_KEY + scene.getCode() + ":" + phone;
    }

    private static String today() {
        return LocalDate.now().format(DAY_FORMAT);
    }

    private static long secondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.MIDNIGHT);
        return Math.max(1, Duration.between(now, midnight).getSeconds());
    }

    /**
     * 日志里只留前 3 后 4 位
     */
    private static String maskPhone(String phone) {
        return phone.length() == 11 ? phone.substring(0, 3) + "****" + phone.substring(7) : "***";
    }

}
