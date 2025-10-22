package com.smurthy.ai.rag.retrieval;

import com.smurthy.ai.rag.service.HybridRetrievalService;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;

/**
 * Hybrid Document Retriever for Spring AI RAG
 *
 * Integrates hybrid retrieval (dense vector + sparse BM25 + reranking)
 * with Spring AI's DocumentRetriever interface.
 *
 * Pipeline:
 * 1. Dense retrieval (vector search)
 * 2. Sparse retrieval (BM25)
 * 3. RRF fusion
 * 4. Cross-encoder reranking
 * 5. Return top-K documents
 *
 * Configuration (application.yaml):
 * - app.retrieval.topK: Final number of documents (default: 5)
 * - app.retrieval.similarityThreshold: Minimum similarity (default: 0.60)
 * - app.retrieval.candidateMultiplier: How many candidates to retrieve before reranking (default: 4)
 *
 * This retriever can be used directly with RetrievalAugmentationAdvisor.
 */
@Component("hybridDocumentRetriever")
@ConditionalOnBean(HybridRetrievalService.class)
public class HybridDocumentRetriever implements DocumentRetriever {

    private final HybridRetrievalService hybridRetrievalService;

    // Configurable parameters from application.yaml
    private final int defaultTopK;
    private final double defaultSimilarityThreshold;

    public HybridDocumentRetriever(
            HybridRetrievalService hybridRetrievalService,
            @Value("${app.retrieval.topK:5}") int defaultTopK,
            @Value("${app.retrieval.similarityThreshold:0.60}") double defaultSimilarityThreshold) {
        this.hybridRetrievalService = hybridRetrievalService;
        this.defaultTopK = defaultTopK;
        this.defaultSimilarityThreshold = defaultSimilarityThreshold;
    }

    @Override
    public List<Document> retrieve(Query query) {
        // Extract parameters from query metadata if present, otherwise use defaults
        int topK = defaultTopK;
        double similarityThreshold = defaultSimilarityThreshold;

        if (query.context() != null) {
            if (query.context().containsKey("topK")) {
                topK = (int) query.context().get("topK");
            }
            if (query.context().containsKey("similarityThreshold")) {
                similarityThreshold = (double) query.context().get("similarityThreshold");
            }
        }

        // Perform hybrid retrieval
        HybridRetrievalService.HybridRetrievalResult result =
                hybridRetrievalService.retrieve(query.text(), topK, similarityThreshold);

        return result.documents();
    }
}