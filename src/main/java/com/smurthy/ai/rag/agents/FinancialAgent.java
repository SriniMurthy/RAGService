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
 * Specialized Agent for Financial Queries
 *
 * Handles stock prices, market analysis, predictions, economic indicators.
 * Intelligently routes between Spring AI tools and Langchain4j MCP agents.
 */
@Component
public class FinancialAgent {

    private static final Logger log = LoggerFactory.getLogger(FinancialAgent.class);
    private final ChatClient financialChatClient;

    @Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private ToolOrchestrator orchestrator;

    // Only financial tools
    private static final Set<String> FINANCIAL_TOOLS = Set.of(
            "getYahooQuote",
            "getRealTimeQuote",
            "getFinnhubQuote",
            "getQuoteFromYahooOnly",
            "getQuoteFromGoogleFinance",
            "getHistoricalPrices",
            "analyzeFinancialRatios",
            "getMarketMovers",
            "getEconomicIndicators"
    );

    private static final String SYSTEM_PROMPT = """
            You are a specialized Financial AI agent with expertise in stocks, markets, and economic data.

            YOUR CAPABILITIES:
            - Real-time stock prices and quotes
            - Futures and indices (S&P 500, Nasdaq, Dow Jones)
            - Financial ratio analysis (P/E, ROE, debt ratios)
            - Market predictions and trends
            - Economic indicators (GDP, inflation)
            - Portfolio analysis

            CRITICAL: When user asks about futures or indices:
            - "S&P futures" or "S&P 500 futures" → Use getMarketMovers with market="SP500" to show top movers
            - "Nasdaq futures" → Use getMarketMovers with market="NASDAQ"
            - "market futures" or "futures" → Use getMarketMovers to show overall market movement

            RULES:
            - ALWAYS call tools - do NOT say "unable to retrieve" without trying
            - If a direct quote fails, try getMarketMovers for that market
            - Provide accurate, data-driven financial information
            - Always cite the tool/source used (e.g., "According to Yahoo Finance...")
            - Be concise but thorough
            - If asked about non-financial topics, politely decline and suggest appropriate resources

            Use your tools to fetch real-time data. Do NOT make up numbers.
            """;

    public FinancialAgent(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.financialChatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultToolNames(FINANCIAL_TOOLS.toArray(new String[0]))
                .build();

        log.info("FinancialAgent initialized with {} tools", FINANCIAL_TOOLS.size());
    }

    /**
     * Execute a financial query
     *
     * Intelligently routes between:
     * - Spring AI financial tools (fast, direct)
     * - Langchain4j Finance MCP agent (reasoning)
     * - External MCP servers (if configured)
     *
     * @param question The user's question
     * @param conversationId Conversation ID for memory
     * @return AgentResult with financial data
     */
    public AgentResult execute(String question, String conversationId) {
        log.debug("[FinancialAgent] Processing: {}", question);
        long startTime = System.currentTimeMillis();

        try {
            String result;

            // Use unified orchestrator if available (Spring AI + Langchain4j)
            if (orchestrator != null) {
                log.debug("[FinancialAgent] Using ToolOrchestrator for intelligent routing");
                ToolOrchestrator.OrchestrationResult orchResult = orchestrator.execute(question);
                result = orchResult.result();
                log.info("[FinancialAgent] Orchestrator routed to: {} ({}ms)",
                    orchResult.framework(), orchResult.executionTimeMs());
            } else {
                // Fallback to direct Spring AI (backward compatibility)
                log.debug("[FinancialAgent] Using direct Spring AI ChatClient");
                result = financialChatClient.prompt()
                        .user(question)
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                        .call()
                        .content();
            }

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[FinancialAgent] Completed in {}ms", elapsed);

            return AgentResult.success("FinancialAgent", result, elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[FinancialAgent] Error processing query", e);
            return AgentResult.failure("FinancialAgent",
                    "Failed to fetch financial data: " + e.getMessage(), elapsed);
        }
    }
}