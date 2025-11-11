package com.smurthy.ai.rag.mcp;


import com.smurthy.ai.rag.mcp.orchestrator.OrchestratorAgent;
import org.springframework.stereotype.Service;

/**
 * UNUSED - EXPERIMENTAL MCP ORCHESTRATOR
 *
 * The MCP "Server" implementation for the master Orchestrator Agent.
 * This service is registered with the bean name "OrchestratorAgent" and acts
 * as the main entry point for all high-level queries.
 *
 * Currently using MasterAgent instead. This is preserved for reference.
 */
@Service("OrchestratorAgent") // The bean name for the MCP router
@Deprecated // Using MasterAgent - keeping this for reference
public class MCPOrchestratorService implements MCPAgent {

    private final OrchestratorAgent orchestratorAgent;

    public MCPOrchestratorService(OrchestratorAgent orchestratorAgent) {
        this.orchestratorAgent = orchestratorAgent;
    }

    @Override
    public MCPResponse handleRequest(MCPRequest request) {
        if (!"Ask".equals(request.action())) {
            return new MCPResponse("Error", "Invalid action '" + request.action() + "' for OrchestratorAgent.");
        }

        String userQuery = request.parameters().get("Query");
        if (userQuery == null || userQuery.isBlank()) {
            return new MCPResponse("Error", "Missing 'Query' parameter for OrchestratorAgent request.");
        }

        // The LangChain4j agent will now call the tool that wraps your entire /ask API
        String agentResponse = orchestratorAgent.orchestrate(userQuery);

        return new MCPResponse("OrchestratorAgent", agentResponse);
    }
}