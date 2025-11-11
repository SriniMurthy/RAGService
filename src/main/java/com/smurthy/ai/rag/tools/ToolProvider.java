package com.smurthy.ai.rag.tools;

import java.util.List;

/**
 * UNUSED - OPTION B ORCHESTRATION PATH
 *
 * Interface for all tool providers to register their tools
 *
 * Implementations discover and expose tools from different frameworks:
 * - Spring AI function beans
 * - Langchain4j MCP agents
 * - External MCP servers
 *
 * Currently using Option A (MasterAgent) instead. This is preserved for potential revert.
 */
@Deprecated // Using MasterAgent (Option A) - keeping this for potential revert
public interface ToolProvider {

    /**
     * Get metadata about all tools this provider offers
     *
     * @return List of tool metadata for registration
     */
    List<ToolMetadata> getTools();

    /**
     * Get the framework this provider uses
     *
     * @return The tool framework enum
     */
    ToolFramework getFramework();

    /**
     * Get a human-readable name for this provider
     *
     * @return Provider name
     */
    default String getProviderName() {
        return getFramework().name() + " Provider";
    }
}