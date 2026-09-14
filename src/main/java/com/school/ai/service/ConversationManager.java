package com.school.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.school.ai.dto.MessageDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationManager {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "chat:session:";
    private static final Duration TTL = Duration.ofDays(7);

    public void saveMessage(String sessionId, String role, String content) {
        String key = KEY_PREFIX + sessionId;
        try {
            String json = objectMapper.writeValueAsString(new MessageDto(role, content, Instant.now()));
            redisTemplate.opsForList().rightPush(key, json);
            redisTemplate.expire(key, TTL);
        } catch (JsonProcessingException e) {
            log.error("保存对话消息失败: sessionId={}", sessionId, e);
        }
    }

    public List<MessageDto> getHistory(String sessionId, int limit) {
        String key = KEY_PREFIX + sessionId;
        List<Object> raw = redisTemplate.opsForList().range(key, -limit, -1);
        if (raw == null) return Collections.emptyList();
        return raw.stream()
                .map(obj -> {
                    try {
                        return objectMapper.readValue(obj.toString(), MessageDto.class);
                    } catch (JsonProcessingException e) {
                        log.warn("反序列化消息失败: {}", obj);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public void clearSession(String sessionId) {
        redisTemplate.delete(KEY_PREFIX + sessionId);
    }

}
