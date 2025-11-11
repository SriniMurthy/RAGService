package com.smurthy.ai.rag.agents;

import com.smurthy.ai.rag.orchestration.ToolOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Specialized Agent for Weather Queries
 *
 * Handles weather-related questions.
 * Intelligently routes between Spring AI tools and Langchain4j MCP agents.
 */
@Component
public class WeatherAgent {

    private static final Logger log = LoggerFactory.getLogger(WeatherAgent.class);
    private final ChatClient weatherChatClient;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private ToolOrchestrator orchestrator;

    // Only weather tools
    private static final Set<String> WEATHER_TOOLS = Set.of(
            "getWeatherByLocation",
            "getWeatherByZipCode"
    );

    private static final String SYSTEM_PROMPT = """
            You are a specialized Weather AI agent with expertise in weather data and forecasts.

            YOUR CAPABILITIES:
            - Fetch current weather by location or zip code
            - Provide temperature and weather conditions
            - Weather forecasts

            RULES:
            - Provide accurate, current weather information
            - Cite the location/source
            - Be concise
            - If asked about non-weather topics, politely decline

            Use your weather tools to fetch real-time data.
            """;

    public WeatherAgent(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.weatherChatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultToolNames(WEATHER_TOOLS.toArray(new String[0]))
                .build();

        log.info("WeatherAgent initialized with {} tools", WEATHER_TOOLS.size());
    }

    /**
     * Execute a weather query
     *
     * Intelligently routes between:
     * - Spring AI weather tools (fast, direct)
     * - Langchain4j Weather MCP agent (reasoning)
     * - External MCP servers (if configured)
     *
     * @param question The user's question
     * @param conversationId Conversation ID for memory
     * @return AgentResult with weather data
     */
    public AgentResult execute(String question, String conversationId) {
        log.debug("[WeatherAgent] Processing: {}", question);
        long startTime = System.currentTimeMillis();

        try {
            String result;

            // Use unified orchestrator if available (Spring AI + Langchain4j)
            if (orchestrator != null) {
                log.debug("[WeatherAgent] Using ToolOrchestrator for intelligent routing");
                ToolOrchestrator.OrchestrationResult orchResult = orchestrator.execute(question);
                result = orchResult.result();
                log.info("[WeatherAgent] Orchestrator routed to: {} ({}ms)",
                    orchResult.framework(), orchResult.executionTimeMs());
            } else {
                // Fallback to direct Spring AI (backward compatibility)
                log.debug("[WeatherAgent] Using direct Spring AI ChatClient");
                result = weatherChatClient.prompt()
                        .user(question)
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                        .call()
                        .content();
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[WeatherAgent] Completed in {}ms", elapsed);

            return AgentResult.success("WeatherAgent", result, elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[WeatherAgent] Error processing query", e);
            return AgentResult.failure("WeatherAgent",
                    "Failed to fetch weather data: " + e.getMessage(), elapsed);
        }
    }
}