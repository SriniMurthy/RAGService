package com.smurthy.ai.rag.tools;

// UNUSED - OPTION B ORCHESTRATION PATH
// Currently using Option A (MasterAgent) instead. This is preserved for potential revert.

/**
 * Metadata about each tool for intelligent routing decisions
 */
@Deprecated // Using MasterAgent (Option A) - keeping this for potential revert
public record ToolMetadata(
    String name,              // Tool name (e.g., "getWeatherByLocation", "WeatherAgent")
    String description,       // Human-readable description
    String category,          // "weather", "finance", "code", "documents"
    String provider,          // "Spring AI Functions", "Langchain4j MCP Agent"
    ToolFramework framework,  // Which framework this tool uses
    String averageLatency,    // "50ms", "200ms"
    String costPerCall,       // "FREE", "$0.001"
    String reliability        // "99.9%", "95%"
) {
    /**
     * Create a concise summary for LLM consumption
     */
    public String toSummary() {
        return String.format("""
            **%s** [%s]
            Description: %s
            Provider: %s | Framework: %s
            Performance: %s latency, %s cost, %s reliable
            """,
            name,
            category,
            description,
            provider,
            framework,
            averageLatency,
            costPerCall,
            reliability
        );
    }
}