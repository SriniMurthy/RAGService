package com.smurthy.ai.rag.test;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test response object for ReACT testing
 * Captures all metrics needed to validate ReACT behavior
 */
public class ReActTestResponse {

    private final String question;
    private final String answer;
    private final int iterations;
    private final int toolCallsTotal;
    private final Set<String> toolsUsed;
    private final Map<Integer, List<String>> toolCallsByIteration;
    private final long executionTimeMs;
    private final List<ReActEvent> allEvents;

    public ReActTestResponse(String question, String answer, int iterations,
                            int toolCallsTotal, Set<String> toolsUsed,
                            Map<Integer, List<String>> toolCallsByIteration,
                            long executionTimeMs, List<ReActEvent> allEvents) {
        this.question = question;
        this.answer = answer;
        this.iterations = iterations;
        this.toolCallsTotal = toolCallsTotal;
        this.toolsUsed = toolsUsed;
        this.toolCallsByIteration = toolCallsByIteration;
        this.executionTimeMs = executionTimeMs;
        this.allEvents = allEvents;
    }

    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public int getIterations() { return iterations; }
    public int getToolCallsTotal() { return toolCallsTotal; }
    public Set<String> getToolsUsed() { return toolsUsed; }
    public Map<Integer, List<String>> getToolCallsByIteration() { return toolCallsByIteration; }
    public long getExecutionTimeMs() { return executionTimeMs; }
    public List<ReActEvent> getAllEvents() { return allEvents; }

    /**
     * Check if same tool was called multiple times with same arguments (waste indicator)
     */
    public boolean hasDuplicateToolCalls() {
        List<String> allCalls = toolCallsByIteration.values().stream()
            .flatMap(List::stream)
            .toList();

        return allCalls.size() != Set.copyOf(allCalls).size();
    }

    /**
     * Get efficiency score: 0-100 where 100 is perfect (no wasted iterations)
     */
    public int getEfficiencyScore() {
        if (iterations == 0) return 0;

        // Penalize for duplicate calls
        if (hasDuplicateToolCalls()) {
            return Math.max(0, 100 - (iterations * 20));
        }

        // Reward for appropriate iteration count
        if (toolCallsTotal <= 1 && iterations == 1) return 100;  // Perfect for simple query
        if (toolCallsTotal > 1 && iterations > 1) return 90;      // Good for complex query
        if (toolCallsTotal > 1 && iterations == 1) return 70;     // Might have missed something

        return 50;  // Default
    }

    public static class ReActEvent {
        private final String type;
        private final String content;

        public ReActEvent(String type, String content) {
            this.type = type;
            this.content = content;
        }

        public String getType() { return type; }
        public String getContent() { return content; }
    }
}
