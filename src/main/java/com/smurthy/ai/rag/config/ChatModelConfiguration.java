package com.smurthy.ai.rag.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configuration to resolve ambiguity when multiple ChatModel beans exist.
 *
 * With both OpenAI and Anthropic configured, Spring Boot's ChatClient.Builder
 * autoconfiguration doesn't know which ChatModel to use. We mark OpenAI as @Primary
 * so existing controllers continue to work, while UnifiedClaudeController explicitly
 * uses @Qualifier("anthropicChatModel") to get the Anthropic model.
 */
@Configuration
public class ChatModelConfiguration {

    /**
     * Make OpenAI the primary ChatModel for default ChatClient.Builder injection.
     * This ensures existing controllers (UnifiedAgenticController, RAGDataController, etc.)
     * continue using OpenAI without modification.
     */
    @Bean
    @Primary
    public ChatModel primaryChatModel(@Qualifier("openAiChatModel") ChatModel openAiChatModel) {
        return openAiChatModel;
    }
}