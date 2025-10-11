package com.smurthy.ai.rag.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Rate Limiter to prevent hitting API limits
 */
@Service
public class RateLimiter {

    private static final int MAX_REQUESTS_PER_MINUTE = 50;
    private final AtomicInteger requestsThisMinute = new AtomicInteger(0);
    private long lastResetTime = System.currentTimeMillis();

    public synchronized boolean allowRequest() {
        resetIfNeeded();

        if (requestsThisMinute.get() < MAX_REQUESTS_PER_MINUTE) {
            requestsThisMinute.incrementAndGet();
            return true;
        }

        return false;
    }

    public int getRemainingRequests() {
        resetIfNeeded();
        return Math.max(0, MAX_REQUESTS_PER_MINUTE - requestsThisMinute.get());
    }

    public long getWaitTimeMs() {
        long now = System.currentTimeMillis();
        long timeUntilReset = 60000 - (now - lastResetTime);
        return Math.max(0, timeUntilReset);
    }

    private void resetIfNeeded() {
        long now = System.currentTimeMillis();
        if (now - lastResetTime > 60000) {
            requestsThisMinute.set(0);
            lastResetTime = now;
        }
    }
}