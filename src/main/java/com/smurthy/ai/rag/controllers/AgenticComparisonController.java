package com.smurthy.ai.rag.controllers;


import com.smurthy.ai.rag.service.FastDocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.rag.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ai.document.Document;


import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Three Different Agentic Approaches - Side by Side Comparison
 */
@RestController
@RequestMapping("/agentic")
class AgenticComparisonController {

    private final ChatClient.Builder baseBuilder;
    private final ChatClient metaAgent;
    private final Map<String, String> allTools;

    private final ChatClient.Builder toolsOnlyBuilder;
    private final VectorStore vectorStore;
    private ChatModel chatModel;

    private static final Logger log = LoggerFactory.getLogger(AgenticComparisonController.class);

    public AgenticComparisonController(
            ChatClient.Builder builder,
            VectorStore vectorStore,
            ChatModel chatModel,
            ChatMemory chatMemory) {

        this.vectorStore = vectorStore;
        this.chatModel = chatModel;


        // Base builder with RAG and Memory (for hybrid endpoint)
        this.baseBuilder = builder
                .defaultAdvisors(
                        RetrievalAugmentationAdvisor.builder()
                                .documentRetriever(VectorStoreDocumentRetriever.builder()
                                        .similarityThreshold(0.30)  // Lowered from 0.50 to be more lenient
                                        .vectorStore(vectorStore)
                                        .build())
                                .build(),
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .build());

        // Tools-only builder (NO RAG advisor - pure function calling)
        this.toolsOnlyBuilder = builder
                .defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .build());

        // Meta-agent for tool selection and planning
        this.metaAgent = ChatClient.builder(chatModel)
                .defaultSystem("""
                    You are a planning agent that analyzes queries and decides on tool usage.

                    IMPORTANT CONTEXT:
                    - The execution agent has access to a RAG document store with user resumes and personal documents
                    - Documents will ALWAYS be checked first before using any tools
                    - Your job: Suggest fallback tools in case documents don't have the answer

                    TOOL SELECTION STRATEGY:

                    1. PERSONAL/BIOGRAPHICAL QUERIES (people in resumes):
                       - "Who is [Person Name]" → [] (documents only, no tools needed)
                       - "Tell me about [Person Name]" → [] (documents only)
                       - Example: "Who is Srinivas Murthy" → []

                    2. COMPANY/ORGANIZATION INFORMATION:
                       - "What is [Company Name]" → ["getMarketNews"] (fallback if not in docs)
                       - "Tell me about [Company]" → ["getMarketNews"] (fallback)
                       - Example: "What is Zscaler" → ["getMarketNews"]
                       - Example: "What does Google do" → ["getMarketNews"]

                    3. REAL-TIME MARKET DATA:
                       - Stock prices → ["getYahooQuote"]
                       - Market news → ["getMarketNews"]
                       - Weather → ["getWeatherByLocation"] or ["getWeatherByZipCode"]
                       - Example: "Stock price of AAPL" → ["getYahooQuote"]

                    4. FINANCIAL ANALYSIS:
                       - Financial ratios → ["analyzeFinancialRatios"]
                       - Historical data → ["getHistoricalPrices"]
                       - Market trends → ["predictMarketTrend"]

                    Available tools:
                    - getStockPrice: Mock/cached stock prices (for testing)
                    - getYahooQuote: Real stock quotes from Yahoo Finance (FREE, NO API KEY REQUIRED)
                    - getHistoricalPrices: Historical stock data from Yahoo Finance
                    - getMarketNews: Latest news from Google News RSS (works for ANY topic - companies, tech, finance, etc.)
                    - analyzeFinancialRatios: Financial analysis (P/E, ROE, Debt/Equity)
                    - getEconomicIndicators: Economic data (GDP, inflation, unemployment)
                    - predictMarketTrend: ML-based market trend predictions
                    - getAdvancedAnalytics: Advanced analytics and insights
                    - compareStocks: Compare multiple stocks side-by-side
                    - getCompanyProfile: Detailed company information (PLACEHOLDER DATA ONLY)
                    - calculatePortfolioMetrics: Portfolio analysis and metrics
                    - getRealTimeQuote: Real-time quotes from Alpha Vantage (requires API key)
                    - getWeatherByLocation: Weather data by location name
                    - getWeatherByZipCode: Weather data by ZIP code

                    KEY PRINCIPLE: When in doubt, suggest ONE relevant fallback tool. The execution agent will check documents first anyway.
                    """)
                .build();

        this.allTools = Map.ofEntries(
                Map.entry("getStockPrice", "Stock prices (mock data)"),
                Map.entry("getYahooQuote", "Real stock quotes from Yahoo Finance (FREE, NO API KEY)"),
                Map.entry("getHistoricalPrices", "Historical stock data from Yahoo Finance"),
                Map.entry("getMarketNews", "Latest market news from Google News RSS"),
                Map.entry("analyzeFinancialRatios", "Financial analysis (P/E, ROE, Debt/Equity)"),
                Map.entry("getEconomicIndicators", "Economic data (GDP, inflation)"),
                Map.entry("predictMarketTrend", "ML-based market predictions"),
                Map.entry("getAdvancedAnalytics", "Advanced analytics"),
                Map.entry("compareStocks", "Compare multiple stocks"),
                Map.entry("getCompanyProfile", "Company information"),
                Map.entry("calculatePortfolioMetrics", "Portfolio analysis"),
                Map.entry("getRealTimeQuote", "Real-time quotes from Alpha Vantage (requires API key)"),
                Map.entry("getWeatherByLocation", "Weather data by location"),
                Map.entry("getWeatherByZipCode", "Weather data by ZIP code")
        );
    }

    // =================================================================
    // APPROACH 1: FULLY AGENTIC - TOOLS ONLY (No RAG Documents)
    // =================================================================
    @GetMapping("/fullyAgentic")
    public FullyAgenticResponse fullyAgentic(
            @RequestParam String question,
            @RequestParam(defaultValue = "default") String conversationId) {

        Instant start = Instant.now();

        log.debug("\n═══════════════════════════════════════════");
        log.debug("FULLY AGENTIC APPROACH (Tools-Only)");
        log.debug("═══════════════════════════════════════════");
        log.debug("Question: " + question);
        log.debug("Strategy: Give model ALL tools, NO document retrieval");
        log.debug("Available tools: " + allTools.size());

        // Build client with ALL tools - NO RAG advisor
        ChatClient client = toolsOnlyBuilder
                .defaultToolNames(allTools.keySet().toArray(new String[0]))
                .defaultSystem("""
                    You are a helpful AI assistant that answers questions using real-time tools and function calling.
                    You do NOT have access to any documents - you must use tools to get current information.

                    IMPORTANT RULES:
                    1. You MUST call appropriate tools to answer questions - do not say "I don't know" without trying
                    2. For questions about "today", "current", "now", "recent" - you MUST call tools
                    3. NEVER make up or guess data - ALWAYS use the tool result
                    4. Use the EXACT values returned by tools in your answer

                    TOOL SELECTION GUIDE:
                    - "What is [Company]" → getMarketNews("CompanyName", 5) - Learn about companies from news
                    - "Why is market up/down today" → getMarketNews("stock market", 5)
                    - "Market movers", "biggest gainers/losers" → getMarketMovers("NASDAQ" or "NYSE", 5)
                    - "Stock price of X" → getYahooQuote("SYMBOL")
                    - "Who won X", "latest news about X" → getMarketNews("topic", 5)
                    - "Market outlook" → getMarketNews("market outlook", 5)
                    - "Economic data" → getEconomicIndicators("indicator")

                    KEY TOOLS:
                    - getMarketNews: Universal news (works for ANY topic: companies, finance, politics, sports, tech, world events)
                      * Use this to learn what a company does, their industry, products, and recent activities
                      * Example: "What is Zscaler" → getMarketNews("Zscaler", 5)
                    - getMarketMovers: Real-time top stock gainers/losers
                    - getYahooQuote: Real stock quotes
                    - getEconomicIndicators: Economic data

                    Always call tools to get fresh data. When describing companies, synthesize information from ALL news articles to provide a comprehensive answer about what the company does, their industry, and key products. Be transparent about which tools you used.
                    """)
                .build();

        ChatResponse response = client.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatResponse();

        Duration elapsed = Duration.between(start, Instant.now());

        log.debug("\nResponse generated in " + elapsed.toMillis() + "ms");
        log.debug("═══════════════════════════════════════════\n");

        return new FullyAgenticResponse(
                response.getResult().getOutput().getText(),
                new ArrayList<>(allTools.keySet()),
                "Model had access to all " + allTools.size() + " tools and decided which to use",
                elapsed.toMillis()
        );
    }

    // This is a new endpoint to be added to AgenticComparisonController.java

    @GetMapping("/temporalQuery")
    public String temporalQuery(@RequestParam String question, @RequestParam int year) {
        log.info("--- TEMPORAL QUERY ---");
        log.info("Question: " + question);
        log.info("Filter: Documents from year " + year);

        // 1. Define the metadata filter using Spring AI's Filter Expression language.
        // This will be translated into a SQL WHERE clause on the JSONB metadata column.
        // e.g., WHERE metadata->>'start_date' >= '2023-01-01' AND metadata->>'end_date' <= '2023-12-31'
        String filterExpression = "metadata.start_date >= '" + year + "-01-01' AND metadata.end_date <= '" + year + "-12-31'";
        log.info("Applying filter expression: " + filterExpression);

        // 2. Create a SearchRequest with the temporal filter.
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(5)
                .similarityThreshold(0.3)
                .filterExpression(filterExpression)
                .build();

        // 3. Perform the filtered similarity search.
        List<Document> filteredDocuments = vectorStore.similaritySearch(searchRequest);
        log.info("Found {} documents after temporal filtering.", filteredDocuments.size());

        if (filteredDocuments.isEmpty()) {
            return "I couldn't find any documents matching the year " + year + " to answer that question.";
        }

        // 4. Manually construct the prompt context with the filtered documents.
        String documentsContext = filteredDocuments.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n---\n"));

        // 5. Build the prompt for the LLM.
        String userPrompt = """
        Answer the following question based ONLY on the provided documents from year %d.

        Question: {question}

        Documents:
        ---
        {documents}
        ---
        """.formatted(year);

        var prompt = new Prompt(userPrompt.replace("{question}", question).replace("{documents}", documentsContext));

        // 6. Call the ChatClient with the filtered context.
        ChatClient client = ChatClient.builder(chatModel).build();
        return Objects.requireNonNull(client.prompt(prompt).call().chatResponse()).getResult().getOutput().getText();
    }

    // =================================================================
    // APPROACH 2: META-AGENT SELECTION (Smart pre-filtering)
    // =================================================================
    @GetMapping("/metaSelection")
    public MetaSelectionResponse metaSelection(
            @RequestParam String question,
            @RequestParam(defaultValue = "default") String conversationId) {

        Instant start = Instant.now();

        log.debug("\n═══════════════════════════════════════════");
        log.debug("META-AGENT SELECTION APPROACH");
        log.debug("═══════════════════════════════════════════");
        log.debug("Question: " + question);
        log.debug("Strategy: Meta-agent analyzes query and selects relevant tools first");

        // STEP 1: Meta-agent analyzes and selects tools
        String selectionPrompt = String.format("""
                Analyze this user question and determine which tools would be most relevant:
                
                Question: "%s"
                
                Respond with ONLY a JSON array of tool names that are needed.
                Be selective - only include tools that are directly relevant.
                Example: ["getStockPrice", "analyzeFinancialRatios"]
                If no tools needed, return: []
                """, question);

        Instant metaStart = Instant.now();
        String toolsJson = metaAgent.prompt()
                .user(selectionPrompt)
                .call()
                .content();
        Duration metaElapsed = Duration.between(metaStart, Instant.now());

        List<String> selectedTools = parseToolList(toolsJson);

        log.debug("\n📋 Meta-agent analysis (" + metaElapsed.toMillis() + "ms):");
        log.debug("   Selected tools: " + selectedTools);
        log.debug("   Filtered out: " + (allTools.size() - selectedTools.size()) + " tools");

        // STEP 2: Build focused client with only selected tools
        ChatClient focusedClient = baseBuilder
                .defaultToolNames(selectedTools.toArray(new String[0]))
                .defaultSystem("""
                    You are a helpful AI assistant with access to both documents and tools.

                    EXECUTION STRATEGY:
                    1. ALWAYS check the DOCUMENTS section FIRST
                    2. If the documents have sufficient information, answer from the documents
                    3. If the documents are missing information or incomplete, you MUST call the provided tools
                    4. You can use BOTH documents and tools together for comprehensive answers

                    IMPORTANT - TOOL USAGE:
                    - For company queries like "What is [Company]", use getMarketNews to get comprehensive information
                    - Example: "What is Zscaler" → Call getMarketNews("Zscaler", 5)
                    - Synthesize information from ALL news articles to describe what the company does, their industry, and products
                    - Documents may have partial info (e.g., "worked at Zscaler") but not complete info (e.g., "what Zscaler is")
                    - In such cases, YOU MUST USE THE TOOLS to fill in the gaps
                    - Be transparent about your sources (documents vs tools)
                    - Only say "I don't have enough information" if BOTH documents and tools fail or return nothing useful
                    """)
                .build();

        Instant execStart = Instant.now();
        ChatResponse response = focusedClient.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatResponse();
        Duration execElapsed = Duration.between(execStart, Instant.now());

        Duration totalElapsed = Duration.between(start, Instant.now());

        log.debug("\n⚡ Execution time: " + execElapsed.toMillis() + "ms");
        log.debug("  Total time: " + totalElapsed.toMillis() + "ms");
        log.debug("═══════════════════════════════════════════\n");

        return new MetaSelectionResponse(
                response.getResult().getOutput().getText(),
                selectedTools,
                allTools.size() - selectedTools.size(),
                metaElapsed.toMillis(),
                execElapsed.toMillis(),
                totalElapsed.toMillis()
        );
    }

    // =================================================================
    // APPROACH 3: metaReasoning WITH PLAN (Most sophisticated)
    // =================================================================
    @GetMapping("/metaReasoning")
    public MetaReasoningResponse metaReasoning(
            @RequestParam String question,
            @RequestParam(defaultValue = "default") String conversationId) {

        Instant start = Instant.now();

        log.debug("\n═══════════════════════════════════════════");
        log.debug("🎯 metaReasoning APPROACH");
        log.debug("═══════════════════════════════════════════");
        log.debug("Question: " + question);
        log.debug("Strategy: Create execution plan, then execute step-by-step");

        // STEP 1: Meta-agent creates detailed execution plan
        String planningPrompt = String.format("""
                Create a step-by-step execution plan to answer this question:
                
                Question: "%s"
                
                Available tools: %s
                
                Respond in this exact format:
                REASONING: [Your strategic reasoning]
                STEPS:
                1. [tool_name] - [purpose]
                2. [tool_name] - [purpose]
                ...
                
                Be strategic - think about what information you need and in what order.
                """, question, String.join(", ", allTools.keySet()));

        Instant planStart = Instant.now();
        String planText = metaAgent.prompt()
                .user(planningPrompt)
                .call()
                .content();
        Duration planElapsed = Duration.between(planStart, Instant.now());

        ExecutionPlan plan = parsePlan(planText);

        log.debug("\n📊 Execution Plan (" + planElapsed.toMillis() + "ms):");
        log.debug("   Reasoning: " + plan.reasoning());
        log.debug("   Steps:");
        for (int i = 0; i < plan.steps().size(); i++) {
            PlanStep step = plan.steps().get(i);
            log.debug("      " + (i+1) + ". " + step.action() + " - " + step.purpose());
        }

        // STEP 2: Extract needed tools from plan
        List<String> toolsNeeded = plan.steps().stream()
                .map(PlanStep::action)
                .distinct()
                .collect(Collectors.toList());

        log.debug("\n   Tools required: " + toolsNeeded);

        // STEP 3: Execute with plan-aware system prompt
        ChatClient reasoningClient = baseBuilder
                .defaultToolNames(toolsNeeded.toArray(new String[0]))
                .defaultSystem(String.format("""
                        You are a helpful AI assistant. Execute this plan to answer the user's question.

                        EXECUTION RULES:
                        1. Always check the DOCUMENTS section first
                        2. If documents are incomplete or missing information, EXECUTE THE TOOLS in the plan below
                        3. For company queries like "What is [Company]", you MUST call getMarketNews to get comprehensive information
                        4. Synthesize information from ALL news articles to describe what the company does, their industry, and products
                        5. Documents may have partial info (e.g., "worked at Zscaler") but not complete info (e.g., "what Zscaler is")
                        6. When documents lack the answer, FOLLOW THE PLAN and use the tools provided

                        REASONING: %s

                        EXECUTION PLAN - FOLLOW THESE STEPS:
                        %s

                        Be transparent about which sources you used (documents vs tools).
                        """,
                        plan.reasoning(),
                        plan.steps().stream()
                                .map(s -> "- " + s.action() + ": " + s.purpose())
                                .collect(Collectors.joining("\n"))))
                .build();

        Instant execStart = Instant.now();
        ChatResponse response = reasoningClient.prompt()
                .user(question)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .chatResponse();
        Duration execElapsed = Duration.between(execStart, Instant.now());

        Duration totalElapsed = Duration.between(start, Instant.now());

        log.debug("\nExecution time: " + execElapsed.toMillis() + "ms");
        log.debug("  Total time: " + totalElapsed.toMillis() + "ms");
        log.debug("═══════════════════════════════════════════\n");

        return new MetaReasoningResponse(
                response.getResult().getOutput().getText(),
                plan.reasoning(),
                plan.steps(),
                toolsNeeded,
                planElapsed.toMillis(),
                execElapsed.toMillis(),
                totalElapsed.toMillis()
        );
    }

    // =================================================================
    // COMPARISON ENDPOINT - Run all three approaches
    // =================================================================
    @GetMapping("/compareAll")
    public ComparisonResult compareAllApproaches(
            @RequestParam String question,
            @RequestParam(defaultValue = "compare") String conversationId) {

        log.debug("\n");
        log.debug("╔═══════════════════════════════════════════════════════╗");
        log.debug("║     COMPARING ALL THREE AGENTIC APPROACHES           ║");
        log.debug("╚═══════════════════════════════════════════════════════╝");
        log.debug("Question: " + question);
        log.debug("═══════════════════════════════════════════════════════\n");

        // Run all three
        FullyAgenticResponse fullyAgentic = fullyAgentic(question, conversationId + "-1");
        MetaSelectionResponse metaSelection = metaSelection(question, conversationId + "-2");
        MetaReasoningResponse metaReasoning = metaReasoning(question, conversationId + "-3");

        log.debug("\n╔═══════════════════════════════════════════════════════╗");
        log.debug("║                   COMPARISON SUMMARY                   ║");
        log.debug("╚═══════════════════════════════════════════════════════╝");
        log.debug("Fully Agentic:    " + fullyAgentic.executionTimeMs() + "ms");
        log.debug("Meta Selection:   " + metaSelection.totalTimeMs() + "ms");
        log.debug("Meta Reasoning:   " + metaReasoning.totalTimeMs() + "ms");
        log.debug("═══════════════════════════════════════════════════════\n");


        return new ComparisonResult(question, fullyAgentic, metaSelection, metaReasoning);
    }

    // =================================================================
    // Helper Methods
    // =================================================================

    private List<String> parseToolList(String json) {
        log.debug("  Parsing tool list from meta-agent response: " + json);

        // Remove JSON array brackets and quotes
        json = json.trim()
                .replaceAll("^\\[|\\]$", "")
                .replaceAll("\"", "")
                .trim();

        // Handle edge cases: "None", "none", "N/A", empty, etc.
        if (json.isEmpty() ||
            json.equalsIgnoreCase("none") ||
            json.equalsIgnoreCase("n/a") ||
            json.equalsIgnoreCase("null")) {
            log.debug("   Meta-agent returned no tools (response was: " + json + ")");
            return List.of();
        }

        // Split and filter out any invalid tool names
        String[] toolNames = json.split(",\\s*");
        List<String> validTools = new ArrayList<>();

        for (String toolName : toolNames) {
            String trimmed = toolName.trim();
            // Only include if it's in our allTools map
            if (allTools.containsKey(trimmed)) {
                validTools.add(trimmed);
            } else {
                log.warn("  Meta-agent suggested invalid/unknown tool: '" + trimmed + "' (ignoring)");
            }
        }

        log.debug(" Validated tools: " + validTools);
        return validTools;
    }

    private ExecutionPlan parsePlan(String planText) {
        log.debug(" Parsing execution plan from meta-agent response");

        // Simple parsing - in production use proper JSON parsing
        String[] lines = planText.split("\n");
        String reasoning = "";
        List<PlanStep> steps = new ArrayList<>();

        boolean inSteps = false;
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("REASONING:")) {
                reasoning = line.substring("REASONING:".length()).trim();
            } else if (line.startsWith("STEPS:")) {
                inSteps = true;
            } else if (inSteps && line.matches("^\\d+\\..*")) {
                // Parse step: "1. getStockPrice - Get current prices"
                String stepText = line.replaceFirst("^\\d+\\.\\s*", "");
                String[] parts = stepText.split("-", 2);
                if (parts.length >= 1) {
                    String toolName = parts[0].trim();
                    String purpose = parts.length > 1 ? parts[1].trim() : "Execute";

                    // Only add valid tools that exist in our allTools map
                    if (allTools.containsKey(toolName)) {
                        steps.add(new PlanStep(toolName, purpose));
                    } else {
                        log.warn("Plan includes invalid tool: '" + toolName + "' (skipping)");
                    }
                }
            }
        }

        // If no steps, don't create a fake one - just return empty
        log.debug("   ✅ Parsed plan: " + steps.size() + " steps");
        return new ExecutionPlan(
                reasoning.isEmpty() ? "Check documents for answer" : reasoning,
                steps  // No fallback - empty list is valid!
        );
    }

    // =================================================================
    // Response Records
    // =================================================================

    record FullyAgenticResponse(
            String answer,
            List<String> availableTools,
            String strategy,
            long executionTimeMs
    ) {}

    record MetaSelectionResponse(
            String answer,
            List<String> selectedTools,
            int toolsFilteredOut,
            long metaAgentTimeMs,
            long executionTimeMs,
            long totalTimeMs
    ) {}

    record MetaReasoningResponse(
            String answer,
            String reasoning,
            List<PlanStep> executionPlan,
            List<String> toolsUsed,
            long planningTimeMs,
            long executionTimeMs,
            long totalTimeMs
    ) {}

    record ComparisonResult(
            String question,
            FullyAgenticResponse fullyAgentic,
            MetaSelectionResponse metaSelection,
            MetaReasoningResponse metaReasoning
    ) {}

    record ExecutionPlan(String reasoning, List<PlanStep> steps) {}
    record PlanStep(String action, String purpose) {}
}
