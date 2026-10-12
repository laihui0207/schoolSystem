package com.school.module.job.spi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;

/**
 * 任务参数工具：解析 sys_job.params 与按键取值
 * <p>
 * 供调度器、内置 handler 以及其他模块的 JobHandler 实现共用，避免每个 handler 各写一遍类型转换。
 *
 * @author School System
 */
public final class JobParams {

    // ponytail: 自建 ObjectMapper——容器里的实例可能带 Redis 的 defaultTyping，解析管理员手填的 JSON 不需要也不该受其影响
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private JobParams() {
    }

    /**
     * 解析 JSON 对象字符串；空白返回空 Map
     *
     * @throws IllegalArgumentException 不是合法 JSON 对象
     */
    public static Map<String, Object> parse(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = OBJECT_MAPPER.readValue(raw, MAP_TYPE);
            return parsed == null ? Collections.emptyMap() : Collections.unmodifiableMap(parsed);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("任务参数不是合法的 JSON 对象: " + e.getOriginalMessage(), e);
        }
    }

    /**
     * 读取整数参数；缺省或 null 返回默认值。数字与数字字符串都接受
     *
     * @throws IllegalArgumentException 值存在但不是整数
     */
    public static int intValue(Map<String, Object> params, String key, int defaultValue) {
        if (params == null) {
            return defaultValue;
        }
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("任务参数 " + key + " 必须是整数，当前值: " + value);
        }
    }

}
