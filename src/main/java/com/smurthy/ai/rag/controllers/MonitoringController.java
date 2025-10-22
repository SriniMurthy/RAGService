package com.smurthy.ai.rag.controllers;

import com.smurthy.ai.rag.observability.RetrievalMetrics;
import com.smurthy.ai.rag.service.RateLimiter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Monitoring endpoint
 */
@RestController
@RequestMapping("/monitoring")
class MonitoringController {

    private final RateLimiter rateLimiter;
    private final RetrievalMetrics retrievalMetrics;

    public MonitoringController(RateLimiter rateLimiter, RetrievalMetrics retrievalMetrics) {
        this.rateLimiter = rateLimiter;
        this.retrievalMetrics = retrievalMetrics;
    }

    @GetMapping("/rateLimit")
    public RateLimitStatus getRateLimitStatus() {
        return new RateLimitStatus(
                rateLimiter.getRemainingRequests(),
                rateLimiter.getWaitTimeMs()
        );
    }

    @GetMapping("/retrieval")
    public RetrievalMetrics.MetricsSummary getRetrievalMetrics() {
        return retrievalMetrics.getMetricsSummary();
    }

    @PostMapping("/retrieval/reset")
    public void resetRetrievalMetrics() {
        retrievalMetrics.resetMetrics();
    }

    record RateLimitStatus(int remainingRequests, long waitTimeMs) {}
}