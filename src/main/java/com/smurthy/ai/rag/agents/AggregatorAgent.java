package com.smurthy.ai.rag.agents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregator Agent
 *
 * Synthesizes results from multiple specialized agents into a coherent, unified answer.
 * Uses LLM to intelligently combine information from different sources.
 */
@Component
public class AggregatorAgent {

    private static final Logger log = LoggerFactory.getLogger(AggregatorAgent.class);
    private final ChatClient aggregatorChatClient;

    private static final String SYSTEM_PROMPT = """
            You are an expert at synthesizing information from multiple specialized AI agents.

            YOUR TASK:
            - Combine information from different agents (Financial, Research, News, Weather)
            - Create a coherent, well-structured response
            - Maintain accuracy - don't add information not provided by agents
            - Cite sources appropriately (e.g., "According to the FinancialAgent...", "The ResearchAgent found...")

            RULES:
            - If agents provide conflicting information, present both perspectives
            - Be concise but thorough
            - Organize information logically
            - If an agent failed, acknowledge it gracefully without dwelling on it

            Your goal: Provide the user with a unified, helpful answer that integrates all agent results.
            """;

    public AggregatorAgent(ChatClient.Builder chatClientBuilder) {
        this.aggregatorChatClient = chatClientBuilder
                .defaultSystem(SYSTEM_PROMPT)
                .build();

        log.info("AggregatorAgent initialized");
    }

    /**
     * Synthesize results from multiple agents into a unified answer
     *
     * @param originalQuestion The user's original question
     * @param agentResults Results from specialized agents
     * @return Synthesized final answer
     */
    public String synthesize(String originalQuestion, List<AgentResult> agentResults) {
        log.debug("[AggregatorAgent] Synthesizing {} agent results", agentResults.size());

        if (agentResults.isEmpty()) {
            return "I couldn't process your request. No agents were able to provide information.";
        }

        // If only one agent, return its result directly (no need for synthesis)
        if (agentResults.size() == 1) {
            AgentResult single = agentResults.get(0);
            return single.success() ? single.result()
                    : "I encountered an error: " + single.result();
        }

        // Build synthesis prompt
        String agentResultsText = agentResults.stream()
                .map(r -> String.format("""
                        [%s] (success: %s, time: %dms)
                        %s
                        """, r.agentName(), r.success(), r.executionTimeMs(), r.result()))
                .collect(Collectors.joining("\n---\n"));

        String synthesisPrompt = String.format("""
                Original User Question:
                "%s"

                Agent Results:
                %s

                Synthesize these results into a unified, coherent answer for the user.
                """, originalQuestion, agentResultsText);

        try {
            String synthesizedAnswer = aggregatorChatClient.prompt()
                    .user(synthesisPrompt)
                    .call()
                    .content();

            log.info("[AggregatorAgent] Successfully synthesized answer");
            return synthesizedAnswer;

        } catch (Exception e) {
            log.error("[AggregatorAgent] Failed to synthesize results", e);

            // Fallback: return raw agent results
            return "I gathered information from multiple sources:\n\n" +
                    agentResults.stream()
                            .filter(AgentResult::success)
                            .map(r -> r.agentName() + ": " + r.result())
                            .collect(Collectors.joining("\n\n"));
        }
    }
}