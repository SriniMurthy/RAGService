package com.smurthy.ai.rag.controllers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

/**
 * CLAUDE-POWERED UNIFIED AGENTIC CONTROLLER
 *
 * Identical functionality to UnifiedAgenticController but uses Claude 3.5 Sonnet
 * instead of GPT-4o for better function calling reliability.
 *
 *
 * This is a testing endpoint to compare:
 * - OpenAI GPT-4o: /unified/ask
 * - Anthropic Claude 3.5 Sonnet: /unifiedClaude/ask
 * GOAL: Validate Claude's superior function calling to fix hallucination issues
 */
@RestController
@RequestMapping("/unifiedClaude")
public class UnifiedClaudeController {

    private static final Logger log = LoggerFactory.getLogger(UnifiedClaudeController.class);
    private final ChatClient.Builder toolsOnlyBuilder;

    // ALL available tools - both RAG and external APIs
    private static final Set<String> ALL_TOOLS = Set.of(
            // === RAG & TEMPORAL QUERY TOOLS ===
            "queryDocuments",              // Search all documents (Excel, PDF, Word, etc.)
            "queryDocumentsAdvanced",      // Advanced search with custom threshold
            "queryDocumentsByYear",        // Search documents from specific year
            "queryDocumentsByDate",        // Search by year or specific date (flexible)
            "queryDocumentsByDateRange",   // Search within date range

            // === STOCK & FINANCE TOOLS ===
            "getYahooQuote",              // FREE stock quotes (Yahoo Finance)
            "getHistoricalPrices",        // Historical stock data
            "getMarketMovers",            // Top gainers/losers
            "compareStocks",              // Compare multiple stocks
            "analyzeFinancialRatios",     // P/E, ROE, Debt/Equity analysis
            "calculatePortfolioMetrics",  // Portfolio analytics

            // === NEWS TOOLS ===
            "getMarketNews",              // Universal news (ANY topic - Google News RSS)
            "getHeadlinesByCategory",     // Category-specific headlines

            // === WEATHER TOOLS ===
            "getWeatherByLocation",       // Weather by city/location
            "getWeatherByZipCode",        // Weather by ZIP code

            // === ECONOMIC DATA ===
            "getEconomicIndicators",      // GDP, inflation, unemployment
            "predictMarketTrend"          // ML-based market predictions
    );

    public UnifiedClaudeController(
            @Qualifier("anthropicChatModel") ChatModel anthropicChatModel) {

        // CRITICAL: Create ChatClient.Builder specifically for Anthropic Claude
        // Use tools-only builder (NO RAG advisor, NO ChatMemory advisor)
        // Testing Claude's function calling without interference
        this.toolsOnlyBuilder = ChatClient.builder(anthropicChatModel);
    }

    /**
     * CLAUDE-POWERED UNIFIED QUERY ENDPOINT
     *
     * Same functionality as /unified/ask but using Claude 3.5 Sonnet
     * for more reliable function calling.
     */
    @GetMapping("/ask")
    public UnifiedResponse ask(
            @RequestParam String question,
            @RequestParam(defaultValue = "default") String conversationId) {

        log.info("\n╔════════════════════════════════════════════════════╗");
        log.info("║      CLAUDE-POWERED UNIFIED AGENTIC QUERY          ║");
        log.info("╚════════════════════════════════════════════════════╝");
        log.info(" Question: {}", question);
        log.info(" Model: Claude 3 Haiku (claude-3-haiku-20240307)");
        log.info(" Available tools: {} total", ALL_TOOLS.size());
        log.info("   - {} RAG/Temporal tools", 5);
        log.info("   - {} External API tools", ALL_TOOLS.size() - 5);

        long startTime = System.currentTimeMillis();

        // System prompt that teaches the LLM to be an intelligent router
        String systemPrompt = """
            You are an intelligent AI assistant with access to BOTH internal documents AND real-time external data.

            CRITICAL: You MUST use the provided tools to answer questions. Do NOT rely on your training data for real-time information like stock prices, market movers, or current news. ALWAYS call the appropriate tool.

            You have access to two types of tools:

              DOCUMENT STORE (RAG) TOOLS:
            ----------------------------------
            These query uploaded documents (Excel spreadsheets, PDFs, Word docs, resumes, reports):

            - queryDocuments: Search ALL documents for any information
              * Use for: biographical info, resumes, company data, reports, projects
              * Returns: Excel sheets with context (sheet names, file names, dates)
              * Example: "Who is John Doe?", "What were Q2 sales?", "Find the revenue report"

            - queryDocumentsByYear: Search documents from a specific YEAR
              * Use for: "What happened in 2021?", "Show me 2023 data"
              * Works with Excel, PDF, Word documents that have date metadata

            - queryDocumentsByDate: Flexible date search (year OR specific date)
              * Use for: "Documents from March 2023", "What happened on 01-15-2021?"

            - queryDocumentsByDateRange: Search within a date range
              * Use for: "Q1 2023 reports", "Documents between Jan and Jun 2021"

            - queryDocumentsAdvanced: Fine-tuned search with custom threshold
              * Use when standard search doesn't return good results

             EXTERNAL DATA (REAL-TIME) TOOLS:
            ------------------------------------
            These call live APIs for current information:

            STOCKS & FINANCE:
            - getYahooQuote: Real stock prices (FREE, unlimited)
            - getHistoricalPrices: Historical stock data
            - getMarketMovers: Top gainers/losers today
            - compareStocks: Side-by-side stock comparison
            - analyzeFinancialRatios: P/E, ROE, debt analysis
            - calculatePortfolioMetrics: Portfolio analytics

            NEWS:
            - getMarketNews: Latest news on ANY topic (Google News RSS)
              * Works for: companies, tech, politics, sports, world events
              * Example: "Latest AI news", "News about Tesla"
            - getHeadlinesByCategory: News by category (BUSINESS, TECH, SPORTS, etc.)

            WEATHER:
            - getWeatherByLocation: Current weather (FREE, unlimited)
            - getWeatherByZipCode: Weather by ZIP code

            ECONOMIC:
            - getEconomicIndicators: GDP, inflation, unemployment data
            - predictMarketTrend: ML-based market predictions

            ═══════════════════════════════════════════════════════════════════

             TOOL SELECTION STRATEGY:
            ═══════════════════════════════════════════════════════════════════

            1. BIOGRAPHICAL/RESUME QUESTIONS → queryDocuments
               "Who is Srinivas Murthy?" → queryDocuments
               "What experience does Jane have?" → queryDocuments

            2. COMPANY/ORGANIZATIONAL INFO:
               Check documents first, then supplement with news if needed:
               "What is Zscaler?" → queryDocuments (check docs first)
                                  → getMarketNews (if docs incomplete)

            3. FINANCIAL REPORTS/DATA IN DOCUMENTS:
               "What were Q2 2023 sales?" → queryDocumentsByYear(2023) + queryDocuments
               "Show me revenue from last quarter" → queryDocumentsByDateRange

            4. TEMPORAL QUESTIONS:
               "What did I work on in 2021?" → queryDocumentsByYear(2021)
               "Projects from March 2023" → queryDocumentsByDate("2023-03")

            5. REAL-TIME MARKET DATA:
               "Stock price of AAPL" → getYahooQuote
               "Weather in SF" → getWeatherByLocation
               "Latest tech news" → getMarketNews

            6. HYBRID QUERIES (combine multiple sources):
               "Compare AAPL and GOOGL, and show my portfolio from 2022"
               → getYahooQuote("AAPL") + getYahooQuote("GOOGL") + queryDocumentsByYear(2022)
            ═══════════════════════════════════════════════════════════════════
             IMPORTANT RULES:
            ═══════════════════════════════════════════════════════════════════
            1. ALWAYS try querying documents FIRST for biographical/organizational questions
            2. For company questions, check docs first, then use getMarketNews if incomplete
            3. For date-specific questions, use temporal tools (queryDocumentsByYear, etc.)
            4. For real-time data (stocks, weather, news), use external API tools
            5. You can call MULTIPLE tools to answer complex questions
            6. Cite your sources: "According to the Q2_Sales_Report.xlsx file, sheet 'Revenue'..."
            7. Be transparent about which tools you used
            8. If documents are empty/missing, fall back to external APIs when appropriate

            ═══════════════════════════════════════════════════════════════════

            Now answer the user's question using the appropriate tools!
            """;

        // Build ChatClient with ALL tools available
        // Use toolsOnlyBuilder (NO RAG advisor) so tools work properly
        ChatClient client = toolsOnlyBuilder
                .defaultSystem(systemPrompt)
                .defaultToolNames(ALL_TOOLS.toArray(new String[0]))
                .build();

        log.info(" Claude is analyzing question and selecting tools...");

        // Call Claude - it will automatically decide which tools to use
        // NO CHAT MEMORY - testing if Claude has better function calling reliability
        String answer = client.prompt()
                .user(question)
                .call()
                .content();

        long elapsedMs = System.currentTimeMillis() - startTime;

        log.info("Response generated in {}ms", elapsedMs);
        log.info("════════════════════════════════════════════════════\n");

        return new UnifiedResponse(
                question,
                answer,
                ALL_TOOLS.size(),
                "Claude 3.5 Sonnet with full tool access - autonomous tool selection",
                elapsedMs
        );
    }

    /**
     * SIMPLIFIED ENDPOINT
     *
     * Even simpler API - just /unifiedClaude/q?query=your+question
     */
    @GetMapping("/q")
    public String query(@RequestParam String query) {
        return ask(query, "default").answer();
    }

    /**
     * Response record with metadata
     */
    public record UnifiedResponse(
            String question,
            String answer,
            int totalToolsAvailable,
            String strategy,
            long executionTimeMs
    ) {}
}