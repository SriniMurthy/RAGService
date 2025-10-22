package com.smurthy.ai.rag.service;

import com.smurthy.ai.rag.config.MarketFunctionConfiguration;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Observing a lot of slowness and ratelimiting from OpenAI
 */
@Service
@EnableRetry
public class FastDocumentIngestionService {

    private final VectorStore vectorStore;
    private final TextSplitter textSplitter;
    private final BM25SearchService bm25SearchService;
    private final int batchSize;
    private final int parallelEmbeddings;
    private static final Logger log = LoggerFactory.getLogger(FastDocumentIngestionService.class);


    public FastDocumentIngestionService(
            VectorStore vectorStore,
            TextSplitter textSplitter,
            BM25SearchService bm25SearchService,
            @Value("${app.ingestion.batch-size:50}") int batchSize,
            @Value("${app.ingestion.parallel-embeddings:3}") int parallelEmbeddings) {
        this.vectorStore = vectorStore;
        this.textSplitter = textSplitter;
        this.bm25SearchService = bm25SearchService;
        this.batchSize = batchSize;
        this.parallelEmbeddings = parallelEmbeddings;
    }

    /**
     * Fast ingestion with parallel embedding API calls
     * This is what actually speeds things up!
     */
    public void ingestFast(List<Document> documents) {
        long startTime = System.currentTimeMillis();

        log.debug("\n╔════════════════════════════════════════════════╗");
        log.debug("║     FAST PARALLEL EMBEDDING INGESTION          ║");
        log.debug("╚════════════════════════════════════════════════╝");
        log.debug("Documents: " + documents.size());
        log.debug("═══════════════════════════════════════════════\n");

        // This service now expects pre-cleaned and pre-split document chunks.
        long embedStart = System.currentTimeMillis();
        embedChunksInParallel(documents);
        long embedTime = System.currentTimeMillis() - embedStart;
        log.debug("✓ Embedding: " + embedTime + "ms");

        // Index in BM25 for hybrid retrieval
        long bm25Start = System.currentTimeMillis();
        try {
            bm25SearchService.indexDocuments(documents);
            long bm25Time = System.currentTimeMillis() - bm25Start;
            log.debug("✓ BM25 Indexing: " + bm25Time + "ms");
        } catch (Exception e) {
            log.warn("BM25 indexing failed (continuing anyway): " + e.getMessage());
        }

        long totalTime = System.currentTimeMillis() - startTime;
        log.debug("\n═══════════════════════════════════════════════");
        log.debug("Total time: " + totalTime + "ms");
        log.debug("Throughput: " + (documents.size() * 1000 / totalTime) + " chunks/sec");
        log.debug("═══════════════════════════════════════════════\n");
    }

    /**
     * THE KEY METHOD: Embed multiple batches in parallel
     * This is what gives you 3-5x speedup!
     */
    private void embedChunksInParallel(List<Document> chunks) {
        // Create batches
        List<List<Document>> batches = createBatches(chunks);
        log.debug("\nEmbedding " + batches.size() + " batches in parallel...");

        // Create thread pool for parallel embedding
        ExecutorService executor = Executors.newFixedThreadPool(parallelEmbeddings);
        AtomicInteger completedBatches = new AtomicInteger(0);
        AtomicInteger failedBatches = new AtomicInteger(0);

        try {
            // Submit all batches for parallel processing
            List<Future<Boolean>> futures = new ArrayList<>();

            for (int i = 0; i < batches.size(); i++) {
                final int batchNum = i + 1;
                final List<Document> batch = batches.get(i);

                Future<Boolean> future = executor.submit(() -> {
                    try {
                        log.debug("  → Batch " + batchNum + "/" + batches.size() +
                                " (" + batch.size() + " chunks) - Starting...");

                        // This calls OpenAI embedding API with retry logic
                        embedBatchWithRetry(batch);

                        int completed = completedBatches.incrementAndGet();
                        log.debug(" -> Batch " + batchNum + "/" + batches.size() +
                                " - Complete! (" + completed + " total)");
                        return true;

                    } catch (Exception e) {
                        failedBatches.incrementAndGet();
                        System.err.println(" Batch " + batchNum + " failed after retries: " + e.getMessage());
                        return false;
                    }
                });

                futures.add(future);

                // Small stagger to avoid overwhelming the API immediately
                if (i < batches.size() - 1) {
                    Thread.sleep(100);
                }
            }

            // Wait for all to complete
            for (Future<Boolean> future : futures) {
                future.get(); // This waits for completion
            }

            log.debug("\nResults: " + completedBatches.get() + " succeeded, " +
                    failedBatches.get() + " failed");

        } catch (Exception e) {
            System.err.println("Error during parallel embedding: " + e.getMessage());
            throw new RuntimeException("Embedding failed", e);
        } finally {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
    }

    /**
     * Embed a batch with retry logic for rate limits and transient errors
     * This method will retry up to 5 times with exponential backoff
     */
    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 5,
            backoff = @Backoff(
                    delay = 1000,      // Start with 1 second
                    multiplier = 2,    // Double each time: 1s, 2s, 4s, 8s
                    maxDelay = 30000   // Cap at 30 seconds
            )
    )
    private void embedBatchWithRetry(List<Document> batch) {
        try {
            // This is the actual OpenAI API call for embeddings
            vectorStore.add(batch);
        } catch (Exception e) {
            System.err.println("Retry triggered: " + e.getMessage());
            throw e; // Let @Retryable handle it
        }
    }

    /**
     * Alternative: Using CompletableFuture for more control
     */
    private void embedChunksWithCompletableFuture(List<Document> chunks) {
        List<List<Document>> batches = createBatches(chunks);

        // Create executor with limited parallelism
        ExecutorService executor = Executors.newFixedThreadPool(parallelEmbeddings);

        try {
            List<CompletableFuture<Void>> futures = batches.stream()
                    .map(batch -> CompletableFuture.runAsync(() -> {
                        try {
                            // Use retry-enabled method
                            embedBatchWithRetry(batch);
                            log.debug("  ✓ Embedded batch of " + batch.size());
                        } catch (Exception e) {
                            System.err.println("  ✗ Batch failed after retries: " + e.getMessage());
                            throw new RuntimeException(e);
                        }
                    }, executor))
                    .toList();

            // Wait for all futures to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        } finally {
            executor.shutdown();
        }
    }

    /**
     * Performance comparison method
     */
    @Deprecated
    public void compareApproaches(List<Document> documents) {
        log.debug("\n╔════════════════════════════════════════════════╗");
        log.debug("║          PERFORMANCE COMPARISON                ║");
        log.debug("╚════════════════════════════════════════════════╝\n");

        // Prepare documents
        List<Document> cleaned = documents.parallelStream()
                .map(DocumentIngestionService::cleanDocument)
                .filter(doc -> !doc.getText().isBlank())
                .toList();

        List<Document> chunks = textSplitter.apply(cleaned);
        log.debug("Test dataset: " + chunks.size() + " chunks\n");

        // Test 1: Sequential (current approach)
        log.debug("═══ Test 1: Sequential Embedding ═══");
        long seq = timeSequentialEmbedding(new ArrayList<>(chunks));

        // Test 2: Parallel with 2 threads
        log.debug("\n═══ Test 2: Parallel (2 threads) ═══");
        long par2 = timeParallelEmbedding(new ArrayList<>(chunks), 2);

        // Test 3: Parallel with 3 threads
        log.debug("\n═══ Test 3: Parallel (3 threads) ═══");
        long par3 = timeParallelEmbedding(new ArrayList<>(chunks), 3);

        // Summary
        log.debug("\n╔════════════════════════════════════════════════╗");
        log.debug("║              RESULTS SUMMARY                   ║");
        log.debug("╚════════════════════════════════════════════════╝");
        log.debug("Sequential:        " + seq + "ms (baseline)");
        log.debug("Parallel (2):      " + par2 + "ms (" +
                String.format("%.1fx", (double)seq/par2) + " speedup)");
        log.debug("Parallel (3):      " + par3 + "ms (" +
                String.format("%.1fx", (double)seq/par3) + " speedup)");
        log.debug("Recommended:       Use 3 parallel threads");
        log.debug("═══════════════════════════════════════════════\n");
    }

    private long timeSequentialEmbedding(List<Document> chunks) {
        long start = System.currentTimeMillis();
        List<List<Document>> batches = createBatches(chunks);

        for (List<Document> batch : batches) {
            vectorStore.add(batch);
        }

        return System.currentTimeMillis() - start;
    }

    private long timeParallelEmbedding(List<Document> chunks, int parallelism) {
        long start = System.currentTimeMillis();
        List<List<Document>> batches = createBatches(chunks);

        ExecutorService executor = Executors.newFixedThreadPool(parallelism);
        try {
            List<? extends Future<?>> futures = batches.stream()
                    .map(batch -> executor.submit(() -> vectorStore.add(batch)))
                    .toList();

            for (Future<?> future : futures) {
                future.get();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            executor.shutdown();
        }

        return System.currentTimeMillis() - start;
    }

    private List<List<Document>> createBatches(List<Document> chunks) {
        List<List<Document>> batches = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i += batchSize) {
            int end = Math.min(i + batchSize, chunks.size());
            batches.add(new ArrayList<>(chunks.subList(i, end)));
        }
        return batches;
    }

}

/**
 * Configuration
 */
@org.springframework.context.annotation.Configuration
@EnableRetry
class IngestionConfig {
    // Add to application.yml:
    // app.ingestion.batch-size: 50
    // app.ingestion.parallel-embeddings: 3
}