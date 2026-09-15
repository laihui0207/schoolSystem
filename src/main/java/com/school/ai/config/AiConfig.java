package com.school.ai.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * AI 模型选择配置
 * 通过 spring.ai.provider=deepseek|ollama 切换底层模型
 */
@Configuration
public class AiConfig {

    @Value("${spring.ai.provider:deepseek}")
    private String aiProvider;

    /**
     * 根据配置选择主用模型
     * DeepSeek bean name: deepSeekChatModel
     * Ollama  bean name: ollamaChatModel
     */
    @Bean
    @Primary
    public ChatModel activeChatModel(
            @Autowired(required = false) @Qualifier("openAiChatModel") ChatModel openAiModel,
            @Autowired(required = false) @Qualifier("ollamaChatModel") ChatModel ollamaModel) {
        if ("ollama".equalsIgnoreCase(aiProvider)) {
            if (ollamaModel == null) {
                throw new IllegalStateException(
                        "AI provider is 'ollama' but OllamaChatModel is not available.");
            }
            return ollamaModel;
        }
        // deepseek uses OpenAI-compatible API via spring-ai-openai-starter
        if (openAiModel != null) return openAiModel;
        if (ollamaModel != null) return ollamaModel;
        throw new IllegalStateException(
                "No ChatModel available. Configure spring.ai.openai.api-key or spring.ai.ollama.base-url.");
    }
}
