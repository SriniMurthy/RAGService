package com.smurthy.ai.rag.controllers;

import com.smurthy.ai.rag.advisor.ToolInvocationTracker;
import com.smurthy.ai.rag.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/")
public class RAGDataController {

    private final ChatClient.Builder chatClientBuilder;
    private final ChatClient.Builder functionCallingBuilder;
    private static final Logger log = LoggerFactory.getLogger(RAGDataController.class);


    public RAGDataController(
            ChatClient.Builder builder,
            VectorStore vectorStore,
            ChatMemory chatMemory) {

        // Builder for RAG-based chat (with document retrieval)
        this.chatClientBuilder = builder
                .defaultAdvisors(
                        RetrievalAugmentationAdvisor.builder()
                                .documentRetriever(VectorStoreDocumentRetriever.builder()
                                        .similarityThreshold(0.50) // Balanced threshold for good recall
                                        .topK(10) // Retrieve more chunks for timeline/comparison questions
                                        .vectorStore(vectorStore)
                                        .build())
                                .build(),
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .build());

        // Builder for function calling (NO RAG advisor - allows tool usage)
        this.functionCallingBuilder = builder
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .build());
    }

    @GetMapping("/chat")
    public String chat(
            @RequestParam String question,
            @RequestParam(defaultValue = "default") String conversationId,
            @RequestParam(required = false) String userRole) {

        // Clear tool tracking for this request
        ToolInvocationTracker.clear();

        // Dynamically determine which functions to enable based on context
        List<String> enabledFunctions = determineEnabledFunctions(question, userRole);

        // Track that tools were made available
        ToolInvocationTracker.recordToolsAvailable(enabledFunctions);

        //  Clone the builder to create an isolated, request-scoped client.
        ChatClient requestScopedClient = this.chatClientBuilder.clone()
                .defaultToolNames(enabledFunctions.toArray(new String[0]))
                .build();

        String response = requestScopedClient.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        // Log tool usage for debugging
        log.debug("Tool usage summary: " + ToolInvocationTracker.getSummary());

        return response;
    }

    @GetMapping("/chatWithReasoning")
    public String chatWithReasoning(
            @RequestParam String question,
            @RequestParam(defaultValue = "default") String conversationId) {

        log.debug("═══════════════════════════════════════════════");
        log.debug(" /chatWithReasoning endpoint called");
        log.debug("   Question: " + question);
        log.debug("═══════════════════════════════════════════════");

        // Clear tool tracking for this request
        ToolInvocationTracker.clear();
        log.debug("✓ Tracker cleared");

        // For this endpoint, we enable all tools and let the model decide.
        // Uses functionCallingBuilder which does NOT have RAG advisor
        // This allows the LLM to freely use tools without document restrictions
        List<String> allTools = Arrays.asList(
                "getStockPrice",
                "getRealTimeQuote",
                "getYahooQuote",            // FREE, unlimited Yahoo Finance
                "getHistoricalPrices",       // Historical data
                "getMarketNews",
                "analyzeFinancialRatios",
                "getEconomicIndicators",
                "predictMarketTrend",
                "getWeatherByLocation",      // FREE weather by location
                "getWeatherByZipCode"        // FREE weather by ZIP
        );

        // Track that tools were made available
        log.debug("✓ Recording " + allTools.size() + " tools as available");
        ToolInvocationTracker.recordToolsAvailable(allTools);
        log.debug("✓ After recording - wereToolsAvailable(): " + ToolInvocationTracker.wereToolsAvailable());

        ChatClient clientWithAllTools = this.functionCallingBuilder.clone()
                .defaultToolNames(allTools.toArray(new String[0]))
                .build();

        log.debug("✓ ChatClient built with " + allTools.size() + " tools");

        String response = clientWithAllTools.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        // Log tool usage for debugging
        log.debug("═══════════════════════════════════════════════");
        log.debug("Tool usage summary: " + ToolInvocationTracker.getSummary());
        log.debug("Tools called: " + ToolInvocationTracker.wereToolsCalled());
        log.debug("Tools available: " + ToolInvocationTracker.wereToolsAvailable());
        log.debug("═══════════════════════════════════════════════");

        return response;
    }

    /**
     * New endpoint that returns JSON with tool invocation metadata
     * This allows tests to verify tool usage without ThreadLocal issues
     */
    @GetMapping("/chatWithReasoningJson")
    public ChatResponse chatWithReasoningJson(
            @RequestParam String question,
            @RequestParam(defaultValue = "default") String conversationId) {

        log.debug("═══════════════════════════════════════════════");
        log.debug("🎯 /chatWithReasoningJson endpoint called");
        log.debug("   Question: " + question);
        log.debug("═══════════════════════════════════════════════");

        // Clear tool tracking for this request
        ToolInvocationTracker.clear();

        // Enable all tools
        List<String> allTools = Arrays.asList(
                "getStockPrice",
                "getRealTimeQuote",
                "getYahooQuote",
                "getHistoricalPrices",
                "getMarketNews",
                "analyzeFinancialRatios",
                "getEconomicIndicators",
                "predictMarketTrend",
                "getWeatherByLocation",
                "getWeatherByZipCode"
        );

        // Track that tools were made available
        ToolInvocationTracker.recordToolsAvailable(allTools);

        ChatClient clientWithAllTools = this.functionCallingBuilder.clone()
                .defaultToolNames(allTools.toArray(new String[0]))
                .build();

        String answer = clientWithAllTools.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        // Capture metadata BEFORE returning (same thread)
        boolean toolsAvailable = ToolInvocationTracker.wereToolsAvailable();
        boolean toolsCalled = ToolInvocationTracker.wereToolsCalled();
        List<String> calledTools = new ArrayList<>(ToolInvocationTracker.getToolCallsInCurrentRequest());
        String summary = ToolInvocationTracker.getSummary();

        log.debug("═══════════════════════════════════════════════");
        log.debug("Metadata: toolsAvailable=" + toolsAvailable + ", toolsCalled=" + toolsCalled);
        log.debug("Called tools: " + calledTools);
        log.debug("Summary: " + summary);
        log.debug("═══════════════════════════════════════════════");

        return ChatResponse.from(answer, toolsAvailable, toolsCalled, allTools, calledTools, summary);
    }

    /**
     * Determine which functions should be available based on the query and user context
     * This controls which functions are available
     * But once available, the *model* decides when to actually call them
     */
    private List<String> determineEnabledFunctions(String question, String userRole) {
        List<String> functions = new ArrayList<>();

        String lowerQuestion = question.toLowerCase();

        // Basic keyword-based function selection (this can be made  more sophisticated)
        if (lowerQuestion.contains("stock") || lowerQuestion.contains("price")) {
            functions.add("getStockPrice");
        }

        if (lowerQuestion.contains("real-time") || lowerQuestion.contains("realtime") ||
                lowerQuestion.contains("live") || lowerQuestion.contains("current")) {
            functions.add("getRealTimeQuote");
        }

        if (lowerQuestion.contains("weather") || lowerQuestion.contains("temperature") ||
                lowerQuestion.contains("forecast") || lowerQuestion.contains("climate")) {
            functions.add("getWeatherByLocation");
            functions.add("getWeatherByZipCode");
        }

        if (lowerQuestion.contains("news") || lowerQuestion.contains("recent")) {
            functions.add("getMarketNews");
        }

        if (lowerQuestion.contains("compare")) {
            functions.add("compareStocks");
        }

        if (lowerQuestion.contains("analyze") || lowerQuestion.contains("ratio")) {
            functions.add("analyzeFinancialRatios");
        }

        if (lowerQuestion.contains("economic") || lowerQuestion.contains("gdp") ||
                lowerQuestion.contains("inflation")) {
            functions.add("getEconomicIndicators");
        }

        // Role-based access control
        if ("premium".equals(userRole)) {
            functions.add("predictMarketTrend");
            functions.add("getAdvancedAnalytics");
        }

        // Always enable basic functions
        if (functions.isEmpty()) {
            functions.add("getStockPrice");
            functions.add("getMarketNews");
        }

        return functions;
    }
}
