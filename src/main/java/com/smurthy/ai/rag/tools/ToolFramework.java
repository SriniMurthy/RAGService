package com.smurthy.ai.rag.tools;

// UNUSED - OPTION B ORCHESTRATION PATH
// Currently using Option A (MasterAgent) instead. This is preserved for potential revert.

/**
 * Enum representing different tool execution frameworks
 */
@Deprecated // Using MasterAgent (Option A) - keeping this for potential revert
public enum ToolFramework {
    /**
     * Spring AI function beans - Fast, direct function calls
     */
    SPRING_AI,

    /**
     * Langchain4j MCP agents - Multi-step reasoning, internal
     */
    LANGCHAIN4J_MCP,

    /**
     * External MCP servers - GitHub, Google Drive, Docker toolkit
     */
    EXTERNAL_MCP
}