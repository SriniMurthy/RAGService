package com.smurthy.ai.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * ReACT Agent Guardrails Configuration
 *
 * Prevents runaway costs and infinite loops by enforcing:
 * - Maximum iteration limits
 * - Token budget caps
 * - Stuck detection (repeated tool calls)
 * - Cost limits per query
 */
@Configuration
@ConfigurationProperties(prefix = "react")
public class ReActConfig {

    /**
     * Maximum iterations the agent can perform (HARD LIMIT - not overridable by request)
     * Default: 5
     */
    private int maxIterations = 5;

    /**
     * Maximum tokens allowed per query (estimated)
     * GPT-4o: ~$0.0025/1K input tokens, ~$0.01/1K output tokens
     * 10K tokens ≈ $0.10 max cost per query
     * Default: 10000
     */
    private int maxTokensPerQuery = 10000;

    /**
     * Stuck detection: If same tool called N times in a row, stop
     * Default: 2
     */
    private int maxRepeatedToolCalls = 2;

    /**
     * Maximum cost per query in USD (safety net)
     * Default: 0.50
     */
    private double maxCostPerQuery = 0.50;

    /**
     * Enable cost tracking and logging
     * Default: true
     */
    private boolean enableCostTracking = true;

    /**
     * Delay between iterations in milliseconds (rate limiting)
     * Helps avoid hitting OpenAI's TPM (tokens per minute) limits
     * Default: 1000ms (1 second)
     */
    private int iterationDelayMs = 1000;

    // Getters and Setters

    public int getMaxIterations() {
        return maxIterations;
    }

    public void setMaxIterations(int maxIterations) {
        this.maxIterations = maxIterations;
    }

    public int getMaxTokensPerQuery() {
        return maxTokensPerQuery;
    }

    public void setMaxTokensPerQuery(int maxTokensPerQuery) {
        this.maxTokensPerQuery = maxTokensPerQuery;
    }

    public int getMaxRepeatedToolCalls() {
        return maxRepeatedToolCalls;
    }

    public void setMaxRepeatedToolCalls(int maxRepeatedToolCalls) {
        this.maxRepeatedToolCalls = maxRepeatedToolCalls;
    }

    public double getMaxCostPerQuery() {
        return maxCostPerQuery;
    }

    public void setMaxCostPerQuery(double maxCostPerQuery) {
        this.maxCostPerQuery = maxCostPerQuery;
    }

    public boolean isEnableCostTracking() {
        return enableCostTracking;
    }

    public void setEnableCostTracking(boolean enableCostTracking) {
        this.enableCostTracking = enableCostTracking;
    }

    public int getIterationDelayMs() {
        return iterationDelayMs;
    }

    public void setIterationDelayMs(int iterationDelayMs) {
        this.iterationDelayMs = iterationDelayMs;
    }
}