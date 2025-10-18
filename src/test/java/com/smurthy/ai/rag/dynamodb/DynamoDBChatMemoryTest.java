package com.smurthy.ai.rag.dynamodb;

import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DynamoDBChatMemory.
 *
 * Prerequisites:
 * - DynamoDB Local running in Docker on port 8000
 * - Run: docker-compose up -d dynamodb-local
 *
 * These tests verify:
 * - Message storage and retrieval
 * - Conversation ordering (chronological)
 * - Last N message windowing
 * - Conversation clearing
 * - Metadata preservation
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DynamoDBChatMemoryTest {

    private static DynamoDbClient dynamoDbClient;
    private static DynamoDbEnhancedClient enhancedClient;
    private static DynamoDBChatMemory chatMemory;

    private static final String TEST_TABLE = "test_chat_history";
    private static final String CONVERSATION_ID = "test-conversation-123";

    @BeforeAll
    static void setupDynamoDB() {
        // Connect to DynamoDB Local
        dynamoDbClient = DynamoDbClient.builder()
                .region(Region.US_WEST_2)
                .endpointOverride(URI.create("http://localhost:8000"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create("fakeKey", "fakeSecret")))
                .build();

        enhancedClient = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();

        // Create chat memory with 0 TTL (no expiration for tests)
        chatMemory = new DynamoDBChatMemory(enhancedClient, TEST_TABLE, 0);

        // Create table
        createTable();
    }

    @AfterAll
    static void tearDown() {
        deleteTable();
        dynamoDbClient.close();
    }

    @BeforeEach
    void clearConversation() {
        chatMemory.clear(CONVERSATION_ID);
    }

    @Test
    @Order(1)
    @DisplayName("Should store and retrieve single message")
    void testSingleMessage() {
        // Given
        UserMessage userMessage = new UserMessage("Hello, AI!");

        // When
        chatMemory.add(CONVERSATION_ID, List.of(userMessage));

        // Then
        List<Message> retrieved = chatMemory.get(CONVERSATION_ID, 10);
        assertThat(retrieved).hasSize(1);
        assertThat(retrieved.get(0).getText()).isEqualTo("Hello, AI!");
        assertThat(retrieved.get(0)).isInstanceOf(UserMessage.class);
    }

    @Test
    @Order(2)
    @DisplayName("Should store and retrieve conversation in chronological order")
    void testConversationOrdering() {
        // Given
        List<Message> conversation = List.of(
                new UserMessage("What is the weather?"),
                new AssistantMessage("It's sunny today!"),
                new UserMessage("Thanks!")
        );

        // When
        chatMemory.add(CONVERSATION_ID, conversation);

        // Then
        List<Message> retrieved = chatMemory.get(CONVERSATION_ID, 10);
        assertThat(retrieved).hasSize(3);
        assertThat(retrieved.get(0).getText()).isEqualTo("What is the weather?");
        assertThat(retrieved.get(1).getText()).isEqualTo("It's sunny today!");
        assertThat(retrieved.get(2).getText()).isEqualTo("Thanks!");
    }

    @Test
    @Order(3)
    @DisplayName("Should respect lastN parameter (message window)")
    void testMessageWindow() {
        // Given - 5 messages
        chatMemory.add(CONVERSATION_ID, List.of(
                new UserMessage("Message 1"),
                new AssistantMessage("Response 1"),
                new UserMessage("Message 2"),
                new AssistantMessage("Response 2"),
                new UserMessage("Message 3")
        ));

        // When - Request last 3 messages
        List<Message> retrieved = chatMemory.get(CONVERSATION_ID, 3);

        // Then - Should get most recent 3
        assertThat(retrieved).hasSize(3);
        assertThat(retrieved.get(0).getText()).isEqualTo("Message 2");
        assertThat(retrieved.get(1).getText()).isEqualTo("Response 2");
        assertThat(retrieved.get(2).getText()).isEqualTo("Message 3");
    }

    @Test
    @Order(4)
    @DisplayName("Should preserve message metadata")
    void testMetadataPreservation() {
        // Given
        Map<String, Object> metadata = Map.of(
                "model", "gpt-4o",
                "tokens", 150,
                "temperature", 0.7
        );
        UserMessage messageWithMetadata = new UserMessage("Test message");
        messageWithMetadata.getMetadata().putAll(metadata);

        // When
        chatMemory.add(CONVERSATION_ID, List.of(messageWithMetadata));

        // Then
        List<Message> retrieved = chatMemory.get(CONVERSATION_ID, 10);
        assertThat(retrieved).hasSize(1);
        assertThat(retrieved.get(0).getMetadata()).containsEntry("model", "gpt-4o");
        assertThat(retrieved.get(0).getMetadata()).containsEntry("tokens", 150);
    }

    @Test
    @Order(5)
    @DisplayName("Should append messages to existing conversation")
    void testAppendingMessages() {
        // Given - Initial conversation
        chatMemory.add(CONVERSATION_ID, List.of(
                new UserMessage("First message")
        ));

        // When - Append more messages
        chatMemory.add(CONVERSATION_ID, List.of(
                new AssistantMessage("First response"),
                new UserMessage("Second message")
        ));

        // Then - Should have all 3 messages
        List<Message> retrieved = chatMemory.get(CONVERSATION_ID, 10);
        assertThat(retrieved).hasSize(3);
        assertThat(retrieved.get(0).getText()).isEqualTo("First message");
        assertThat(retrieved.get(1).getText()).isEqualTo("First response");
        assertThat(retrieved.get(2).getText()).isEqualTo("Second message");
    }

    @Test
    @Order(6)
    @DisplayName("Should handle multiple concurrent conversations")
    void testMultipleConversations() {
        String conv1 = "user1-session1";
        String conv2 = "user2-session2";

        // Given
        chatMemory.add(conv1, List.of(new UserMessage("User 1 message")));
        chatMemory.add(conv2, List.of(new UserMessage("User 2 message")));

        // Then
        List<Message> conv1Messages = chatMemory.get(conv1, 10);
        List<Message> conv2Messages = chatMemory.get(conv2, 10);

        assertThat(conv1Messages).hasSize(1);
        assertThat(conv2Messages).hasSize(1);
        assertThat(conv1Messages.get(0).getText()).isEqualTo("User 1 message");
        assertThat(conv2Messages.get(0).getText()).isEqualTo("User 2 message");

        // Cleanup
        chatMemory.clear(conv1);
        chatMemory.clear(conv2);
    }

    @Test
    @Order(7)
    @DisplayName("Should clear conversation")
    void testClearConversation() {
        // Given
        chatMemory.add(CONVERSATION_ID, List.of(
                new UserMessage("Message 1"),
                new AssistantMessage("Response 1"),
                new UserMessage("Message 2")
        ));

        // When
        chatMemory.clear(CONVERSATION_ID);

        // Then
        List<Message> retrieved = chatMemory.get(CONVERSATION_ID, 10);
        assertThat(retrieved).isEmpty();
    }

    @Test
    @Order(8)
    @DisplayName("Should return empty list for non-existent conversation")
    void testNonExistentConversation() {
        // When
        List<Message> retrieved = chatMemory.get("non-existent-conversation", 10);

        // Then
        assertThat(retrieved).isEmpty();
    }

    @Test
    @Order(9)
    @DisplayName("Should handle large conversations efficiently")
    void testLargeConversation() {
        // Given - 50 messages
        for (int i = 0; i < 50; i++) {
            chatMemory.add(CONVERSATION_ID, List.of(
                    new UserMessage("Message " + i),
                    new AssistantMessage("Response " + i)
            ));
        }

        // When - Request last 20 messages
        long startTime = System.currentTimeMillis();
        List<Message> retrieved = chatMemory.get(CONVERSATION_ID, 20);
        long duration = System.currentTimeMillis() - startTime;

        // Then
        assertThat(retrieved).hasSize(20);
        assertThat(duration).isLessThan(1000); // Should be fast (< 1 second)

        // Verify we got the most recent messages
        assertThat(retrieved.get(0).getText()).contains("Message 4"); // Last 20 starts from message 40
        assertThat(retrieved.get(19).getText()).contains("Response 49");
    }

    // ========== HELPER METHODS ==========

    private static void createTable() {
        try {
            var table = enhancedClient.table(TEST_TABLE,
                    software.amazon.awssdk.enhanced.dynamodb.TableSchema.fromBean(ChatMessageEntity.class));
            table.createTable();

            // Wait for table to be active
            dynamoDbClient.waiter().waitUntilTableExists(
                    software.amazon.awssdk.services.dynamodb.model.DescribeTableRequest.builder()
                            .tableName(TEST_TABLE)
                            .build()
            );
        } catch (Exception e) {
            // Table might already exist, that's ok
        }
    }

    private static void deleteTable() {
        try {
            dynamoDbClient.deleteTable(
                    software.amazon.awssdk.services.dynamodb.model.DeleteTableRequest.builder()
                            .tableName(TEST_TABLE)
                            .build()
            );
        } catch (Exception e) {
            // Ignore if table doesn't exist
        }
    }
}