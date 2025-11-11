package com.smurthy.ai.rag.tools.providers;

import com.smurthy.ai.rag.mcp.MCPAgent;
import com.smurthy.ai.rag.tools.ToolFramework;
import com.smurthy.ai.rag.tools.ToolMetadata;
import com.smurthy.ai.rag.tools.ToolProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Discovers all Langchain4j MCP agents
 *
 * Scans for beans implementing MCPAgent interface
 * Uses lazy lookup to avoid circular dependency with @AiService beans
 */
@Component
public class Langchain4jMCPToolProvider implements ToolProvider {

    private static final Logger log = LoggerFactory.getLogger(Langchain4jMCPToolProvider.class);

    private final ApplicationContext context;

    public Langchain4jMCPToolProvider(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public List<ToolMetadata> getTools() {
        List<ToolMetadata> tools = new ArrayList<>();

        // Lazy lookup of MCPAgent beans to avoid circular dependency
        Map<String, MCPAgent> mcpAgents = context.getBeansOfType(MCPAgent.class);

        // Each MCP agent becomes a registered tool
        for (Map.Entry<String, MCPAgent> entry : mcpAgents.entrySet()) {
            String agentName = entry.getKey();

            tools.add(new ToolMetadata(
                agentName,
                generateDescription(agentName),
                categorizeFromName(agentName),
                "Langchain4j MCP Agent",
                ToolFramework.LANGCHAIN4J_MCP,
                "300ms",     // Higher latency (multi-step reasoning with LLM)
                "VARIES",    // Depends on LLM usage
                "95%"        // Slightly lower due to LLM dependencies
            ));

            log.debug("Registered Langchain4j MCP agent: {}", agentName);
        }

        log.info("Langchain4j MCP Tool Provider discovered {} agents", tools.size());
        return tools;
    }

    @Override
    public ToolFramework getFramework() {
        return ToolFramework.LANGCHAIN4J_MCP;
    }

    /**
     * Generate description based on agent name
     */
    private String generateDescription(String agentName) {
        return String.format(
            "Agentic MCP service for %s with multi-step reasoning capabilities",
            agentName.replace("Agent", "").toLowerCase()
        );
    }

    /**
     * Categorize agent based on name heuristics
     */
    private String categorizeFromName(String name) {
        String lowerName = name.toLowerCase();

        if (lowerName.contains("weather")) return "weather";
        if (lowerName.contains("finance") || lowerName.contains("stock")) return "finance";
        if (lowerName.contains("github") || lowerName.contains("code") ||
            lowerName.contains("repository")) return "code";
        if (lowerName.contains("drive") || lowerName.contains("document")) return "documents";
        if (lowerName.contains("docker") || lowerName.contains("container")) return "infrastructure";
        if (lowerName.contains("search") || lowerName.contains("rag")) return "search";

        return "general";
    }
}