package com.smurthy.ai.rag.agents;

/**
 * Represents the routing decision for a user query.
 * Determines which specialized agents should be invoked.
 */
public record QueryIntent(
        boolean needsFinancial,
        boolean needsResearch,
        boolean needsNews,
        boolean needsWeather,
        String reasoning
) {
    /**
     * Check if any agent is needed
     */
    public boolean needsAnyAgent() {
        return needsFinancial || needsResearch || needsNews || needsWeather;
    }

    /**
     * Get a summary of which agents are needed
     */
    public String getAgentSummary() {
        var agents = new StringBuilder();
        if (needsFinancial) agents.append("Financial, ");
        if (needsResearch) agents.append("Research, ");
        if (needsNews) agents.append("News, ");
        if (needsWeather) agents.append("Weather, ");

        if (agents.isEmpty()) {
            return "None";
        }

        // Remove trailing comma and space
        return agents.substring(0, agents.length() - 2);
    }
}