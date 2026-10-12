package com.school.ai.config;

import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@Configuration
public class AiConfig {

    @Value("${spring.ai.provider:deepseek}")
    private String aiProvider;

    /**
     * 根据配置选择主用模型
     * DeepSeek bean name: deepSeekChatModel（spring-ai-starter-model-deepseek 的 DeepSeekChatAutoConfiguration 注册）
     * Ollama  bean name: ollamaChatModel
     */
    @Bean
    @Primary
    public ChatModel activeChatModel(
            // ponytail: pom 只有 deepseek/ollama starter，没有 openai starter；DeepSeek 自动配置注册的 bean 名是 deepSeekChatModel，
            // 之前写成 openAiChatModel 永远注入 null，provider=deepseek 时静默回退到 Ollama
            @Autowired(required = false) @Qualifier("deepSeekChatModel") ChatModel deepSeekModel,
            @Autowired(required = false) @Qualifier("ollamaChatModel") ChatModel ollamaModel) {
        if ("ollama".equalsIgnoreCase(aiProvider)) {
            if (ollamaModel == null) {
                throw new IllegalStateException(
                        "AI provider is 'ollama' but OllamaChatModel is not available. "
                                + "Configure spring.ai.ollama.base-url (OLLAMA_BASE_URL).");
            }
            log.info("AI provider: ollama (ollamaChatModel)");
            return ollamaModel;
        }
        // ponytail: provider=deepseek 而 bean 缺失时直接报错，不再静默回退 Ollama——回退会让"配置错了"伪装成"模型答得差"
        if (deepSeekModel == null) {
            throw new IllegalStateException(
                    "AI provider is 'deepseek' but DeepSeekChatModel is not available. "
                            + "Configure DEEPSEEK_API_KEY (spring.ai.deepseek.api-key) or set AI_PROVIDER=ollama.");
        }
        log.info("AI provider: deepseek (deepSeekChatModel)");
        return deepSeekModel;
    }
}
