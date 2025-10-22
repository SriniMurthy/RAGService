package com.smurthy.ai.rag.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Retrieval Observability Metrics
 *
 * Tracks key metrics for RAG retrieval quality and performance:
 * - Retrieval latency (dense, sparse, fusion, reranking)
 * - Similarity score distributions
 * - Document usage patterns
 * - Query patterns and performance
 *
 * Metrics can be exposed via /metrics endpoint or exported to:
 * - Prometheus
 * - CloudWatch
 * - Datadog
 * - Custom monitoring systems
 *
 * Usage:
 * 1. Call recordRetrieval() after each retrieval operation
 * 2. Call getMetricsSummary() to get current statistics
 * 3. Call resetMetrics() to clear metrics (e.g., hourly)
 */
@Component
public class RetrievalMetrics {

    private static final Logger log = LoggerFactory.getLogger(RetrievalMetrics.class);

    // Counters
    private final LongAdder totalRetrievals = new LongAdder();
    private final LongAdder totalDocumentsRetrieved = new LongAdder();
    private final LongAdder totalHybridRetrievals = new LongAdder();
    private final LongAdder totalVectorOnlyRetrievals = new LongAdder();

    // Timing metrics (in milliseconds)
    private final LongAdder totalLatencyMs = new LongAdder();
    private final AtomicLong maxLatencyMs = new AtomicLong(0);
    private final AtomicLong minLatencyMs = new AtomicLong(Long.MAX_VALUE);

    // Similarity score tracking
    private final Map<String, Double> querySimilarityScores = new ConcurrentHashMap<>();

    // Document usage tracking (which documents get retrieved most?)
    private final Map<String, LongAdder> documentRetrievalCounts = new ConcurrentHashMap<>();

    /**
     * Record a retrieval operation
     */
    public void recordRetrieval(RetrievalMetricData data) {
        totalRetrievals.increment();
        totalDocumentsRetrieved.add(data.documentsReturned);

        if (data.isHybrid) {
            totalHybridRetrievals.increment();
        } else {
            totalVectorOnlyRetrievals.increment();
        }

        // Track latency
        totalLatencyMs.add(data.latencyMs);
        updateMaxLatency(data.latencyMs);
        updateMinLatency(data.latencyMs);

        // Track similarity scores
        if (data.query != null && data.averageSimilarityScore > 0) {
            querySimilarityScores.put(data.query, data.averageSimilarityScore);
        }

        // Track document usage
        if (data.retrievedDocuments != null) {
            for (Document doc : data.retrievedDocuments) {
                documentRetrievalCounts
                        .computeIfAbsent(doc.getId(), k -> new LongAdder())
                        .increment();
            }
        }

        // Log if retrieval was slow
        if (data.latencyMs > 500) {
            log.warn("Slow retrieval detected: {}ms for query: {}", data.latencyMs, data.query);
        }

        // Log if similarity scores are low
        if (data.averageSimilarityScore > 0 && data.averageSimilarityScore < 0.5) {
            log.warn("Low similarity scores (avg: {}) for query: {}", data.averageSimilarityScore, data.query);
        }
    }

    /**
     * Get current metrics summary
     */
    public MetricsSummary getMetricsSummary() {
        long retrievals = totalRetrievals.sum();
        long latency = totalLatencyMs.sum();

        return new MetricsSummary(
                retrievals,
                totalDocumentsRetrieved.sum(),
                totalHybridRetrievals.sum(),
                totalVectorOnlyRetrievals.sum(),
                retrievals > 0 ? latency / retrievals : 0,
                maxLatencyMs.get() == 0 ? 0 : maxLatencyMs.get(),
                minLatencyMs.get() == Long.MAX_VALUE ? 0 : minLatencyMs.get(),
                retrievals > 0 ? (double) totalDocumentsRetrieved.sum() / retrievals : 0,
                calculateAverageSimilarityScore(),
                getMostRetrievedDocuments(5)
        );
    }

    /**
     * Reset all metrics (useful for hourly/daily resets)
     */
    public void resetMetrics() {
        totalRetrievals.reset();
        totalDocumentsRetrieved.reset();
        totalHybridRetrievals.reset();
        totalVectorOnlyRetrievals.reset();
        totalLatencyMs.reset();
        maxLatencyMs.set(0);
        minLatencyMs.set(Long.MAX_VALUE);
        querySimilarityScores.clear();
        documentRetrievalCounts.clear();
        log.info("Retrieval metrics reset");
    }

    /**
     * Log current metrics summary
     */
    public void logMetricsSummary() {
        MetricsSummary summary = getMetricsSummary();
        log.info("""

                ╔═══════════════════════════════════════════════════════════════╗
                ║              RETRIEVAL METRICS SUMMARY                        ║
                ╠═══════════════════════════════════════════════════════════════╣
                ║ Total Retrievals:       {}
                ║ Documents Retrieved:    {}
                ║ Hybrid Retrievals:      {} ({} %)
                ║ Vector Only:            {}
                ║ Avg Latency:            {} ms
                ║ Max Latency:            {} ms
                ║ Min Latency:            {} ms
                ║ Avg Docs/Query:         {}
                ║ Avg Similarity Score:   {}
                ╚═══════════════════════════════════════════════════════════════╝
                """,
                summary.totalRetrievals,
                summary.totalDocumentsRetrieved,
                summary.hybridRetrievals,
                summary.totalRetrievals > 0 ? (summary.hybridRetrievals * 100 / summary.totalRetrievals) : 0,
                summary.vectorOnlyRetrievals,
                summary.averageLatencyMs,
                summary.maxLatencyMs,
                summary.minLatencyMs,
                String.format("%.2f", summary.averageDocsPerQuery),
                String.format("%.3f", summary.averageSimilarityScore)
        );
    }

    // Helper methods

    private void updateMaxLatency(long latency) {
        maxLatencyMs.updateAndGet(current -> Math.max(current, latency));
    }

    private void updateMinLatency(long latency) {
        minLatencyMs.updateAndGet(current -> Math.min(current, latency));
    }

    private double calculateAverageSimilarityScore() {
        if (querySimilarityScores.isEmpty()) {
            return 0.0;
        }
        DoubleSummaryStatistics stats = querySimilarityScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();
        return stats.getAverage();
    }

    private List<String> getMostRetrievedDocuments(int limit) {
        return documentRetrievalCounts.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().sum(), e1.getValue().sum()))
                .limit(limit)
                .map(e -> e.getKey() + " (" + e.getValue().sum() + " times)")
                .toList();
    }

    // Data classes

    public record RetrievalMetricData(
            String query,
            long latencyMs,
            int documentsReturned,
            boolean isHybrid,
            double averageSimilarityScore,
            List<Document> retrievedDocuments
    ) {}

    public record MetricsSummary(
            long totalRetrievals,
            long totalDocumentsRetrieved,
            long hybridRetrievals,
            long vectorOnlyRetrievals,
            long averageLatencyMs,
            long maxLatencyMs,
            long minLatencyMs,
            double averageDocsPerQuery,
            double averageSimilarityScore,
            List<String> topRetrievedDocuments
    ) {}
}