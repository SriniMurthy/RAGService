package com.smurthy.ai.rag.mcp.orchestrator;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.spring.AiService;

// UNUSED - EXPERIMENTAL MCP ORCHESTRATOR
// This was an experimental approach. Currently using MasterAgent instead.
@AiService
@Deprecated // Using MasterAgent - keeping this for reference
public interface OrchestratorAgent {

    @SystemMessage("""
        You are a master orchestrator. Your one and only job is to pass the user's query
        directly to the 'askUnifiedAgent' tool and return its output.
        Do not attempt to answer the question yourself.
        Do not try to use any other tools.
        Simply delegate the entire query to the 'askUnifiedAgent' tool.
    """)
    String orchestrate(String userQuery);
}
