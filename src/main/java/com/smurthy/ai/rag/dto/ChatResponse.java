package com.smurthy.ai.rag.dto;

import java.util.List;

/**
 * Response DTO that includes both the chat response and metadata about tool usage
 */
public record ChatResponse(
    String answer,
    boolean toolsWereAvailable,
    boolean toolsWereCalled,
    List<String> toolsAvailable,
    List<String> toolsCalled,
    String summary
) {
    public static ChatResponse from(String answer, boolean toolsAvailable, boolean toolsCalled,
                                   List<String> availableTools, List<String> calledTools, String summary) {
        return new ChatResponse(answer, toolsAvailable, toolsCalled, availableTools, calledTools, summary);
    }
}