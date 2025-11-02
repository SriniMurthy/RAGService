package com.smurthy.ai.rag.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper class for testing ReACT behavior
 * Captures SSE events and analyzes ReACT patterns
 */
@Component
public class ReActTestHelper {

    private static final Logger log = LoggerFactory.getLogger(ReActTestHelper.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // Pattern to detect tool calls in observations
    private static final Pattern TOOL_CALL_PATTERN = Pattern.compile(
        "\\b(getRealTimeQuote|getYahooQuote|queryDocuments|getWeatherByLocation|getMarketNews)\\s*\\(([^)]+)\\)",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * Test the /askStream endpoint and capture all ReACT events
     */
    public ReActTestResponse testAskStream(String question, int port) {
        return testAskStream(question, port, 2);
    }

    public ReActTestResponse testAskStream(String question, int port, int maxIterations) {
        long startTime = System.currentTimeMillis();
        List<ReActTestResponse.ReActEvent> events = new ArrayList<>();

        try {
            String encodedQuestion = URLEncoder.encode(question, StandardCharsets.UTF_8);
            String url = String.format("http://localhost:%d/RAG/askStream?q=%s", port, encodedQuestion);

            log.info("Testing ReACT with question: {}", question);

            WebClient webClient = WebClient.create();

            Flux<String> eventStream = webClient.get()
                .uri(url)
                .retrieve()
                .bodyToFlux(String.class)
                .timeout(Duration.ofSeconds(30))
                .doOnError(error -> log.error("Stream error: {}", error.getMessage()));

            // Collect all events
            eventStream.toStream().forEach(line -> {
                if (line.startsWith("data:")) {
                    String jsonData = line.substring(5).trim();
                    try {
                        JsonNode node = objectMapper.readTree(jsonData);
                        String type = node.get("type").asText();
                        String content = node.get("content").asText();
                        events.add(new ReActTestResponse.ReActEvent(type, content));
                        log.debug("Event: {} - {}", type, content.substring(0, Math.min(50, content.length())));
                    } catch (Exception e) {
                        log.warn("Failed to parse event: {}", jsonData);
                    }
                }
            });

        } catch (Exception e) {
            log.error("Test failed", e);
            throw new RuntimeException("ReACT test failed: " + e.getMessage(), e);
        }

        long executionTime = System.currentTimeMillis() - startTime;

        // Analyze events
        int iterations = countIterations(events);
        int toolCallsTotal = countToolCalls(events);
        Set<String> toolsUsed = extractToolsUsed(events);
        Map<Integer, List<String>> toolCallsByIteration = groupToolCallsByIteration(events);
        String answer = extractAnswer(events);

        log.info("ReACT Test Complete:");
        log.info("  Iterations: {}", iterations);
        log.info("  Total tool calls: {}", toolCallsTotal);
        log.info("  Tools used: {}", toolsUsed);
        log.info("  Execution time: {}ms", executionTime);

        return new ReActTestResponse(
            question, answer, iterations, toolCallsTotal,
            toolsUsed, toolCallsByIteration, executionTime, events
        );
    }

    private int countIterations(List<ReActTestResponse.ReActEvent> events) {
        return (int) events.stream()
            .filter(e -> "THOUGHT".equals(e.getType()))
            .count();
    }

    private int countToolCalls(List<ReActTestResponse.ReActEvent> events) {
        return events.stream()
            .filter(e -> "OBSERVATION".equals(e.getType()))
            .mapToInt(this::countToolCallsInObservation)
            .sum();
    }

    private int countToolCallsInObservation(ReActTestResponse.ReActEvent event) {
        String content = event.getContent();
        Matcher matcher = TOOL_CALL_PATTERN.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return Math.max(1, count);  // At least 1 if it's an observation
    }

    private Set<String> extractToolsUsed(List<ReActTestResponse.ReActEvent> events) {
        Set<String> tools = new HashSet<>();

        events.stream()
            .filter(e -> "OBSERVATION".equals(e.getType()))
            .forEach(event -> {
                String content = event.getContent();
                Matcher matcher = TOOL_CALL_PATTERN.matcher(content);
                while (matcher.find()) {
                    tools.add(matcher.group(1));
                }
            });

        return tools;
    }

    private Map<Integer, List<String>> groupToolCallsByIteration(List<ReActTestResponse.ReActEvent> events) {
        Map<Integer, List<String>> result = new ConcurrentHashMap<>();
        int currentIteration = 0;

        for (ReActTestResponse.ReActEvent event : events) {
            if ("THOUGHT".equals(event.getType())) {
                currentIteration++;
            } else if ("OBSERVATION".equals(event.getType())) {
                List<String> toolCalls = result.computeIfAbsent(currentIteration, k -> new ArrayList<>());

                String content = event.getContent();
                Matcher matcher = TOOL_CALL_PATTERN.matcher(content);
                while (matcher.find()) {
                    String toolCall = matcher.group(1) + "(" + matcher.group(2) + ")";
                    toolCalls.add(toolCall);
                }
            }
        }

        return result;
    }

    private String extractAnswer(List<ReActTestResponse.ReActEvent> events) {
        return events.stream()
            .filter(e -> "ANSWER".equals(e.getType()))
            .map(ReActTestResponse.ReActEvent::getContent)
            .findFirst()
            .orElse("");
    }
}
