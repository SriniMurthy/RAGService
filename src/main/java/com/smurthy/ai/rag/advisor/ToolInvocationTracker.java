package com.smurthy.ai.rag.advisor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Thread-safe tracker for tool invocations during AI chat interactions.
 * This allows tests to verify that the agentic AI made decisions to use tools,
 * independent of whether external services succeeded or failed.
 * Future use for audit log purposes, currently used to figure out usage of tools.
 */
public class ToolInvocationTracker {

    private static final ThreadLocal<List<String>> toolCallsInCurrentRequest =
        ThreadLocal.withInitial(ArrayList::new);

    private static final ThreadLocal<Boolean> toolsWereAvailable =
        ThreadLocal.withInitial(() -> false);

    /**
     * Record that a tool was invoked by the AI
     */
    public static void recordToolCall(String toolName) {
        toolCallsInCurrentRequest.get().add(toolName);
    }

    /**
     * Record that tools were made available to the AI (even if not called)
     */
    public static void recordToolsAvailable(List<String> toolNames) {
        if (toolNames != null && !toolNames.isEmpty()) {
            toolsWereAvailable.set(true);
        }
    }

    /**
     * Get all tools that were called in the current request
     */
    public static List<String> getToolCallsInCurrentRequest() {
        return Collections.unmodifiableList(toolCallsInCurrentRequest.get());
    }

    /**
     * Check if any tools were called in the current request
     */
    public static boolean wereToolsCalled() {
        return !toolCallsInCurrentRequest.get().isEmpty();
    }

    /**
     * Check if specific tool was called
     */
    public static boolean wasToolCalled(String toolName) {
        return toolCallsInCurrentRequest.get().contains(toolName);
    }

    /**
     * Check if tools were made available to the AI
     */
    public static boolean wereToolsAvailable() {
        return toolsWereAvailable.get();
    }

    /**
     * Clear tracking data for current request (call at start of each request)
     */
    public static void clear() {
        toolCallsInCurrentRequest.get().clear();
        toolsWereAvailable.set(false);
    }

    /**
     * Get summary of tool usage for debugging
     */
    public static String getSummary() {
        List<String> calls = toolCallsInCurrentRequest.get();
        if (calls.isEmpty()) {
            return toolsWereAvailable.get()
                ? "Tools were available but AI chose not to call them"
                : "No tools were available";
        }
        return "Tools called: " + String.join(", ", calls);
    }
}