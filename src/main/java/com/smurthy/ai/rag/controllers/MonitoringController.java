package com.smurthy.ai.rag.controllers;

import com.smurthy.ai.rag.service.RateLimiter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Monitoring endpoint
 */
@RestController
@RequestMapping("/monitoring")
class MonitoringController {

    private final RateLimiter rateLimiter;

    public MonitoringController(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @GetMapping("/rate-limit")
    public RateLimitStatus getRateLimitStatus() {
        return new RateLimitStatus(
                rateLimiter.getRemainingRequests(),
                rateLimiter.getWaitTimeMs()
        );
    }

    record RateLimitStatus(int remainingRequests, long waitTimeMs) {}
}