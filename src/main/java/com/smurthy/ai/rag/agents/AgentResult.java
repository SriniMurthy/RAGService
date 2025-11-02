package com.smurthy.ai.rag.agents;

/**
 * Result returned by a specialized agent after execution
 */
public record AgentResult(
        String agentName,
        String result,
        boolean success,
        long executionTimeMs
) {
    public static AgentResult success(String agentName, String result, long executionTimeMs) {
        return new AgentResult(agentName, result, true, executionTimeMs);
    }

    public static AgentResult failure(String agentName, String errorMessage, long executionTimeMs) {
        return new AgentResult(agentName, errorMessage, false, executionTimeMs);
    }
}