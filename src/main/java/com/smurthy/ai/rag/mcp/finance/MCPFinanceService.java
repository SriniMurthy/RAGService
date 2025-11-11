package com.smurthy.ai.rag.mcp.finance;

import com.smurthy.ai.rag.mcp.MCPAgent;
import org.springframework.stereotype.Service;

/**
 * The MCP "Server" implementation for the Finance Agent.
 * This service is registered with the bean name "FinanceAgent" to be discoverable
 * by the McpClient router. It delegates to MCPFinanceAgent which now has all
 * Spring AI tools wired in via Langchain4jAgentConfiguration.
 */
@Service("FinanceAgent") // The bean name MUST match the targetAgent name in the MCP request
public class MCPFinanceService implements MCPAgent {

    private final MCPFinanceAgent financeAgent;

    public MCPFinanceService(MCPFinanceAgent financeAgent) {
        this.financeAgent = financeAgent;
    }

    @Override
    public MCPResponse handleRequest(MCPRequest request) {
        // Validate that the action is one this agent can handle
        if (!"GetFinancialData".equals(request.action())) {
            return new MCPResponse("Error", "Invalid action '" + request.action() + "' for FinanceAgent.");
        }

        // Extract the user's natural language query from the parameters
        String userQuery = request.parameters().get("Query");
        if (userQuery == null || userQuery.isBlank()) {
            return new MCPResponse("Error", "Missing 'Query' parameter for FinanceAgent request.");
        }

        // The LangChain4j agent now has all Spring AI tools wired in
        String agentResponse = financeAgent.getFinancialData(userQuery);

        // Wrap the final, human-readable string in the standard MCP response
        return new MCPResponse("FinanceAgent", agentResponse);
    }
}
