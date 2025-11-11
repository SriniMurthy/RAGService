package com.smurthy.ai.rag.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import java.util.Map;

/**
 * A router for the Model Context Protocol (MCP).
 * This client discovers all available McpAgent beans and acts as a switchboard,
 * routing incoming requests to the correct agent based on the 'targetAgent' field.
 * Uses lazy bean lookup to avoid circular dependencies with @AiService beans.
 */
@Service
public class MCPClient {

    private static final Logger log = LoggerFactory.getLogger(MCPClient.class);

    private final ApplicationContext context;

    public MCPClient(ApplicationContext context) {
        this.context = context;
        log.info("McpClient initialized (lazy agent lookup mode)");
    }

    /**
     * Executes an MCP request by routing it to the appropriate agent.
     *
     * @param request The MCP request to execute.
     * @return The response from the target agent.
     * @throws IllegalStateException if no agent is found for the given target.
     */
    public MCPAgent.MCPResponse execute(MCPAgent.MCPRequest request) {
        String targetAgentName = request.targetAgent();
        log.debug("Routing MCP request to target agent: {}", targetAgentName);

        // Lazy lookup of MCPAgent beans
        Map<String, MCPAgent> agentMap = context.getBeansOfType(MCPAgent.class);
        MCPAgent agent = agentMap.get(targetAgentName);

        if (agent == null) {
            log.error("No MCP Agent bean found for target: '{}'. Available agents are: {}",
                    targetAgentName, agentMap.keySet());
            throw new IllegalStateException("No MCP Agent found for target: " + targetAgentName);
        }

        return agent.handleRequest(request);
    }
}
