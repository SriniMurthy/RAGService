package com.smurthy.ai.rag.tools.providers;

import com.smurthy.ai.rag.tools.ToolFramework;
import com.smurthy.ai.rag.tools.ToolMetadata;
import com.smurthy.ai.rag.tools.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Discovers tools from external MCP servers
 * (Docker MCP toolkit, GitHub server, Google Drive server)
 *
 * FUTURE IMPLEMENTATION: Enable when external MCP servers are configured
 */
@Component
@ConditionalOnProperty(name = "mcp.external.enabled", havingValue = "true", matchIfMissing = false)
public class ExternalMCPToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(ExternalMCPToolProvider.class);

    // TODO: Inject external MCP clients when available
    // private final List<DefaultMcpClient> externalServers;

    public ExternalMCPToolProvider() {
        log.info("External MCP Tool Provider initialized (skeleton - no external servers configured)");
    }

    @Override
    public List<ToolMetadata> getTools() {
        List<ToolMetadata> tools = new ArrayList<>();

        // TODO: When Java MCP SDK is integrated, discover tools from external servers
        /*
        for (DefaultMcpClient server : externalServers) {
            List<McpTool> serverTools = server.listTools();

            for (McpTool tool : serverTools) {
                tools.add(new ToolMetadata(
                    tool.name(),
                    tool.description(),
                    categorizeTool(tool.name()),
                    "External MCP Server",
                    ToolFramework.EXTERNAL_MCP,
                    "500ms",     // Network latency
                    "VARIES",
                    "90%"        // Lower reliability (network dependency)
                ));
            }
        }
        */

        log.info("External MCP Tool Provider discovered {} tools (currently 0 - skeleton)", tools.size());
        return tools;
    }

    @Override
    public ToolFramework getFramework() {
        return ToolFramework.EXTERNAL_MCP;
    }

    /**
     * Categorize external tool based on name heuristics
     */
    @SuppressWarnings("unused")
    private String categorizeTool(String name) {
        String lowerName = name.toLowerCase();

        if (lowerName.contains("github") || lowerName.contains("repository") ||
            lowerName.contains("clone") || lowerName.contains("commit")) return "code";
        if (lowerName.contains("drive") || lowerName.contains("document") ||
            lowerName.contains("file")) return "documents";
        if (lowerName.contains("docker") || lowerName.contains("container") ||
            lowerName.contains("image")) return "infrastructure";
        if (lowerName.contains("slack") || lowerName.contains("email")) return "communication";

        return "general";
    }
}