package com.smurthy.ai.rag.mcp.orchestrator;

import com.smurthy.ai.rag.controllers.UnifiedAgenticController;
import dev.langchain4j.agent.tool.Tool;
import org.springframework.stereotype.Component;

/**
 * A LangChain4j Tool that wraps the main UnifiedAgenticController.
 * This exposes the entire high-level reasoning capability of the RAG service
 * as a single tool for a master orchestrator agent.
 */
@Component
public class OrchestratorTools {

    private final UnifiedAgenticController unifiedAgenticController;

    public OrchestratorTools(UnifiedAgenticController unifiedAgenticController) {
        this.unifiedAgenticController = unifiedAgenticController;
    }

    @Tool("Use this master tool to answer any complex query. It has access to all financial, weather, news, and document search capabilities. This is the main entry point for all questions.")
    public String askUnifiedAgent(String query) {
        // We call the existing ask method and return only the answer string.
        // A default conversation ID is used as this context is stateless.
        return unifiedAgenticController.ask(query, "mcp-orchestrator-session").answer();
    }
}
