package com.smurthy.ai.rag.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Specialized Agent for Research/Document Queries
 *
 * Handles queries about user's uploaded documents and RAG knowledge base.
 * Has access ONLY to document/RAG tools.
 */
@Component
public class ResearchAgent {

    private static final Logger log = LoggerFactory.getLogger(ResearchAgent.class);
    private final ChatClient researchChatClient;

    // Only RAG/document tools
    private static final Set<String> RESEARCH_TOOLS = Set.of(
            "queryDocuments",
            "queryDocumentsByYear",
            "queryDocumentsByDateRange",
            "queryDocumentsAdvanced"
    );

    private static final String SYSTEM_PROMPT = """
            You are a specialized Research AI agent with expertise in analyzing documents and knowledge bases.

            YOUR CAPABILITIES:
            - Search and analyze user's uploaded documents
            - Retrieve information from the RAG knowledge base
            - Synthesize information across multiple document chunks
            - Time-based document queries (by year, date range)

            RULES:
            - Only provide information found in the documents
            - If information is not in the documents, explicitly state: "I don't have information about this in the available documents."
            - Cite document sources when possible
            - Be thorough but concise
            - If asked about real-time data (stocks, news), politely decline and suggest using appropriate tools

            Use your document search tools to find accurate information.
            """;

    public ResearchAgent(ChatClient.Builder chatClientBuilder, ChatMemory chatMemory) {
        this.researchChatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .defaultToolNames(RESEARCH_TOOLS.toArray(new String[0]))
                .build();

        log.info("ResearchAgent initialized with {} tools", RESEARCH_TOOLS.size());
    }

    /**
     * Execute a research/document query
     *
     * @param question The user's question
     * @param conversationId Conversation ID for memory
     * @return AgentResult with document findings
     */
    public AgentResult execute(String question, String conversationId) {
        log.debug("[ResearchAgent] Processing: {}", question);
        long startTime = System.currentTimeMillis();

        try {
            String result = researchChatClient.prompt()
                    .user(question)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                    .call()
                    .content();

            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[ResearchAgent] Completed in {}ms", elapsed);

            return AgentResult.success("ResearchAgent", result, elapsed);

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[ResearchAgent] Error processing query", e);
            return AgentResult.failure("ResearchAgent",
                    "Failed to search documents: " + e.getMessage(), elapsed);
        }
    }
}