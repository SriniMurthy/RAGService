package com.smurthy.ai.rag.orchestration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smurthy.ai.rag.mcp.MCPAgent;
import com.smurthy.ai.rag.mcp.MCPClient;
import com.smurthy.ai.rag.tools.UnifiedToolRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * UNUSED - OPTION B ORCHESTRATION PATH
 *
 * Agentic Tool Orchestrator
 *
 * Main entry point for unified tool execution.
 * Intelligently routes queries to:
 * - Spring AI tools (fast, direct function calls)
 * - Langchain4j MCP agents (multi-step reasoning)
 * - External MCP servers (specialized capabilities)
 *
 * Uses @Lazy to avoid circular dependency with @AiService beans (RoutingAgent)
 *
 * Currently using Option A (MasterAgent) instead. This is preserved for potential revert.
 */
@Service
@org.springframework.context.annotation.Lazy
@Deprecated // Using MasterAgent (Option A) - keeping this for potential revert
public class ToolOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ToolOrchestrator.class);

    private final UnifiedToolRegistry toolRegistry;
    private final RoutingAgent routingAgent;
    private final ChatClient.Builder springAIChatClientBuilder;
    private final MCPClient mcpClient;
    private final ExternalMCPExecutor externalMCPExecutor;
    private final ObjectMapper objectMapper;

    public ToolOrchestrator(
            UnifiedToolRegistry toolRegistry,
            RoutingAgent routingAgent,
            ChatClient.Builder chatClientBuilder,
            MCPClient mcpClient,
            ExternalMCPExecutor externalMCPExecutor) {

        this.toolRegistry = toolRegistry;
        this.routingAgent = routingAgent;
        this.springAIChatClientBuilder = chatClientBuilder;
        this.mcpClient = mcpClient;
        this.externalMCPExecutor = externalMCPExecutor;
        this.objectMapper = new ObjectMapper();

        log.info("Tool Orchestrator initialized with {} tools",
            toolRegistry.getTotalToolCount());
    }

    /**
     * Main entry point - intelligently routes query to best tool system
     *
     * @param userQuery The user's natural language query
     * @return The response from the selected tool/agent
     */
    public OrchestrationResult execute(String userQuery) {
        log.info("Executing query via Tool Orchestrator: {}", userQuery);

        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Get tool catalog for LLM
            String toolCatalog = toolRegistry.getToolCatalogForLLM();

            // Step 2: Analyze query and get routing decision
            String routingResponse = routingAgent.analyzeAndRoute(userQuery, toolCatalog);
            RoutingDecision decision = parseRoutingDecision(routingResponse);

            log.info("Routing Decision: Framework={}, Tool={}, Reasoning={}",
                decision.framework(), decision.toolName(), decision.reasoning());

            // Step 3: Execute using selected framework
            String result = switch (decision.framework()) {
                case SPRING_AI -> executeSpringAI(userQuery, decision);
                case LANGCHAIN4J_MCP -> executeLangchain4jMCP(userQuery, decision);
                case EXTERNAL_MCP -> executeExternalMCP(userQuery, decision);
            };

            long elapsed = System.currentTimeMillis() - startTime;

            log.info("Query executed successfully in {}ms via {}",
                elapsed, decision.framework());

            return new OrchestrationResult(
                true,
                result,
                decision.framework().name(),
                decision.toolName(),
                decision.reasoning(),
                elapsed
            );

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("Error executing query: {}", e.getMessage(), e);

            return new OrchestrationResult(
                false,
                "Error: " + e.getMessage(),
                "UNKNOWN",
                "UNKNOWN",
                "Execution failed: " + e.getMessage(),
                elapsed
            );
        }
    }

    /**
     * Execute using Spring AI function calling
     */
    private String executeSpringAI(String query, RoutingDecision decision) {
        log.debug("Executing via Spring AI with tool: {}", decision.toolName());

        // Build ChatClient with specific function enabled
        ChatClient chatClient = springAIChatClientBuilder
            .clone()
            .defaultToolNames(decision.toolName())  // Enable specific function
            .build();

        return chatClient.prompt()
            .user(query)
            .call()
            .content();
    }

    /**
     * Execute using Langchain4j MCP agent
     */
    private String executeLangchain4jMCP(String query, RoutingDecision decision) {
        log.debug("Executing via Langchain4j MCP agent: {}", decision.toolName());

        // Determine action (default to "Search" if not specified)
        String action = decision.action() != null ? decision.action() : "Search";

        // Route to Langchain4j MCP agent
        MCPAgent.MCPRequest request = new MCPAgent.MCPRequest(
            decision.toolName(),  // Target agent (e.g., "WeatherAgent", "FinanceAgent")
            action,
            Map.of("Query", query)
        );

        MCPAgent.MCPResponse response = mcpClient.execute(request);
        return response.summary();
    }

    /**
     * Execute using external MCP server
     */
    private String executeExternalMCP(String query, RoutingDecision decision) {
        log.debug("Executing via External MCP: server={}, tool={}",
            decision.serverName(), decision.toolName());

        return externalMCPExecutor.execute(
            decision.serverName(),
            decision.toolName(),
            query
        );
    }

    /**
     * Parse routing agent's JSON response to RoutingDecision
     */
    private RoutingDecision parseRoutingDecision(String jsonResponse) {
        try {
            // Remove markdown code blocks if present
            String cleanJson = jsonResponse
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();

            JsonNode json = objectMapper.readTree(cleanJson);

            return new RoutingDecision(
                com.smurthy.ai.rag.tools.ToolFramework.valueOf(json.get("framework").asText()),
                json.get("toolName").asText(),
                json.has("action") && !json.get("action").isNull() ?
                    json.get("action").asText() : null,
                json.has("serverName") && !json.get("serverName").isNull() ?
                    json.get("serverName").asText() : null,
                json.get("reasoning").asText()
            );

        } catch (Exception e) {
            log.error("Failed to parse routing decision: {}", jsonResponse, e);
            throw new RuntimeException("Failed to parse routing decision: " + e.getMessage());
        }
    }

    /**
     * Result of orchestration execution
     */
    public record OrchestrationResult(
        boolean success,
        String result,
        String framework,
        String toolName,
        String reasoning,
        long executionTimeMs
    ) {
        public String toSummary() {
            return String.format("""
                === Execution Summary ===
                Success: %s
                Framework: %s
                Tool: %s
                Reasoning: %s
                Time: %dms

                Result:
                %s
                """,
                success,
                framework,
                toolName,
                reasoning,
                executionTimeMs,
                result
            );
        }
    }
}