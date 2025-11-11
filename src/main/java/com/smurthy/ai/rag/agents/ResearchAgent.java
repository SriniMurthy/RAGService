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
            - Search and analyze user's uploaded documents (PDFs, Excel, Word, scanned images, etc.)
            - Retrieve information from the RAG knowledge base
            - Synthesize information across multiple document chunks
            - Time-based document queries (by year, date range)

            CRITICAL RULES - MUST FOLLOW:
            1. ALWAYS call `queryDocuments` tool for EVERY question you receive
            2. The document store contains: resumes, PDFs, scanned images, news articles, reports, Excel files, Word docs
            3. Only provide information found in the documents
            4. If `queryDocuments` returns "No relevant documents found", then state: "I don't have information about this in the available documents."
            5. Cite document sources when possible (file names, dates)
            6. Be thorough but concise
            7. If asked about real-time data (stocks, weather), politely decline and suggest using appropriate tools

            WORKFLOW:
            Step 1: Call `queryDocuments` with the user's question
            Step 2: Analyze the retrieved documents
            Step 3: Synthesize and answer based on document content
            Step 4: If no documents found, inform the user

            Remember: ALWAYS search documents first - they may contain news articles, reports, or information you wouldn't expect!
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