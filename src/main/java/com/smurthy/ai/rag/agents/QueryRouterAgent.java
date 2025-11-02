package com.smurthy.ai.rag.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

/**
 * LLM-based Query Router Agent
 *
 * Analyzes user queries to determine which specialized agents should be invoked.
 * Uses structured JSON output for reliable parsing.
 */
@Component
public class QueryRouterAgent {

    private static final Logger log = LoggerFactory.getLogger(QueryRouterAgent.class);
    private final ChatClient routerChatClient;
    private final ObjectMapper objectMapper;

    private static final String ROUTING_SYSTEM_PROMPT = """
            You are a query routing specialist. Analyze user queries and determine which specialized agents are needed.

            AVAILABLE AGENTS:

            1. **FinancialAgent** - Use for:
               - Stock prices, quotes, ticker symbols (e.g., "AAPL price", "what's TSLA trading at")
               - Market predictions, analysis, trends
               - Financial ratios, company fundamentals
               - Portfolio analysis
               - Economic indicators

            2. **ResearchAgent** - Use for:
               - Questions about user's uploaded documents
               - RAG knowledge base queries
               - "What's in my documents about X?"
               - "Search my files for Y"

            3. **NewsAgent** - Use for:
               - Current events, breaking news
               - Political events (e.g., "Trump Xi meeting")
               - Market news, headlines
               - Recent happenings

            4. **WeatherAgent** - Use for:
               - Weather queries
               - Temperature, forecast
               - Weather by location or zip code

            IMPORTANT RULES:
            - A query can need MULTIPLE agents (e.g., "Compare NVDA in my docs to current price" needs Research + Financial)
            - If unsure, err on the side of including an agent
            - Conversation history questions (e.g., "What is my favorite stock?") need NO agents - they're answered from memory

            Respond ONLY with valid JSON in this exact format:
            {
              "needsFinancial": true,
              "needsResearch": false,
              "needsNews": false,
              "needsWeather": false,
              "reasoning": "Query asks for stock price, requires FinancialAgent"
            }
            """;

    public QueryRouterAgent(ChatClient.Builder chatClientBuilder, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;

        // Build a lightweight router client (no tools, no memory needed for routing)
        this.routerChatClient = chatClientBuilder
                .defaultSystem(ROUTING_SYSTEM_PROMPT)
                .build();

        log.info("QueryRouterAgent initialized with LLM-based routing");
    }

    /**
     * Analyze a user query and determine which agents should handle it
     *
     * @param question The user's question
     * @return QueryIntent with agent routing decisions
     */
    public QueryIntent analyze(String question) {
        log.debug("Routing query: {}", question);

        String routingPrompt = String.format("""
                Analyze this query:

                "%s"

                Respond with JSON indicating which agents are needed.
                """, question);

        try {
            String response = routerChatClient.prompt()
                    .user(routingPrompt)
                    .call()
                    .content();

            log.debug("Router LLM response: {}", response);

            // Parse JSON response
            QueryIntent intent = parseQueryIntent(response);
            log.info("Routing decision for '{}': {} | Reasoning: {}",
                    question, intent.getAgentSummary(), intent.reasoning());

            return intent;

        } catch (Exception e) {
            log.error("Error in query routing, defaulting to safe fallback", e);
            // Fallback: activate all agents if routing fails
            return new QueryIntent(true, true, true, true,
                    "Routing failed, activating all agents as fallback");
        }
    }

    /**
     * Parse LLM's JSON response into QueryIntent
     */
    private QueryIntent parseQueryIntent(String jsonResponse) throws JsonProcessingException {
        // Clean up response - sometimes LLMs add markdown code blocks
        String cleanJson = jsonResponse
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

        // Parse JSON
        var node = objectMapper.readTree(cleanJson);

        return new QueryIntent(
                node.get("needsFinancial").asBoolean(),
                node.get("needsResearch").asBoolean(),
                node.get("needsNews").asBoolean(),
                node.get("needsWeather").asBoolean(),
                node.get("reasoning").asText()
        );
    }
}