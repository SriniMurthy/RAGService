package com.smurthy.ai.rag.service;

import com.smurthy.ai.rag.config.EmbeddingConfig;
import com.smurthy.ai.rag.config.RAGConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;


@Service
public class BatchEmbeddingService {

    private final VectorStore vectorStore;
    private final EmbeddingConfig config;
    private final RateLimiter rateLimiter;
    private static final Logger log = LoggerFactory.getLogger(BatchEmbeddingService.class);

    public BatchEmbeddingService(
            VectorStore vectorStore,
            EmbeddingConfig config,
            RateLimiter rateLimiter) {
        this.vectorStore = vectorStore;
        this.config = config;
        this.rateLimiter = rateLimiter;
    }

    /**
     * Add documents with automatic batching and rate limiting
     */
    public void addDocuments(List<Document> documents) {
        List<List<Document>> batches = createTokenAwareBatches(documents);

        log.debug("=== Starting document embedding ===");
        log.debug("Total documents: " + documents.size());
        log.debug("Number of batches: " + batches.size());
        log.debug("Batch size: " + config.batchSize());
        log.debug("Embedding model: " + config.model());

        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < batches.size(); i++) {
            List<Document> batch = batches.get(i);

            try {
                // Check rate limit before processing
                if (!rateLimiter.allowRequest()) {
                    log.debug("Rate limit reached, waiting...");
                    Thread.sleep(rateLimiter.getWaitTimeMs());
                }

                // Process batch with retry
                processBatchWithRetry(batch, i + 1, batches.size());
                successCount += batch.size();

                // Delay between batches
                if (i < batches.size() - 1) {
                    Thread.sleep(config.delayBetweenBatchesMs());
                }

            } catch (Exception e) {
                failCount += batch.size();
                System.err.println("Failed to process batch " + (i + 1) + ": " + e.getMessage());
            }
        }

        log.debug("=== Embedding complete ===");
        log.debug("Success: " + successCount + " documents");
        log.debug("Failed: " + failCount + " documents");
    }

    /**
     * Process a single batch with retry mechanism
     */
    @Retryable(
            maxAttempts = 5,
            backoff = @Backoff(
                    delay = 1000,      // Start with 1 second
                    multiplier = 2,    // Double each time
                    maxDelay = 30000   // Cap at 30 seconds
            )
    )
    public void processBatchWithRetry(List<Document> batch, int batchNum, int totalBatches) {
        log.debug("Processing batch " + batchNum + "/" + totalBatches +
                " (" + batch.size() + " documents)");
        vectorStore.add(batch);
    }

    /**
     * Create fixed-size batches
     */
    private List<List<Document>> createBatches(List<Document> documents) {
        List<List<Document>> batches = new ArrayList<>();
        int batchSize = config.batchSize();

        for (int i = 0; i < documents.size(); i += batchSize) {
            int end = Math.min(i + batchSize, documents.size());
            batches.add(new ArrayList<>(documents.subList(i, end)));
        }

        return batches;
    }

    /**
     * Create token-aware batches (more sophisticated)
     */
    private List<List<Document>> createTokenAwareBatches(List<Document> documents) {
        List<List<Document>> batches = new ArrayList<>();
        List<Document> currentBatch = new ArrayList<>();
        int currentTokenCount = 0;
        int maxTokens = config.maxTokensPerBatch();

        for (Document doc : documents) {
            int estimatedTokens = estimateTokenCount(doc.getFormattedContent());

            if (currentTokenCount + estimatedTokens > maxTokens && !currentBatch.isEmpty()) {
                batches.add(currentBatch);
                currentBatch = new ArrayList<>();
                currentTokenCount = 0;
            }

            currentBatch.add(doc);
            currentTokenCount += estimatedTokens;
        }

        if (!currentBatch.isEmpty()) {
            batches.add(currentBatch);
        }

        return batches;
    }

    /**
     * Estimate token count (1 token ≈ 4 characters for English)
     */
    private int estimateTokenCount(String text) {
        if (text == null) return 0;
        return (int) Math.ceil(text.length() / 4.0);
    }
}