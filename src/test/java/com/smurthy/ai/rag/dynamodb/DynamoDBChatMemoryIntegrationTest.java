package com.smurthy.ai.rag.dynamodb;

import org.junit.jupiter.api.*;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for DynamoDB Chat Memory with REST endpoints.
 *
 * Prerequisites:
 * - DynamoDB Local running in Docker on port 8000
 * - Application configured with chat.memory.dynamodb.enabled=true
 * - OPENAI_API_KEY environment variable set
 *
 * These tests verify:
 * - Chat memory persists across controller invocations
 * - Conversation history is retrieved correctly
 * - Different conversations are isolated
 * - Chat memory survives application context refresh (simulated)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DynamoDBChatMemoryIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ChatMemory chatMemory;

    private static final String BASE_URL = "/unified/ask";
    private static final String CONVERSATION_ID = "integration-test-" + System.currentTimeMillis();

    @BeforeEach
    void clearConversation() {
        chatMemory.clear(CONVERSATION_ID);
    }

    @Test
    @Order(1)
    @DisplayName("Should persist conversation across multiple API calls")
    void testConversationPersistence() {
        // First call: Introduce name
        String question1 = "My name is Integration Test User";
        ResponseEntity<String> response1 = restTemplate.getForEntity(
                BASE_URL + "?question={q}&conversationId={cid}",
                String.class,
                question1,
                CONVERSATION_ID
        );

        assertThat(response1.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response1.getBody()).containsIgnoringCase("Integration Test User");

        // Second call: Ask for name (should remember)
        String question2 = "What is my name?";
        ResponseEntity<String> response2 = restTemplate.getForEntity(
                BASE_URL + "?question={q}&conversationId={cid}",
                String.class,
                question2,
                CONVERSATION_ID
        );

        assertThat(response2.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response2.getBody()).containsIgnoringCase("Integration Test User");
    }

    @Test
    @Order(2)
    @DisplayName("Should maintain context across multi-turn conversation")
    void testMultiTurnConversation() {
        String conv = "multi-turn-" + System.currentTimeMillis();

        // Turn 1: Set topic
        restTemplate.getForEntity(
                BASE_URL + "?question={q}&conversationId={cid}",
                String.class,
                "Let's talk about Java programming",
                conv
        );

        // Turn 2: Ask follow-up (should understand context)
        ResponseEntity<String> response2 = restTemplate.getForEntity(
                BASE_URL + "?question={q}&conversationId={cid}",
                String.class,
                "What are the main features?",
                conv
        );

        assertThat(response2.getBody()).containsAnyOf("Java", "programming", "features");

        // Turn 3: Another follow-up
        ResponseEntity<String> response3 = restTemplate.getForEntity(
                BASE_URL + "?question={q}&conversationId={cid}",
                String.class,
                "Can you give an example?",
                conv
        );

        assertThat(response3.getBody()).isNotEmpty();

        // Cleanup
        chatMemory.clear(conv);
    }

    @Test
    @Order(3)
    @DisplayName("Should isolate different conversations")
    void testConversationIsolation() {
        String conv1 = "isolation-1-" + System.currentTimeMillis();
        String conv2 = "isolation-2-" + System.currentTimeMillis();

        // Conversation 1: Set name to Alice
        restTemplate.getForEntity(
                BASE_URL + "?question={q}&conversationId={cid}",
                String.class,
                "My name is Alice",
                conv1
        );

        // Conversation 2: Set name to Bob
        restTemplate.getForEntity(
                BASE_URL + "?question={q}&conversationId={cid}",
                String.class,
                "My name is Bob",
                conv2
        );

        // Ask for name in conversation 1
        ResponseEntity<String> response1 = restTemplate.getForEntity(
                BASE_URL + "?question={q}&conversationId={cid}",
                String.class,
                "What is my name?",
                conv1
        );

        // Ask for name in conversation 2
        ResponseEntity<String> response2 = restTemplate.getForEntity(
                BASE_URL + "?question={q}&conversationId={cid}",
                String.class,
                "What is my name?",
                conv2
        );

        // Verify isolation
        assertThat(response1.getBody()).containsIgnoringCase("Alice");
        assertThat(response1.getBody()).doesNotContainIgnoringCase("Bob");

        assertThat(response2.getBody()).containsIgnoringCase("Bob");
        assertThat(response2.getBody()).doesNotContainIgnoringCase("Alice");

        // Cleanup
        chatMemory.clear(conv1);
        chatMemory.clear(conv2);
    }

    @Test
    @Order(4)
    @DisplayName("Should handle long conversations efficiently")
    void testLongConversation() {
        String conv = "long-conv-" + System.currentTimeMillis();

        // Send 10 messages
        for (int i = 1; i <= 10; i++) {
            restTemplate.getForEntity(
                    BASE_URL + "?question={q}&conversationId={cid}",
                    String.class,
                    "This is message number " + i,
                    conv
            );
        }

        // Ask about first message (should be in history)
        ResponseEntity<String> response = restTemplate.getForEntity(
                BASE_URL + "?question={q}&conversationId={cid}",
                String.class,
                "What was my first message?",
                conv
        );

        assertThat(response.getBody()).containsAnyOf("message number 1", "first message");

        // Cleanup
        chatMemory.clear(conv);
    }

    @Test
    @Order(5)
    @DisplayName("Should support conversation with tool calls")
    void testConversationWithTools() {
        String conv = "tool-conv-" + System.currentTimeMillis();

        // First: Ask for stock price (requires tool call)
        ResponseEntity<String> response1 = restTemplate.getForEntity(
                BASE_URL + "?question={q}&conversationId={cid}",
                String.class,
                "What is the stock price of AAPL?",
                conv
        );

        assertThat(response1.getBody()).containsIgnoringCase("AAPL");

        // Second: Ask follow-up about previous answer
        ResponseEntity<String> response2 = restTemplate.getForEntity(
                BASE_URL + "?question={q}&conversationId={cid}",
                String.class,
                "Is that price higher than yesterday?",
                conv
        );

        assertThat(response2.getBody()).isNotEmpty();
        // Response should understand context from previous AAPL question

        // Cleanup
        chatMemory.clear(conv);
    }

    @Test
    @Order(6)
    @DisplayName("Should handle conversation reset")
    void testConversationReset() {
        String conv = "reset-" + System.currentTimeMillis();

        // Set initial context
        restTemplate.getForEntity(
                BASE_URL + "?question={q}&conversationId={cid}",
                String.class,
                "My favorite color is blue",
                conv
        );

        // Verify it remembers
        ResponseEntity<String> response1 = restTemplate.getForEntity(
                BASE_URL + "?question={q}&conversationId={cid}",
                String.class,
                "What is my favorite color?",
                conv
        );
        assertThat(response1.getBody()).containsIgnoringCase("blue");

        // Clear conversation
        chatMemory.clear(conv);

        // Ask again after clearing (should not remember)
        ResponseEntity<String> response2 = restTemplate.getForEntity(
                BASE_URL + "?question={q}&conversationId={cid}",
                String.class,
                "What is my favorite color?",
                conv
        );

        // Should not have context anymore
        assertThat(response2.getBody()).doesNotContainIgnoringCase("blue");
    }

    @Test
    @Order(7)
    @DisplayName("Should persist metadata in conversation")
    void testMetadataPersistence() {
        String conv = "metadata-" + System.currentTimeMillis();

        // Send message (metadata added by framework)
        ResponseEntity<String> response = restTemplate.getForEntity(
                BASE_URL + "?question={q}&conversationId={cid}",
                String.class,
                "Test message for metadata",
                conv
        );

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        // Verify messages were stored (metadata should be preserved)
        var messages = ((DynamoDBChatMemory) chatMemory).get(conv, 10);
        assertThat(messages).isNotEmpty();
        assertThat(messages.get(0).getMetadata()).isNotNull();

        // Cleanup
        chatMemory.clear(conv);
    }

    @Test
    @Order(8)
    @DisplayName("Should handle concurrent conversations")
    void testConcurrentConversations() throws InterruptedException {
        int numConversations = 5;
        String[] convIds = new String[numConversations];

        // Create multiple conversations concurrently
        for (int i = 0; i < numConversations; i++) {
            convIds[i] = "concurrent-" + i + "-" + System.currentTimeMillis();

            final int index = i;
            new Thread(() -> {
                restTemplate.getForEntity(
                        BASE_URL + "?question={q}&conversationId={cid}",
                        String.class,
                        "My ID is " + index,
                        convIds[index]
                );
            }).start();
        }

        // Wait for all threads to complete
        Thread.sleep(5000);

        // Verify each conversation stored its unique ID
        for (int i = 0; i < numConversations; i++) {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    BASE_URL + "?question={q}&conversationId={cid}",
                    String.class,
                    "What is my ID?",
                    convIds[i]
            );

            assertThat(response.getBody()).contains(String.valueOf(i));
            chatMemory.clear(convIds[i]);
        }
    }
}