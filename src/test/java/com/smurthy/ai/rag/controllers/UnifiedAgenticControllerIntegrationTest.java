package com.smurthy.ai.rag.controllers;

import com.smurthy.ai.rag.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for UnifiedAgenticController.
 *
 * Prerequisites:
 * - OPENAI_API_KEY environment variable set
 * - DynamoDB Local running (for chat memory)
 * - PostgreSQL with PGVector running (for document queries)
 *
 * These tests verify:
 * - Real-time API tool usage (stocks, weather, news)
 * - RAG document query tool usage
 * - Temporal query tool usage
 * - Hybrid queries (combining multiple tools)
 * - Conversation memory persistence
 *
 * NOTE: These tests make real API calls and consume OpenAI tokens!
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class UnifiedAgenticControllerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ========== REAL-TIME API TOOL TESTS ==========

    @Test
    @DisplayName("Should fetch real-time stock quote using getYahooQuote tool")
    void testStockQuoteTool() throws Exception {
        mockMvc.perform(get("/unified/ask")
                        .param("question", "What is the current stock price of AAPL?")
                        .param("conversationId", "test-stock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(containsStringIgnoringCase("AAPL")))
                .andExpect(jsonPath("$.answer").value(anyOf(
                        containsString("$"),
                        containsString("price")
                )))
                .andExpect(jsonPath("$.totalToolsAvailable").value(17))
                .andExpect(jsonPath("$.executionTimeMs").isNumber());
    }

    @Test
    @DisplayName("Should fetch weather using getWeatherByLocation tool")
    void testWeatherTool() throws Exception {
        mockMvc.perform(get("/unified/ask")
                        .param("question", "What is the weather in San Francisco?")
                        .param("conversationId", "test-weather"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(anyOf(
                        containsStringIgnoringCase("San Francisco"),
                        containsStringIgnoringCase("temperature"),
                        containsStringIgnoringCase("weather")
                )))
                .andExpect(jsonPath("$.totalToolsAvailable").value(17));
    }

    @Test
    @DisplayName("Should fetch news using getMarketNews tool")
    void testNewsTool() throws Exception {
        mockMvc.perform(get("/unified/ask")
                        .param("question", "What's the latest tech news?")
                        .param("conversationId", "test-news"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(anyOf(
                        containsStringIgnoringCase("news"),
                        containsStringIgnoringCase("tech"),
                        containsStringIgnoringCase("technology")
                )))
                .andExpect(jsonPath("$.totalToolsAvailable").value(17));
    }

    // ========== RAG DOCUMENT QUERY TESTS ==========

    @Test
    @DisplayName("Should query documents using queryDocuments tool")
    void testRAGDocumentQuery() throws Exception {
        // Note: This test assumes documents are loaded in the vector store
        // If no documents exist, the tool will return "No relevant documents found"

        mockMvc.perform(get("/unified/ask")
                        .param("question", "Who is Srinivas Murthy?")
                        .param("conversationId", "test-rag"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").exists())
                .andExpect(jsonPath("$.totalToolsAvailable").value(17))
                // Answer should either contain biographical info OR mention no documents found
                .andExpect(jsonPath("$.answer").value(anyOf(
                        containsStringIgnoringCase("Srinivas"),
                        containsStringIgnoringCase("no relevant documents")
                )));
    }

    // ========== TEMPORAL QUERY TESTS ==========

    @Test
    @DisplayName("Should query documents by year using queryDocumentsByYear tool")
    void testTemporalYearQuery() throws Exception {
        // Note: This test assumes documents with year metadata exist

        mockMvc.perform(get("/unified/ask")
                        .param("question", "What projects were completed in 2021?")
                        .param("conversationId", "test-temporal"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").exists())
                .andExpect(jsonPath("$.totalToolsAvailable").value(17))
                // Should mention 2021 or no documents found
                .andExpect(jsonPath("$.answer").value(anyOf(
                        containsString("2021"),
                        containsStringIgnoringCase("no documents found")
                )));
    }

    // ========== HYBRID QUERY TESTS ==========

    @Test
    @DisplayName("Should handle hybrid query combining multiple tools")
    void testHybridQuery() throws Exception {
        // This question requires both stock price AND document search
        mockMvc.perform(get("/unified/ask")
                        .param("question", "What is the current AAPL price and do we have any documents about Apple?")
                        .param("conversationId", "test-hybrid"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").exists())
                .andExpect(jsonPath("$.totalToolsAvailable").value(17))
                // Answer should mention AAPL price
                .andExpect(jsonPath("$.answer").value(anyOf(
                        containsStringIgnoringCase("AAPL"),
                        containsStringIgnoringCase("Apple")
                )));
    }

    // ========== CONVERSATION MEMORY TESTS ==========

    @Test
    @DisplayName("Should maintain conversation context across multiple requests")
    void testConversationMemory() throws Exception {
        String conversationId = "test-memory-" + System.currentTimeMillis();

        // First message: Introduce yourself
        mockMvc.perform(get("/unified/ask")
                        .param("question", "My name is Alice")
                        .param("conversationId", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(containsStringIgnoringCase("Alice")));

        // Second message: Ask for name (should remember from previous message)
        mockMvc.perform(get("/unified/ask")
                        .param("question", "What is my name?")
                        .param("conversationId", conversationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(containsStringIgnoringCase("Alice")));
    }

    @Test
    @DisplayName("Should isolate different conversations")
    void testConversationIsolation() throws Exception {
        String conv1 = "test-conv1-" + System.currentTimeMillis();
        String conv2 = "test-conv2-" + System.currentTimeMillis();

        // Conversation 1: Name is Alice
        mockMvc.perform(get("/unified/ask")
                        .param("question", "My name is Alice")
                        .param("conversationId", conv1))
                .andExpect(status().isOk());

        // Conversation 2: Name is Bob
        mockMvc.perform(get("/unified/ask")
                        .param("question", "My name is Bob")
                        .param("conversationId", conv2))
                .andExpect(status().isOk());

        // Ask for name in conversation 1 - should say Alice
        mockMvc.perform(get("/unified/ask")
                        .param("question", "What is my name?")
                        .param("conversationId", conv1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(containsStringIgnoringCase("Alice")));

        // Ask for name in conversation 2 - should say Bob
        mockMvc.perform(get("/unified/ask")
                        .param("question", "What is my name?")
                        .param("conversationId", conv2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(containsStringIgnoringCase("Bob")));
    }

    // ========== SIMPLIFIED ENDPOINT TESTS ==========

    @Test
    @DisplayName("Should work with simplified /q endpoint")
    void testSimplifiedEndpoint() throws Exception {
        mockMvc.perform(get("/unified/q")
                        .param("query", "What is 2+2?"))
                .andExpect(status().isOk())
                .andExpect(content().string(anyOf(
                        containsString("4"),
                        containsString("four")
                )));
    }

    // ========== EDGE CASES ==========

    @Test
    @DisplayName("Should handle empty question gracefully")
    void testEmptyQuestion() throws Exception {
        mockMvc.perform(get("/unified/ask")
                        .param("question", "")
                        .param("conversationId", "test-empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").exists());
    }

    @Test
    @DisplayName("Should handle very long question")
    void testLongQuestion() throws Exception {
        String longQuestion = "Tell me about ".repeat(100) + "artificial intelligence";

        mockMvc.perform(get("/unified/ask")
                        .param("question", longQuestion)
                        .param("conversationId", "test-long"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").exists());
    }

    @Test
    @DisplayName("Should handle special characters in question")
    void testSpecialCharacters() throws Exception {
        mockMvc.perform(get("/unified/ask")
                        .param("question", "What's the weather in O'Reilly's hometown?")
                        .param("conversationId", "test-special"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").exists());
    }

    // ========== PERFORMANCE TESTS ==========

    @Test
    @DisplayName("Should respond within reasonable time")
    void testResponseTime() throws Exception {
        long startTime = System.currentTimeMillis();

        mockMvc.perform(get("/unified/ask")
                        .param("question", "What is 1+1?")
                        .param("conversationId", "test-performance"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executionTimeMs").isNumber())
                .andExpect(jsonPath("$.executionTimeMs").value(lessThan(30000)));  // < 30 seconds

        long duration = System.currentTimeMillis() - startTime;
        System.out.println("Response time: " + duration + "ms");
    }

    // ========== TOOL SELECTION VERIFICATION ==========

    @Test
    @DisplayName("Should select appropriate tools based on question type")
    void testToolSelection() throws Exception {
        // Stock question should NOT call weather or news tools
        mockMvc.perform(get("/unified/ask")
                        .param("question", "Stock price of GOOGL")
                        .param("conversationId", "test-tool-selection"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(anyOf(
                        containsStringIgnoringCase("GOOGL"),
                        containsStringIgnoringCase("Google"),
                        containsString("$")
                )))
                // Should NOT mention weather or news
                .andExpect(jsonPath("$.answer").value(not(containsStringIgnoringCase("weather"))))
                .andExpect(jsonPath("$.answer").value(not(containsStringIgnoringCase("forecast"))));
    }
}