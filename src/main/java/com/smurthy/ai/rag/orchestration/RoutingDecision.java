package com.smurthy.ai.rag.orchestration;

import com.smurthy.ai.rag.tools.ToolFramework;

// UNUSED - OPTION B ORCHESTRATION PATH
// Currently using Option A (MasterAgent) instead. This is preserved for potential revert.

/**
 * Routing decision from the RoutingAgent
 *
 * Contains which framework to use and which specific tool/agent
 */
@Deprecated // Using MasterAgent (Option A) - keeping this for potential revert
public record RoutingDecision(
    ToolFramework framework,   // Which framework to execute with
    String toolName,           // Exact tool/agent name
    String action,             // Action to perform (for MCP agents)
    String serverName,         // Server name (for external MCP)
    String reasoning           // LLM's reasoning for this choice
) {
    /**
     * Create decision for Spring AI
     */
    public static RoutingDecision springAI(String toolName, String reasoning) {
        return new RoutingDecision(
            ToolFramework.SPRING_AI,
            toolName,
            null,
            null,
            reasoning
        );
    }

    /**
     * Create decision for Langchain4j MCP
     */
    public static RoutingDecision langchain4jMCP(String agentName, String action, String reasoning) {
        return new RoutingDecision(
            ToolFramework.LANGCHAIN4J_MCP,
            agentName,
            action,
            null,
            reasoning
        );
    }

    /**
     * Create decision for External MCP
     */
    public static RoutingDecision externalMCP(String serverName, String toolName, String reasoning) {
        return new RoutingDecision(
            ToolFramework.EXTERNAL_MCP,
            toolName,
            null,
            serverName,
            reasoning
        );
    }
}