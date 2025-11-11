package com.smurthy.ai.rag.config;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;



/**
 * Main configuration class
 */
@Configuration
@EnableRetry
@EnableConfigurationProperties(EmbeddingConfig.class)
public class RAGConfiguration {

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(8)  // Reduced from 20 to 8 to stay under 30K token limit
                .build();
    }
}
