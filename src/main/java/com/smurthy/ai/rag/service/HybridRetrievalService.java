package com.smurthy.ai.rag.service;

import com.smurthy.ai.rag.observability.RetrievalMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.retrieval.search.DocumentRetriever;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Hybrid Retrieval Service using Reciprocal Rank Fusion (RRF)
 *
 * Combines two retrieval strategies:
 * 1. Dense Retrieval: Vector similarity search (semantic understanding)
 * 2. Sparse Retrieval: BM25 keyword search (exact term matching)
 *
 * Why Hybrid Retrieval?
 * - Vector search: Great for semantic queries ("what's the company's growth strategy?")
 * - BM25 search: Great for exact matches (product IDs, acronyms, specific names)
 * - RRF: Combines both rankings to get best of both worlds
 *
 * Reciprocal Rank Fusion (RRF):
 * score(doc) = Σ(1 / (k + rank_i)) for each ranking i
 * - k = 60 (standard constant from research)
 * - Ranks from both retrieval methods are fused
 * - Documents appearing in both rankings get boosted
 *
 * Performance:
 * - Dense search: ~50-100ms
 * - Sparse search: ~5-15ms
 * - RRF fusion: ~1-5ms
 * - Total: ~60-120ms (acceptable for RAG)
 *
 * Research:
 * "Reciprocal Rank Fusion outperforms the best known automatic evaluation
 * strategies (and their variants) on the TREC 2004 Robust track" - Cormack et al.
 */
@Service
public class HybridRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(HybridRetrievalService.class);

    private final VectorStore vectorStore;
    private final BM25SearchService bm25SearchService;
    private final RerankingService rerankingService;
    private final RetrievalMetrics metrics;

    // RRF constant (k=60 is standard from research)
    private static final int RRF_K = 60;

    public HybridRetrievalService(
            VectorStore vectorStore,
            BM25SearchService bm25SearchService,
            RerankingService rerankingService,
            com.smurthy.ai.rag.observability.RetrievalMetrics metrics) {
        this.vectorStore = vectorStore;
        this.bm25SearchService = bm25SearchService;
        this.rerankingService = rerankingService;
        this.metrics = metrics;
    }

    /**
     * Hybrid retrieval with RRF fusion and reranking
     *
     * Pipeline:
     * 1. Dense retrieval: Get top 20 from vector search (threshold=0.30)
     * 2. Sparse retrieval: Get top 20 from BM25 search
     * 3. RRF fusion: Combine rankings
     * 4. Rerank: Apply cross-encoder to top 20 fused results
     * 5. Return: Top K highest-scored documents
     *
     * What is Reciprocal Rank Fusion (RRF)?
     * In  HybridRetrievalService, we are combining results from two different search methods:
     * 1. A traditional keyword search (likely using BM25/TF-IDF).
     * 2. A modern vector similarity search.
     * RRF is the algorithm used to intelligently merge these two separate ranked lists into a single, more relevant final list.
     * The formula for each document's RRF score is: RRF_Score = (1 / (k + rank_bm25)) + (1 / (k + rank_vector))
     *
     * rank_bm25: The document's position (rank) in the keyword search results.
     * rank_vector: The document's position in the vector search results.
     * k: The RRF constant.
     * The Role of the k Constant
     * The k constant is a tuning parameter that controls how much weight
     * is given to lower-ranked documents. Its primary job is to mitigate the impact of outlier
     * high ranks from a single search method.
     *
     * Without k (or with a small k): The formula would be dominated by the 1 / rank term.
     * A document ranked #1 by either method would get a huge score, potentially drowning out a document that is ranked #5 by both methods.
     * With a large k (like k=60): The formula becomes less sensitive to the exact rank.
     * It gives more credit to documents that simply appear consistently across both lists,
     * even if they aren't at the very top of either. It reduces the penalty for being ranked lower.
     *
     * @param query User query
     * @param topK Final number of documents to return
     * @param similarityThreshold Minimum similarity for vector search
     * @return Hybrid-retrieved and reranked documents
     */
    public HybridRetrievalResult retrieve(String query, int topK, double similarityThreshold) {
        long startTime = System.currentTimeMillis();

        // Step 1: Dense retrieval (vector search)
        long denseStart = System.currentTimeMillis();
        List<Document> denseResults = performDenseRetrieval(query, topK * 2, similarityThreshold);
        long denseTime = System.currentTimeMillis() - denseStart;

        // Step 2: Sparse retrieval (BM25)
        long sparseStart = System.currentTimeMillis();
        List<BM25SearchService.BM25Result> sparseResults = bm25SearchService.search(query, topK * 2);
        long sparseTime = System.currentTimeMillis() - sparseStart;

        // Step 3: RRF Fusion
        long fusionStart = System.currentTimeMillis();
        List<Document> fusedResults = fuseRankings(denseResults, sparseResults, topK * 2);
        long fusionTime = System.currentTimeMillis() - fusionStart;

        // Step 4: Reranking
        long rerankStart = System.currentTimeMillis();
        List<Document> rerankedResults = rerankingService.rerank(query, fusedResults, topK);
        long rerankTime = System.currentTimeMillis() - rerankStart;

        long totalTime = System.currentTimeMillis() - startTime;

        log.info("Hybrid retrieval: dense={}ms ({} docs), sparse={}ms ({} docs), fusion={}ms, rerank={}ms → {} final docs (total: {}ms)",
                denseTime, denseResults.size(),
                sparseTime, sparseResults.size(),
                fusionTime, rerankTime, rerankedResults.size(), totalTime);

        // Record metrics
        double avgSimilarity = rerankedResults.stream()
                .mapToDouble(doc -> {
                    Object sim = doc.getMetadata().get("rerank_score");
                    return sim instanceof Number ? ((Number) sim).doubleValue() : 0.0;
                })
                .average()
                .orElse(0.0);

        metrics.recordRetrieval(new com.smurthy.ai.rag.observability.RetrievalMetrics.RetrievalMetricData(
                query,
                totalTime,
                rerankedResults.size(),
                true, // hybrid retrieval
                avgSimilarity,
                rerankedResults
        ));

        return new HybridRetrievalResult(
                rerankedResults,
                denseResults.size(),
                sparseResults.size(),
                totalTime,
                Map.of(
                        "denseTime", denseTime,
                        "sparseTime", sparseTime,
                        "fusionTime", fusionTime,
                        "rerankTime", rerankTime
                )
        );
    }

    /**
     * Dense retrieval using vector similarity search
     */
    private List<Document> performDenseRetrieval(String query, int topK, double similarityThreshold) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();

        return vectorStore.similaritySearch(request);
    }

    /**
     * Reciprocal Rank Fusion (RRF) to combine dense and sparse rankings
     *
     * Formula: RRF_score(doc) = Σ(1 / (k + rank_i))
     * where rank_i is the position in each ranking (1-indexed)
     */
    private List<Document> fuseRankings(
            List<Document> denseResults,
            List<BM25SearchService.BM25Result> sparseResults,
            int topK) {

        // Map document IDs to their documents (from dense search)
        Map<String, Document> docMap = denseResults.stream()
                .collect(Collectors.toMap(Document::getId, doc -> doc));

        // Map to store RRF scores
        Map<String, Double> rrfScores = new HashMap<>();

        // Add scores from dense ranking (vector search)
        for (int i = 0; i < denseResults.size(); i++) {
            String docId = denseResults.get(i).getId();
            double rrfScore = 1.0 / (RRF_K + (i + 1)); // 1-indexed rank
            rrfScores.merge(docId, rrfScore, Double::sum);
        }

        // Add scores from sparse ranking (BM25)
        for (int i = 0; i < sparseResults.size(); i++) {
            String docId = sparseResults.get(i).documentId();
            double rrfScore = 1.0 / (RRF_K + (i + 1)); // 1-indexed rank

            // If document wasn't in dense results, fetch it from vector store
            if (!docMap.containsKey(docId)) {
                // This document was found by BM25 but not vector search
                // We'll skip it for now (could optionally fetch from DB)
                continue;
            }

            rrfScores.merge(docId, rrfScore, Double::sum);
        }

        // Sort by RRF score and return top K documents
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(entry -> {
                    Document doc = docMap.get(entry.getKey());
                    // Add RRF score to metadata
                    Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
                    metadata.put("rrf_score", entry.getValue());
                    return Document.builder()
                            .id(doc.getId())
                            .text(doc.getText())
                            .metadata(metadata)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Index documents in BM25 index
     * Should be called during document ingestion
     */
    public void indexDocuments(List<Document> documents) {
        try {
            bm25SearchService.indexDocuments(documents);
        } catch (IOException e) {
            log.error("Failed to index documents in BM25", e);
            throw new RuntimeException("BM25 indexing failed", e);
        }
    }

    /**
     * Result object containing retrieved documents and performance metrics
     */
    public record HybridRetrievalResult(
            List<Document> documents,
            int denseResultCount,
            int sparseResultCount,
            long totalTimeMs,
            Map<String, Object> performanceMetrics
    ) {}
}