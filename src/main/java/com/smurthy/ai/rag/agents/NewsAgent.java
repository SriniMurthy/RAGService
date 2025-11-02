package com.smurthy.ai.rag.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Specialized Agent for News and Current Events
 *
 * Handles news queries, current events, headlines.
 * Has access ONLY to news tools.
 */
@Component
public class NewsAgent {

    private static final Logger log = LoggerFactory.getLogger(NewsAgent.class);
    private final ChatClient newsChatClient;

    // Only news tools
    private static final Set<String> NEWS_TOOLS = Set.of(
            "getMarketNews",
            "getHeadlinesByCategory"
    );

    private static final String SYSTEM_PROMPT = """
            You are a specialized News AI agent with expertise in current events and news analysis.

            YOUR CAPABILITIES:
            - Fetch latest market news
            - Retrieve headlines by category
            - Analyze news trends and events
            - Provide context on current events (e.g., political meetings, market happenings)

            RULES:
            - Provide timely, accurate news information
            - Always cite the news source
            - Be objective and factual
            - If asked about historical data or documents, politely decline and suggest appropriate tools

            Use your news tools to fetch real-time information.
            """;

    public NewsAgent(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.newsChatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultToolNames(NEWS_TOOLS.toArray(new String[0]))
                .build();

        log.info("NewsAgent initialized with {} tools", NEWS_TOOLS.size());
    }

    /**
     * Execute a news query
     *
     * @param question The user's question
     * @param conversationId Conversation ID for memory
     * @return AgentResult with news data
     */
    public AgentResult execute(String question, String conversationId) {
        log.debug("[NewsAgent] Processing: {}", question);
        long startTime = System.currentTimeMillis();

        try {
            String result = newsChatClient.prompt()
                    .user(question)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[NewsAgent] Completed in {}ms", elapsed);

            return AgentResult.success("NewsAgent", result, elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[NewsAgent] Error processing query", e);
            return AgentResult.failure("NewsAgent",
                    "Failed to fetch news: " + e.getMessage(), elapsed);
        }
    }
}