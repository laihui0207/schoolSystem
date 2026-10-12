package com.school.module.config.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 成绩规则 / 系统开关读取服务实现
 * <p>
 * ponytail: 用「值 + 加载时间戳」做 60 秒本地缓存而不是 @Cacheable——这几个键在登录、统计、改分等热路径上被读，
 * 不该每次都打 sys_config；又不想为 4 个键引入 Spring Cache 配置和 Redis 序列化问题。
 *
 * @author School System
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreRuleServiceImpl implements ScoreRuleService {

    static final String KEY_PASS_LINE = "score.pass.line";
    static final String KEY_EXCELLENT_LINE = "score.excellent.line";
    static final String KEY_CHANGE_APPROVAL = "score.change.approval";
    static final String KEY_MAINTENANCE = "system.maintenance";

    static final BigDecimal DEFAULT_PASS_RATIO = new BigDecimal("0.60");
    static final BigDecimal DEFAULT_EXCELLENT_RATIO = new BigDecimal("0.85");
    static final boolean DEFAULT_CHANGE_APPROVAL = true;
    static final boolean DEFAULT_MAINTENANCE = false;

    /** 缓存有效期（毫秒） */
    static final long CACHE_TTL_MILLIS = 60_000L;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final int RATIO_SCALE = 4;
    private static final Set<String> TRUE_WORDS = Set.of("true", "1", "yes", "on", "是");
    private static final Set<String> FALSE_WORDS = Set.of("false", "0", "no", "off", "否");

    private final ConfigService configService;

    /** key → 已加载的原始值（可为 null，表示库里没有该键，同样缓存以免反复查） */
    private final Map<String, CachedValue> cache = new ConcurrentHashMap<>();

    private record CachedValue(String value, long loadedAt) {
    }

    @Override
    public BigDecimal passRatio() {
        return readRatio(KEY_PASS_LINE, DEFAULT_PASS_RATIO);
    }

    @Override
    public BigDecimal excellentRatio() {
        return readRatio(KEY_EXCELLENT_LINE, DEFAULT_EXCELLENT_RATIO);
    }

    @Override
    public boolean changeApprovalRequired() {
        return readFlag(KEY_CHANGE_APPROVAL, DEFAULT_CHANGE_APPROVAL);
    }

    @Override
    public boolean maintenanceMode() {
        return readFlag(KEY_MAINTENANCE, DEFAULT_MAINTENANCE);
    }

    @Override
    public void evictCache() {
        cache.clear();
    }

    /**
     * 百分数配置 → 比例：合法范围 [0, 100]，越界或非数字回默认值并 warn
     */
    private BigDecimal readRatio(String key, BigDecimal defaultRatio) {
        String raw = rawValue(key);
        if (!StringUtils.hasText(raw)) {
            return defaultRatio;
        }
        try {
            BigDecimal percent = new BigDecimal(raw.trim());
            if (percent.compareTo(BigDecimal.ZERO) < 0 || percent.compareTo(HUNDRED) > 0) {
                log.warn("配置 {} 的值 {} 超出 0-100，使用默认比例 {}", key, raw, defaultRatio);
                return defaultRatio;
            }
            return percent.divide(HUNDRED, RATIO_SCALE, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            log.warn("配置 {} 的值 {} 不是数字，使用默认比例 {}", key, raw, defaultRatio);
            return defaultRatio;
        }
    }

    /**
     * 布尔配置：接受 true/false、1/0、yes/no、on/off、是/否（忽略大小写），其他值回默认并 warn
     */
    private boolean readFlag(String key, boolean defaultFlag) {
        String raw = rawValue(key);
        if (!StringUtils.hasText(raw)) {
            return defaultFlag;
        }
        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        if (TRUE_WORDS.contains(normalized)) {
            return true;
        }
        if (FALSE_WORDS.contains(normalized)) {
            return false;
        }
        log.warn("配置 {} 的值 {} 不是合法布尔值，使用默认值 {}", key, raw, defaultFlag);
        return defaultFlag;
    }

    /**
     * 带 TTL 的原始值读取；读库异常时沿用旧缓存（没有则视为缺失），不让配置表故障拖垮登录/统计
     */
    private String rawValue(String key) {
        long now = System.currentTimeMillis();
        CachedValue cached = cache.get(key);
        if (cached != null && now - cached.loadedAt() < CACHE_TTL_MILLIS) {
            return cached.value();
        }
        try {
            String value = configService.getConfigByKey(key);
            cache.put(key, new CachedValue(value, now));
            return value;
        } catch (RuntimeException e) {
            log.warn("读取配置 {} 失败，{}: {}", key, cached != null ? "沿用旧值" : "视为未配置", e.getMessage());
            return cached != null ? cached.value() : null;
        }
    }

}
