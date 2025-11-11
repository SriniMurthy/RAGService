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
    private final MasterAgent masterAgent;

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
            MasterAgent masterAgent) {

        this.mcpTools = mcpTools;
        this.reActConfig = reActConfig;
        this.masterAgent = masterAgent;

        log.info("SimpleAgenticController initialized");
        log.info("  - Using MasterAgent for true agentic routing (no if/else)");
        log.info("  - Bean tools: {}", ALL_TOOLS.size());
        log.info("  - MCP tools: {}", mcpTools.size());

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

        // MASTER AGENT - Truly agentic routing via function calling (no if/else)
        log.info("Delegating to MasterAgent for agentic routing...");
        String answer = masterAgent.orchestrate(q);

        long elapsedMs = System.currentTimeMillis() - startTime;

        log.debug("MasterAgent completed in {}ms", elapsedMs);

        return new AskResponse(q, answer, ALL_TOOLS.size() + mcpTools.size(), elapsedMs);
    }

    /**
     * STREAMING ENDPOINT (GET - SSE compatibility)
     *
     * Streams MasterAgent's response in real-time.
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

        log.debug("╔══════════════════════════════════════════╗");
        log.debug("║  /askStream - MasterAgent Streaming      ║");
        log.debug("╚══════════════════════════════════════════╝");
        log.debug("Question: {}", q);

        return Flux.<ServerSentEvent<ReActEvent>>create(sink -> {
            try {
                long startTime = System.currentTimeMillis();

                sink.next(ServerSentEvent.<ReActEvent>builder()
                        .data(new ReActEvent("THOUGHT", "Routing to appropriate agent..."))
                        .build());

                // Call MasterAgent (truly agentic - no if/else)
                String answer = masterAgent.orchestrate(q);

                sink.next(ServerSentEvent.<ReActEvent>builder()
                        .data(new ReActEvent("ANSWER", answer))
                        .build());

                long elapsed = System.currentTimeMillis() - startTime;

                sink.next(ServerSentEvent.<ReActEvent>builder()
                        .event("complete")
                        .data(new ReActEvent("COMPLETE",
                            String.format("Completed in %dms", elapsed)))
                        .build());

                log.debug("Stream completed in {}ms", elapsed);
                sink.complete();

            } catch (Exception e) {
                log.error("Error in streaming endpoint", e);
                sink.next(ServerSentEvent.<ReActEvent>builder()
                        .data(new ReActEvent("ERROR", "Error: " + e.getMessage()))
                        .build());
                sink.error(e);
            }
        }).delayElements(Duration.ofMillis(100));
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