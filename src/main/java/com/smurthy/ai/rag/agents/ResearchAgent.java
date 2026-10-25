package com.smurthy.ai.rag.agents;

import com.smurthy.ai.rag.config.RAGFunctionConfiguration;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Specialized Agent for Research/Document Queries
 *
 * Retrieves documents via queryDocuments function bean directly (bypasses Spring AI tool-calling),
 * then synthesizes an answer using LangChain4j's ChatModel (bypasses Spring AI RestClient bug).
 */
@Component
public class ResearchAgent {

    private static final Logger log = LoggerFactory.getLogger(ResearchAgent.class);
    private final ChatModel chatModel;
    private final Function<RAGFunctionConfiguration.DocumentQueryRequest,
                           RAGFunctionConfiguration.DocumentQueryResponse> queryDocumentsFn;

    private static final String SYSTEM_PROMPT = """
            You are a specialized Research AI agent with expertise in analyzing documents and knowledge bases.

            YOUR CAPABILITIES:
            - Search and analyze user's uploaded documents (PDFs, Excel, Word, scanned images, resumes, reports, etc.)
            - Retrieve information from the RAG knowledge base

            CRITICAL RULES - MUST FOLLOW:
            1. Answer ONLY from the DOCUMENTS section provided.
            2. Each retrieved chunk begins with a header like === DOCUMENT CHUNK ===, Source: filename.
               Read this header carefully — it tells you exactly which document the chunk belongs to.
            3. NEVER merge information from different source documents into a single answer about one person.
               If two chunks come from different files, they describe DIFFERENT people or documents.
            4. For EVERY fact you state, you MUST attribute it: "According to [filename]: ..."
            5. If asked about a specific person, only use chunks from that person's file.
            6. If the DOCUMENTS section says "No relevant documents found", state exactly:
               "I don't have information about this in the available documents."
            7. If asked about real-time data (stocks, weather), decline and suggest appropriate tools.

            ANTI-HALLUCINATION RULES:
            - Only state facts that appear in the retrieved chunks.
            - Do not infer, assume, or fill gaps between chunks from different source files.
            """;

    public ResearchAgent(
            ChatModel chatModel,
            @Qualifier("queryDocuments") Function<RAGFunctionConfiguration.DocumentQueryRequest,
                                                  RAGFunctionConfiguration.DocumentQueryResponse> queryDocumentsFn) {
        this.chatModel = chatModel;
        this.queryDocumentsFn = queryDocumentsFn;
        log.info("ResearchAgent initialized (LangChain4j + direct retrieval)");
    }

    /**
     * Execute a research/document query
     *
     * @param question The user's question
     * @param conversationId Conversation ID for memory (unused here — stateless synthesis)
     * @return AgentResult with document findings
     */
    public AgentResult execute(String question, String conversationId) {
        log.debug("[ResearchAgent] Processing: {}", question);
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: Retrieve documents directly — no LLM tool-routing round-trip
            RAGFunctionConfiguration.DocumentQueryResponse retrieved =
                    queryDocumentsFn.apply(new RAGFunctionConfiguration.DocumentQueryRequest(question));

            log.info("[ResearchAgent] Retrieved {} documents for query", retrieved.documentsFound());

            // Step 2: Format chunks into a synthesis prompt
            String chunksText;
            if (retrieved.documentsFound() == 0 || retrieved.documents().isEmpty()) {
                chunksText = "No relevant documents found.";
            } else {
                chunksText = retrieved.documents().stream()
                        .map(RAGFunctionConfiguration.DocumentQueryResponse.DocumentChunk::toFormattedString)
                        .collect(Collectors.joining("\n"));
            }

            String userPrompt = """
                    QUESTION: %s

                    DOCUMENTS:
                    %s

                    Answer the question above using ONLY the documents provided.
                    Attribute every fact to its source file.
                    """.formatted(question, chunksText);

            // Step 3: Synthesize using LangChain4j ChatModel directly
            ChatResponse response = chatModel.chat(List.of(
                    SystemMessage.from(SYSTEM_PROMPT),
                    UserMessage.from(userPrompt)
            ));
            String result = response.aiMessage().text();

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
