package com.smurthy.ai.rag.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * MCP Configuration (Model Context Protocol)
 *
 * TODO: Complete Wikipedia MCP integration with 0.15.0 SDK
 * Temporarily disabled to avoid compilation errors.
 */
@Configuration
public class McpConfiguration {

    private static final Logger log = LoggerFactory.getLogger(McpConfiguration.class);

    @Bean
    public List<ToolCallback> wikipediaMcpTools() {
        log.info("MCP tools temporarily disabled (integration in progress)");
        return List.of();
    }

    /**
     * Creates the OpenAiChatModel bean.
     *
     * @param apiKey The OpenAI API key, injected from application.yaml.
     * @return A configured instance of OpenAiChatModel.
     */
    @Bean
    public ChatLanguageModel chatLanguageModel(
            @Value("${langchain4j.open-ai.chat-model.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.chat-model.model-name}") String modelName,
            @Value("${langchain4j.open-ai.chat-model.temperature}") Double temperature) {

        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName(modelName)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(120))
                .build();
    }
}
