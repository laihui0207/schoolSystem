package com.school.ai.controller;

import com.school.ai.dto.ChatRequest;
import com.school.ai.dto.MessageDto;
import com.school.ai.service.ChatService;
import com.school.ai.service.ConversationManager;
import com.school.common.result.Result;
import com.school.security.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ConversationManager conversationManager;

    /**
     * SSE 流式对话
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @RequestParam String message,
            @RequestParam String sessionId) {

        return chatService.chat(message, sessionId)
                .map(chunk -> ServerSentEvent.<String>builder().data(chunk).build())
                .onErrorResume(e -> Flux.just(
                        ServerSentEvent.<String>builder()
                                .event("error")
                                .data(e.getMessage())
                                .build()
                ));
    }

    /**
     * 非流式对话（备用）
     */
    @PostMapping("/message")
    public Result<String> message(@Valid @RequestBody ChatRequest request) {
        // ponytail: 收集 Flux 到字符串，简单场景用
        String response = chatService.chat(request.getMessage(), request.getSessionId())
                .collectList()
                .map(chunks -> String.join("", chunks))
                .block();
        return Result.success(response);
    }

    /**
     * 获取对话历史
     */
    @GetMapping("/history")
    public Result<List<MessageDto>> history(@RequestParam String sessionId) {
        return Result.success(conversationManager.getHistory(sessionId, 50));
    }

    /**
     * 清除会话
     */
    @DeleteMapping("/session")
    public Result<Void> clearSession(@RequestParam String sessionId) {
        conversationManager.clearSession(sessionId);
        return Result.success();
    }

}
