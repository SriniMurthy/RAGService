package com.smurthy.ai.rag.service;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * Reranking Service Interface
 *
 * Reranking improves RAG retrieval quality by re-scoring retrieved chunks
 * using more sophisticated models than simple vector similarity.
 *
 * Why Reranking?
 * - Vector search retrieves ~10-20 candidates (high recall, lower precision)
 * - Reranking re-scores with cross-encoder (lower recall, high precision)
 * - Returns top-k most relevant chunks to LLM
 *
 * Typical pipeline:
 * 1. Vector search: Retrieve 20 candidates (threshold=0.30)
 * 2. Rerank: Score all 20 with cross-encoder
 * 3. Return top 5-7 highest scored chunks
 */
public interface RerankingService {

    /**
     * Rerank documents based on query relevance.
     *
     * @param query The user query
     * @param documents The candidate documents from vector search
     * @param topK Number of top documents to return after reranking
     * @return Reranked documents sorted by relevance score (highest first)
     */
    List<Document> rerank(String query, List<Document> documents, int topK);

    /**
     * Get the reranking strategy name (for logging/observability)
     * For now, its just "HybridCrossEncoder"
     */
    default String getStrategyName() {
        return "HybridCrossEncoder";
    }
}