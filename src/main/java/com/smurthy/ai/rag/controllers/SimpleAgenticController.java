package com.smurthy.ai.rag.controllers;

import com.smurthy.ai.rag.agents.*;
import com.smurthy.ai.rag.config.ReActConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * SIMPLIFIED AGENTIC API
 *
 * Just two endpoints for everything:
 * - /ask       : Blocking ReACT agent
 * - /askStream : Streaming ReACT agent
 *
 * All RAG, tools, memory, and reasoning happen automatically.
 */
@RestController
public class SimpleAgenticController {

    private static final Logger log = LoggerFactory.getLogger(SimpleAgenticController.class);
    private final ChatClient.Builder chatClientBuilder;
    private final List<ToolCallback> mcpTools;
    private final ReActConfig reActConfig;

    // Multi-agent orchestration components
    private final QueryRouterAgent queryRouterAgent;
    private final FinancialAgent financialAgent;
    private final ResearchAgent researchAgent;
    private final NewsAgent newsAgent;
    private final WeatherAgent weatherAgent;
    private final AggregatorAgent aggregatorAgent;

    // All available tools
    private static final Set<String> ALL_TOOLS = Set.of(
            // RAG & Document Tools
            "queryDocuments",
            "queryDocumentsByYear",
            "queryDocumentsByDateRange",
            "queryDocumentsAdvanced",

            // Stock & Finance Tools
            "getYahooQuote",
            "getRealTimeQuote",
            "getFinnhubQuote",
            "getQuoteFromYahooOnly",
            "getQuoteFromGoogleFinance",
            "getHistoricalPrices",
            "analyzeFinancialRatios",

            // News Tools
            "getMarketNews",
            "getHeadlinesByCategory",

            // Weather Tools
            "getWeatherByLocation",
            "getWeatherByZipCode",

            // Market & Economic Tools
            "getMarketMovers",
            "getEconomicIndicators"
    );

    public SimpleAgenticController(
            ChatClient.Builder builder,
            ChatMemory chatMemory,
            List<ToolCallback> mcpTools,
            ReActConfig reActConfig,
            QueryRouterAgent queryRouterAgent,
            FinancialAgent financialAgent,
            ResearchAgent researchAgent,
            NewsAgent newsAgent,
            WeatherAgent weatherAgent,
            AggregatorAgent aggregatorAgent) {

        this.mcpTools = mcpTools;
        this.reActConfig = reActConfig;
        this.queryRouterAgent = queryRouterAgent;
        this.financialAgent = financialAgent;
        this.researchAgent = researchAgent;
        this.newsAgent = newsAgent;
        this.weatherAgent = weatherAgent;
        this.aggregatorAgent = aggregatorAgent;

        log.debug("SimpleAgenticController initialized");
        log.debug("  - Bean tools: {}", ALL_TOOLS.size());
        log.debug("  - MCP tools: {}", mcpTools.size());
        log.debug("  - ReACT max iterations: {} (HARD LIMIT)", reActConfig.getMaxIterations());
        log.debug("  - ReACT max cost per query: ${}", reActConfig.getMaxCostPerQuery());
        log.debug("  - ReACT iteration delay: {}ms (rate limiting)", reActConfig.getIterationDelayMs());
        log.info("  - Multi-agent orchestration: ENABLED");

        this.chatClientBuilder = builder
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .conversationId("default")
                                .build())
                .defaultToolCallbacks(mcpTools.toArray(new ToolCallback[0]));
    }

    /**
     * BLOCKING ENDPOINT (POST)
     *
     * Client sends query in request body, waits for final answer.
     * Server runs ReACT loop internally.
     *
     * ConversationId is automatically managed via HTTP session (transparent to client).
     * Follow-up questions from the same browser maintain conversation context automatically.
     *
     * Example:
     * curl -X POST -H "Content-Type: application/json" \
     *      -b cookies.txt -c cookies.txt \
     *      -d '{"question":"What is AAPL stock price?"}' \
     *      http://localhost:8080/RAG/ask
     */
    @PostMapping("/ask")
    public AskResponse ask(
            @RequestBody AskRequest request,
            HttpSession session) {

        String q = request.question();

        if (!StringUtils.hasText(q)) {
            return new AskResponse(q, "Please provide a question", 0, 0);
        }

        // Session-based conversationId: auto-generated and persisted across requests
        String tempConversationId = (String) session.getAttribute("conversationId");
        if (tempConversationId == null) {
            tempConversationId = UUID.randomUUID().toString();
            session.setAttribute("conversationId", tempConversationId);
            log.debug("Created new conversation: {}", tempConversationId);
        }
        final String conversationId = tempConversationId;  // Make final for lambda

        log.debug("╔══════════════════════════════════════════╗");
        log.debug("║  /ask - Multi-Agent Orchestration        ║");
        log.debug("╚══════════════════════════════════════════╝");
        log.debug("Question: {}", q);
        log.debug("ConversationId: {} (session-managed)", conversationId);

        long startTime = System.currentTimeMillis();

        // STEP 1: ROUTER AGENT - Analyze query and determine which agents to invoke
        QueryIntent intent = queryRouterAgent.analyze(q);
        log.info("Routing decision: {}", intent.getAgentSummary());

        // STEP 2: SPECIALIZED AGENTS - Execute relevant agents in parallel
        List<AgentResult> agentResults = new ArrayList<>();

        if (intent.needsFinancial()) {
            CompletableFuture<AgentResult> financialFuture = CompletableFuture.supplyAsync(
                    () -> financialAgent.execute(q, conversationId));
            agentResults.add(financialFuture.join());
        }

        if (intent.needsResearch()) {
            CompletableFuture<AgentResult> researchFuture = CompletableFuture.supplyAsync(
                    () -> researchAgent.execute(q, conversationId));
            agentResults.add(researchFuture.join());
        }

        if (intent.needsNews()) {
            CompletableFuture<AgentResult> newsFuture = CompletableFuture.supplyAsync(
                    () -> newsAgent.execute(q, conversationId));
            agentResults.add(newsFuture.join());
        }

        if (intent.needsWeather()) {
            CompletableFuture<AgentResult> weatherFuture = CompletableFuture.supplyAsync(
                    () -> weatherAgent.execute(q, conversationId));
            agentResults.add(weatherFuture.join());
        }

        // STEP 3: AGGREGATOR AGENT - Synthesize results into unified answer
        String finalAnswer = aggregatorAgent.synthesize(q, agentResults);

        long elapsedMs = System.currentTimeMillis() - startTime;

        log.debug("Multi-agent orchestration completed in {}ms", elapsedMs);
        log.debug("═══════════════════════════════════════════\n");

        return new AskResponse(q, finalAnswer, ALL_TOOLS.size() + mcpTools.size(), elapsedMs);
    }

    /**
     * STREAMING ENDPOINT (GET - SSE compatibility)
     *
     * Client receives real-time updates as agent thinks.
     * Shows: THOUGHT → ACTION → OBSERVATION → ANSWER
     *
     * ConversationId is automatically managed via HTTP session (transparent to client).
     * Follow-up questions from the same browser maintain conversation context automatically.
     *
     * Note: Uses GET because EventSource API doesn't support POST
     * Example: const eventSource = new EventSource('/RAG/askStream?q=What+is+AAPL+price');
     */
    @GetMapping(value = "/askStream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ReActEvent>> askStream(
            @RequestParam String q,
            HttpSession session) {

        if (!StringUtils.hasText(q)) {
            return Flux.just(ServerSentEvent.<ReActEvent>builder()
                    .data(new ReActEvent("ERROR", "Please provide a question"))
                    .build());
        }

        // Session-based conversationId: auto-generated and persisted across requests
        String conversationId = (String) session.getAttribute("conversationId");
        if (conversationId == null) {
            conversationId = UUID.randomUUID().toString();
            session.setAttribute("conversationId", conversationId);
            log.debug("Created new conversation: {}", conversationId);
        }

        final String finalConversationId = conversationId;  // For use in lambda
        final int maxIterations = reActConfig.getMaxIterations();  // HARD LIMIT from config

        log.debug("╔══════════════════════════════════════════╗");
        log.debug("║  /askStream - Streaming ReACT Agent      ║");
        log.debug("╚══════════════════════════════════════════╝");
        log.debug("Question: {}", q);
        log.debug("ConversationId: {} (session-managed)", finalConversationId);
        log.debug("Max iterations: {} (from config, not overridable)", maxIterations);

        return Flux.<ServerSentEvent<ReActEvent>>create(sink -> {
            try {
                long startTime = System.currentTimeMillis();
                int iterations = 0;
                boolean answered = false;
                int totalTokens = 0;

                String systemPrompt = """
                    You are an efficient AI agent with access to conversation history, RAG documents, and real-time tools.

                    CRITICAL: When to use what:

                    1. **CONVERSATION HISTORY** - For user preferences, prior context:
                       - "What is my favorite stock?" → Check history
                       - Follow-up questions → Use conversation context

                    2. **REAL-TIME TOOLS** - ALWAYS use for current/live data (DO NOT search documents):
                       - Stock prices, predictions → getYahooQuote, getRealTimeQuote, analyzeFinancialRatios
                       - News, current events, Trump/Xi meeting → getMarketNews, getHeadlinesByCategory
                       - Weather → getWeatherByLocation
                       - Market trends → getMarketMovers, getEconomicIndicators

                    3. **RAG DOCUMENTS** - For user's uploaded documents only:
                       - "What's in my documents about X?" → queryDocuments

                    RULES:
                    - News/stock/weather questions → ALWAYS use tools, NEVER say "not in documents"
                    - After using tools, provide direct answer - don't ask user to confirm
                    - Be concise
                    """;

                ChatClient client = chatClientBuilder
                        .defaultSystem(systemPrompt)
                        .defaultToolNames(ALL_TOOLS.toArray(new String[0]))
                        .build();

                while (iterations < maxIterations && !answered) {
                    iterations++;

                    // THOUGHT
                    sink.next(ServerSentEvent.<ReActEvent>builder()
                            .data(new ReActEvent("THOUGHT",
                                "Iteration " + iterations + ": Analyzing question..."))
                            .build());

                    // ACTION (call LLM with tools)
                    String actionPrompt = String.format(
                        "Question: %s\n\nIteration: %d/%d\n\nWhat action should I take? Use available tools if needed.",
                        q, iterations, maxIterations
                    );

                    sink.next(ServerSentEvent.<ReActEvent>builder()
                            .data(new ReActEvent("ACTION", "Executing tools..."))
                            .build());

                    String observation = client.prompt()
                            .user(actionPrompt)
                            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, finalConversationId))
                            .call()
                            .content();

                    // OBSERVATION
                    sink.next(ServerSentEvent.<ReActEvent>builder()
                            .data(new ReActEvent("OBSERVATION", observation))
                            .build());

                    // EVALUATE: Proper feedback loop - ask LLM if question is answered
                    String evaluationPrompt = String.format("""
                        EVALUATION TASK:
                        Original question: %s

                        Your previous response: %s

                        Can you provide a complete answer to the user's question with the information you have?

                        Respond ONLY with:
                        YES - [your final answer here]
                        OR
                        NO - [what additional information you need]
                        """, q, observation);

                    String evaluation = client.prompt()
                            .user(evaluationPrompt)
                            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, finalConversationId))
                            .call()
                            .content();

                    // Check if evaluation says YES
                    answered = evaluation != null && evaluation.trim().toUpperCase().startsWith("YES");

                    if (answered) {
                        // Extract answer after "YES -"
                        String finalAnswer = evaluation.replaceFirst("(?i)^YES\\s*-\\s*", "").trim();
                        sink.next(ServerSentEvent.<ReActEvent>builder()
                                .data(new ReActEvent("ANSWER", finalAnswer))
                                .build());
                    } else if (iterations >= maxIterations) {
                        // Max iterations reached - provide best answer we have
                        sink.next(ServerSentEvent.<ReActEvent>builder()
                                .data(new ReActEvent("ANSWER",
                                    "I've reached the maximum number of iterations. Based on what I have: " + observation))
                                .build());
                        answered = true;
                    } else {
                        // Continue to next iteration
                        sink.next(ServerSentEvent.<ReActEvent>builder()
                                .data(new ReActEvent("REASONING", evaluation))
                                .build());
                    }

                    // Rate limiting delay between iterations
                    if (iterations < maxIterations && !answered) {
                        Thread.sleep(reActConfig.getIterationDelayMs());
                    }
                }

                long elapsed = System.currentTimeMillis() - startTime;

                // Send completion event with metadata
                sink.next(ServerSentEvent.<ReActEvent>builder()
                        .event("complete")
                        .data(new ReActEvent("COMPLETE",
                            String.format("Completed in %d iterations, %dms, ~%d tokens",
                                iterations, elapsed, totalTokens)))
                        .build());

                log.debug("Stream completed: {} iterations in {}ms", iterations, elapsed);

                sink.complete();

            } catch (Exception e) {
                log.error("Error in streaming endpoint", e);
                sink.next(ServerSentEvent.<ReActEvent>builder()
                        .data(new ReActEvent("ERROR", "Error: " + e.getMessage()))
                        .build());
                sink.error(e);
            }
        }).delayElements(Duration.ofMillis(100)); // Smooth streaming
    }

    /**
     * Request for /ask endpoint
     */
    public record AskRequest(
            String question
    ) {}

    /**
     * Response for /ask endpoint
     * Note: conversationId is NOT included - it's managed transparently via HTTP session
     */
    public record AskResponse(
            String question,
            String answer,
            int toolsAvailable,
            long executionTimeMs
    ) {}

    /**
     * Event for /askStream endpoint
     */
    public record ReActEvent(
            String type,
            String content
    ) {}
}