package com.smurthy.ai.rag.orchestration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * UNUSED - OPTION B ORCHESTRATION PATH
 *
 * Executor for external MCP servers
 *
 * FUTURE IMPLEMENTATION: Enable when external MCP servers are configured
 *
 * Currently using Option A (MasterAgent) instead. This is preserved for potential revert.
 */
@Component
@ConditionalOnProperty(name = "mcp.external.enabled", havingValue = "true", matchIfMissing = true)
@Deprecated // Using MasterAgent (Option A) - keeping this for potential revert
public class ExternalMCPExecutor {

    private static final Logger log = LoggerFactory.getLogger(ExternalMCPExecutor.class);

    // TODO: Inject external MCP clients when available
    // private final Map<String, DefaultMcpClient> externalServers;

    public ExternalMCPExecutor() {
        log.info("External MCP Executor initialized (skeleton - no external servers configured)");
    }

    /**
     * Execute a tool on an external MCP server
     *
     * @param serverName Name of the external server
     * @param toolName Name of the tool to execute
     * @param query User query/parameters
     * @return Result from external MCP server
     */
    public String execute(String serverName, String toolName, String query) {
        log.warn("External MCP execution requested but not implemented yet: server={}, tool={}, query={}",
            serverName, toolName, query);

        // TODO: When Java MCP SDK is integrated:
        /*
        DefaultMcpClient client = externalServers.get(serverName);
        if (client == null) {
            throw new IllegalStateException("External MCP server not found: " + serverName);
        }

        Map<String, Object> arguments = parseQueryToArguments(query);
        Object result = client.callTool(toolName, arguments);
        return result.toString();
        */

        return String.format(
            "External MCP execution not yet implemented. " +
            "To enable: configure external MCP servers and set mcp.external.enabled=true. " +
            "Requested: server=%s, tool=%s",
            serverName, toolName
        );
    }
}