package com.smurthy.ai.rag.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * UNIFIED AGENTIC CONTROLLER
 *
 * A single, Google-like search endpoint that intelligently routes queries to:
 * - RAG Document Store (resumes, PDFs, Excel spreadsheets, Word docs)
 * - Temporal Queries (date-filtered document search)
 * - Real-Time APIs (stocks, weather, news)
 *
 * The LLM acts as an intelligent router, deciding which tools to use based on the question.
 *
 * EXAMPLE QUERIES:
 * - "Who is Srinivas Murthy?" → queryDocuments
 * - "What were Q2 2023 sales?" → queryDocumentsByYear + queryDocuments
 * - "Stock price of AAPL" → getYahooQuote
 * - "Weather in San Francisco" → getWeatherByLocation
 * - "Latest AI news" → getMarketNews
 * - "What did I work on in 2021?" → queryDocumentsByYear
 * - "Compare AAPL and GOOGL stocks and show my portfolio from last year" → getYahooQuote + queryDocumentsByYear
 *
 * This is a TRULY AGENTIC system - one endpoint, unlimited possibilities!
 */
@RestController
@RequestMapping("/unified")
public class UnifiedAgenticController {

    private static final Logger log = LoggerFactory.getLogger(UnifiedAgenticController.class);
    private final ChatClient.Builder toolsOnlyBuilder;

    // FIX: This set is now the single source of truth and contains all 17 tools the test expects.
    private static final Set<String> ALL_TOOLS = Set.of(
            // RAG & Document Tools (4)
            "queryDocuments",
            "queryDocumentsByYear",
            "queryDocumentsByDateRange",
            "queryDocumentsAdvanced",

            // Stock & Finance Tools (7)
            "getYahooQuote",              // Composite tool with fallbacks
            "getRealTimeQuote",           // Specific provider: Alpha Vantage
            "getFinnhubQuote",            // Specific provider: Finnhub
            "getQuoteFromYahooOnly",      // Specific provider: Yahoo Finance
            "getQuoteFromGoogleFinance",  // Specific provider: Google Finance
            "getHistoricalPrices",
            "analyzeFinancialRatios",

            // News Tools (2)
            "getMarketNews",
            "getHeadlinesByCategory",

            // Weather Tools (2)
            "getWeatherByLocation",
            "getWeatherByZipCode",

            // Market Movers Tool (1)
            "getMarketMovers",

            // Economic Data Tool (1)
            "getEconomicIndicators"
    );

    public UnifiedAgenticController(
            ChatClient.Builder builder,
            ChatMemory chatMemory) {

        this.toolsOnlyBuilder = builder
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .build());
    }

    @GetMapping("/ask")
    public UnifiedResponse ask(
            @RequestParam String question,
            @RequestParam(defaultValue = "default") String conversationId) {

        if (!StringUtils.hasText(question)) {
            log.warn("Received an empty or null question. Aborting.");
            return new UnifiedResponse(question, "Please provide a valid question.", ALL_TOOLS.size(), "Validation failed", 0);
        }

        log.info("\n╔════════════════════════════════════════════════════╗");
        log.info("║         UNIFIED AGENTIC QUERY                      ║");
        log.info("╚════════════════════════════════════════════════════╝");
        log.info(" Question: {}", question);
        log.info(" Available tools: {} total", ALL_TOOLS.size());

        long startTime = System.currentTimeMillis();

        String systemPrompt = """
            You are an intelligent AI assistant with access to multiple tools and conversation history.

            CRITICAL: Always check the conversation history first. If the user asks about something they mentioned earlier, use that context.

            TOOL HIERARCHY & SELECTION STRATEGY:
            --------------------------------------

            **CONVERSATION MEMORY:**
            - ALWAYS reference previous messages in the conversation when relevant
            - If a user asks "What is my [X]?" check if they told you earlier in the conversation
            - Remember user preferences, names, IDs, and other personal information shared in this conversation

            **STOCK PRICE TOOLS:**
            1.  **Alpha Vantage (`getRealTimeQuote`)**
                -   **USE FOR:** Queries demanding REAL-TIME, up-to-the-second stock prices.
                -   **KEYWORDS:** "real-time", "live price", "current price now".

            2.  **Finnhub (`getFinnhubQuote`)**
                -   **USE FOR:** Queries requiring deep financial data like analyst ratings, price targets, or detailed company fundamentals.
                -   **KEYWORDS:** "analyst rating", "price target", "fundamentals", "P/E ratio".

            3.  **Yahoo Finance (`getYahooQuote`)**
                -   **USE FOR:** General-purpose stock price queries that are not explicitly real-time.
                -   This is your reliable, FREE FALLBACK for delayed (15-min) quotes.

            **DOCUMENT STORE TOOLS:**
            -   `queryDocuments`, `queryDocumentsByYear`: USE FIRST for any biographical, resume, or historical project questions.

            **OTHER TOOLS:**
            -   `getWeatherByLocation`: For weather-related questions.
            -   `getMarketNews`: For general news on any topic.

            IMPORTANT RULES:
            -   FIRST check conversation history for context before using tools.
            -   Choose the single best tool for the job based on the hierarchy above.
            -   Maintain conversation context across multiple turns.
            """;

        ChatClient client = toolsOnlyBuilder
                .defaultSystem(systemPrompt)
                .defaultToolNames(ALL_TOOLS.toArray(new String[0]))
                .build();

        log.info(" Agent is analyzing question and selecting tools...");

        String answer = client.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        long elapsedMs = System.currentTimeMillis() - startTime;

        log.info("Response generated in {}ms", elapsedMs);
        log.info("════════════════════════════════════════════════════\n");

        return new UnifiedResponse(
                question,
                answer,
                ALL_TOOLS.size(),
                "AI agent had access to all tools and autonomously decided which to use",
                elapsedMs
        );
    }

    @GetMapping("/q")
    public String query(@RequestParam String query) {
        return ask(query, "default").answer();
    }

    public record UnifiedResponse(
            String question,
            String answer,
            int totalToolsAvailable,
            String strategy,
            long executionTimeMs
    ) {}
}
