package com.smurthy.ai.rag.mcp;


import java.util.Map;

/**
 * Common interface for all Model Context Protocol (MCP) agents.
 * Each agent (e.g., Weather, Finance) will implement this interface
 * to handle requests targeted to it.
 */
public interface MCPAgent {

    /**
     * Handles an incoming MCP request.
     *
     * @param request The MCP request containing the target agent, action, and parameters.
     * @return An MCP response containing the result from the agent.
     */
    MCPResponse handleRequest(MCPRequest request);

    /**
     * Standard request format for the Model Context Protocol.
     */
    record MCPRequest(String targetAgent, String action, Map<String, String> parameters) {}

    /**
     * Standard response format for the Model Context Protocol.
     */
    record MCPResponse(String source, String summary) {}
}