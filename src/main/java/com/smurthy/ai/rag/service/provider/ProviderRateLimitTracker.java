package com.smurthy.ai.rag.service.provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks API usage and rate limits for stock quote providers.
 * Helps the agentic selector make informed decisions about which provider to use.
 */
@Service
public class ProviderRateLimitTracker {

    private static final Logger log = LoggerFactory.getLogger(ProviderRateLimitTracker.class);

    // Provider name -> Usage stats
    private final Map<String, ProviderUsageStats> usageStats = new ConcurrentHashMap<>();

    /**
     * Records a successful API call for a provider.
     */
    public void recordSuccess(String providerName) {
        ProviderUsageStats stats = getOrCreateStats(providerName);
        stats.recordSuccess();
        log.debug("Recorded success for {}: {}", providerName, stats.getSummary());
    }

    /**
     * Records a failed API call for a provider.
     */
    public void recordFailure(String providerName, String reason) {
        ProviderUsageStats stats = getOrCreateStats(providerName);
        stats.recordFailure(reason);
        log.debug("Recorded failure for {}: {}", providerName, stats.getSummary());
    }

    /**
     * Records a rate limit hit for a provider.
     */
    public void recordRateLimit(String providerName) {
        ProviderUsageStats stats = getOrCreateStats(providerName);
        stats.recordRateLimit();
        log.warn("Rate limit hit for {}: {}", providerName, stats.getSummary());
    }

    /**
     * Gets usage statistics for a provider.
     */
    public ProviderUsageStats getStats(String providerName) {
        return getOrCreateStats(providerName);
    }

    /**
     * Gets a summary of all provider statistics.
     */
    public String getAllStatsSummary() {
        StringBuilder sb = new StringBuilder();
        usageStats.forEach((provider, stats) -> {
            sb.append(provider).append(": ").append(stats.getSummary()).append("\n");
        });
        return sb.toString();
    }

    /**
     * Resets statistics for all providers (useful for testing).
     */
    public void resetAll() {
        usageStats.clear();
        log.info("Reset all provider usage statistics");
    }

    private ProviderUsageStats getOrCreateStats(String providerName) {
        return usageStats.computeIfAbsent(providerName, k -> new ProviderUsageStats(providerName));
    }

    /**
     * Usage statistics for a single provider.
     */
    public static class ProviderUsageStats {
        private final String providerName;
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger rateLimitCount = new AtomicInteger(0);
        private LocalDateTime lastSuccess;
        private LocalDateTime lastFailure;
        private LocalDateTime lastRateLimit;
        private String lastFailureReason;

        // Sliding window counters (last minute, last hour)
        private final Map<String, AtomicInteger> minuteWindow = new ConcurrentHashMap<>();
        private final Map<String, AtomicInteger> hourWindow = new ConcurrentHashMap<>();

        public ProviderUsageStats(String providerName) {
            this.providerName = providerName;
        }

        public synchronized void recordSuccess() {
            successCount.incrementAndGet();
            lastSuccess = LocalDateTime.now();
            incrementWindow(minuteWindow, 60);
            incrementWindow(hourWindow, 3600);
        }

        public synchronized void recordFailure(String reason) {
            failureCount.incrementAndGet();
            lastFailure = LocalDateTime.now();
            lastFailureReason = reason;
        }

        public synchronized void recordRateLimit() {
            rateLimitCount.incrementAndGet();
            lastRateLimit = LocalDateTime.now();
        }

        private void incrementWindow(Map<String, AtomicInteger> window, int windowSeconds) {
            String key = getWindowKey(windowSeconds);
            window.computeIfAbsent(key, k -> new AtomicInteger(0)).incrementAndGet();
            // Cleanup old entries
            window.entrySet().removeIf(e -> !e.getKey().equals(key));
        }

        private String getWindowKey(int windowSeconds) {
            long timestamp = System.currentTimeMillis() / 1000;
            long windowBucket = timestamp / windowSeconds;
            return String.valueOf(windowBucket);
        }

        public int getCallsInLastMinute() {
            String key = getWindowKey(60);
            return minuteWindow.getOrDefault(key, new AtomicInteger(0)).get();
        }

        public int getCallsInLastHour() {
            String key = getWindowKey(3600);
            return hourWindow.getOrDefault(key, new AtomicInteger(0)).get();
        }

        public boolean isRateLimitedRecently() {
            if (lastRateLimit == null) return false;
            Duration since = Duration.between(lastRateLimit, LocalDateTime.now());
            return since.toMinutes() < 5; // Consider rate-limited for 5 minutes
        }

        public double getSuccessRate() {
            int total = successCount.get() + failureCount.get();
            return total == 0 ? 0.0 : (double) successCount.get() / total;
        }

        public String getSummary() {
            return String.format(
                "Success: %d, Failures: %d, RateLimits: %d, LastMin: %d, LastHour: %d, SuccessRate: %.2f%%",
                successCount.get(),
                failureCount.get(),
                rateLimitCount.get(),
                getCallsInLastMinute(),
                getCallsInLastHour(),
                getSuccessRate() * 100
            );
        }

        // Getters for agentic decision-making
        public String getProviderName() { return providerName; }
        public int getSuccessCount() { return successCount.get(); }
        public int getFailureCount() { return failureCount.get(); }
        public int getRateLimitCount() { return rateLimitCount.get(); }
        public LocalDateTime getLastSuccess() { return lastSuccess; }
        public LocalDateTime getLastFailure() { return lastFailure; }
        public LocalDateTime getLastRateLimit() { return lastRateLimit; }
        public String getLastFailureReason() { return lastFailureReason; }
    }
}