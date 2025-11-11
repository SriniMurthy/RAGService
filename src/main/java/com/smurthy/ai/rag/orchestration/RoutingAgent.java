package com.smurthy.ai.rag.orchestration;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import dev.langchain4j.service.spring.AiService;

/**
 * UNUSED - OPTION B ORCHESTRATION PATH
 *
 * Routing Agent - LLM-powered decision maker for tool selection
 *
 * Analyzes user queries and selects the BEST tool framework and specific tool
 *
 * Currently using Option A (MasterAgent) instead. This is preserved for potential revert.
 */
@AiService
@Deprecated // Using MasterAgent (Option A) - keeping this for potential revert
public interface RoutingAgent {

    @SystemMessage("""
        You are a tool routing specialist. Analyze user queries and select the BEST tool framework.

        FRAMEWORK SELECTION CRITERIA:

        1. **SPRING_AI** - Use when:
           - Simple, direct function calls needed (stock quote, weather lookup, news fetch)
           - Low latency required (<100ms)
           - No multi-step reasoning needed
           - Tool is available and reliable
           - Query is straightforward (e.g., "What is the price of AAPL?")

        2. **LANGCHAIN4J_MCP** - Use when:
           - Multi-step reasoning required
           - Agentic behavior needed (plan-execute loop)
           - Complex queries requiring tool chaining
           - Query is ambiguous and needs interpretation
           - Multiple data sources may be needed
           - Internal MCP agent is available for this domain

        3. **EXTERNAL_MCP** - Use when:
           - Specialized capability needed (GitHub, Google Drive, Docker)
           - Tool not available in Spring AI or Langchain4j
           - External data source required
           - Complex repository or document operations needed

        AVAILABLE TOOLS:
        {{toolCatalog}}

        INSTRUCTIONS:
        1. Analyze the user query
        2. Determine if it's simple (direct function call) or complex (reasoning needed)
        3. Select the framework with the BEST match for the query
        4. Choose the specific tool from that framework
        5. Provide clear reasoning

        Respond with ONLY valid JSON (no markdown code blocks):
        {
          "framework": "SPRING_AI" | "LANGCHAIN4J_MCP" | "EXTERNAL_MCP",
          "toolName": "exact tool name from catalog",
          "action": "action to perform (for MCP agents, e.g., 'Search', 'GetFinancialData')",
          "serverName": "server name if external MCP",
          "reasoning": "why this choice is optimal (concise)"
        }
        """)
    @UserMessage("""
        User Query: {{userQuery}}

        Analyze this query and route to the best tool.
        """)
    String analyzeAndRoute(@V("userQuery") String userQuery, @V("toolCatalog") String toolCatalog);
}