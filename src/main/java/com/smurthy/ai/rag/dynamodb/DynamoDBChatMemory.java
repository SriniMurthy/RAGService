package com.smurthy.ai.rag.dynamodb;

import com.smurthy.ai.rag.messages.FunctionMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * DynamoDB-backed implementation of Spring AI's ChatMemory interface.
 *
 * Features:
 * - Persistent chat history across application restarts
 * - Scalable storage in DynamoDB Local (can migrate to AWS DynamoDB later)
 * - TTL support for auto-expiring old conversations
 * - Efficient querying by conversation ID with timestamp-based ordering
 * - Thread-safe operations
 *
 * Usage:
 * ```java
 * ChatMemory chatMemory = new DynamoDBChatMemory(dynamoDbClient, "chat_history", 7);
 * chatMemory.add("conv-123", List.of(new UserMessage("Hello!")));
 * List<Message> history = chatMemory.get("conv-123", 10);
 * ```
 */
public class DynamoDBChatMemory implements ChatMemory {

    private static final Logger log = LoggerFactory.getLogger(DynamoDBChatMemory.class);

    private final DynamoDbTable<ChatMessageEntity> table;
    private final int ttlDays;

    /**
     * Constructor for DynamoDB Chat Memory
     *
     * @param enhancedClient DynamoDB Enhanced Client
     * @param tableName Name of the DynamoDB table
     * @param ttlDays Number of days before messages expire (0 = no expiration)
     */
    public DynamoDBChatMemory(DynamoDbEnhancedClient enhancedClient, String tableName, int ttlDays) {
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(ChatMessageEntity.class));
        this.ttlDays = ttlDays;
        log.info("DynamoDBChatMemory initialized with table='{}', ttl={}days", tableName, ttlDays);
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        log.debug("Adding {} messages to conversation '{}'", messages.size(), conversationId);

        long now = Instant.now().toEpochMilli();
        Long expirationTime = ttlDays > 0
                ? Instant.now().plusSeconds(ttlDays * 24L * 60 * 60).getEpochSecond()
                : null;

        for (Message message : messages) {
            ChatMessageEntity entity = new ChatMessageEntity(
                    conversationId,
                    now++,  // Increment timestamp slightly to maintain order
                    UUID.randomUUID().toString(),
                    mapMessageTypeToRole(message.getMessageType()),
                    message.getText(),
                    message.getMetadata(),
                    expirationTime
            );

            table.putItem(entity);
            log.trace("Stored message: {}", entity);
        }

        log.info("Successfully added {} messages to conversation '{}'", messages.size(), conversationId);
    }

    @Override
    public List<Message> get(String conversationId) {
        log.debug("Retrieving all messages from conversation '{}'", conversationId);
        return get(conversationId, Integer.MAX_VALUE);
    }

    /**
     * Get last N messages from a conversation (convenience method, not from interface)
     */
    public List<Message> get(String conversationId, int lastN) {
        log.debug("Retrieving last {} messages from conversation '{}'", lastN, conversationId);

        try {
            // Query all messages for this conversation, sorted by timestamp descending
            QueryConditional queryConditional = QueryConditional
                    .keyEqualTo(Key.builder()
                            .partitionValue(conversationId)
                            .build());

            QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .scanIndexForward(false)  // Sort descending (newest first)
                    .build();

            List<ChatMessageEntity> entities = table.query(queryRequest)
                    .items()
                    .stream()
                    .limit(lastN)  // Limit to last N messages in the stream
                    .collect(Collectors.toList());

            log.debug("Found {} messages in DynamoDB for conversation '{}'", entities.size(), conversationId);

            // Convert entities to Spring AI Messages and reverse to chronological order
            List<Message> messages = entities.stream()
                    .sorted(Comparator.comparing(ChatMessageEntity::getTimestamp))  // Re-sort ascending
                    .map(this::entityToMessage)
                    .collect(Collectors.toList());

            log.info("Retrieved {} messages for conversation '{}'", messages.size(), conversationId);
            return messages;

        } catch (Exception e) {
            log.error("Error retrieving messages for conversation '{}'", conversationId, e);
            return Collections.emptyList();
        }
    }

    @Override
    public void clear(String conversationId) {
        log.info("Clearing all messages for conversation '{}'", conversationId);

        try {
            // Query all messages for this conversation
            QueryConditional queryConditional = QueryConditional
                    .keyEqualTo(Key.builder()
                            .partitionValue(conversationId)
                            .build());

            QueryEnhancedRequest queryRequest = QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .build();

            List<ChatMessageEntity> entities = table.query(queryRequest)
                    .items()
                    .stream()
                    .collect(Collectors.toList());

            log.debug("Found {} messages to delete for conversation '{}'", entities.size(), conversationId);

            // Delete each message
            int deleted = 0;
            for (ChatMessageEntity entity : entities) {
                table.deleteItem(Key.builder()
                        .partitionValue(entity.getConversationId())
                        .sortValue(entity.getTimestamp())
                        .build());
                deleted++;
            }

            log.info("Deleted {} messages for conversation '{}'", deleted, conversationId);

        } catch (Exception e) {
            log.error("Error clearing conversation '{}'", conversationId, e);
        }
    }

    // ========== HELPER METHODS ==========

    /**
     * Convert Spring AI MessageType to role string
     */
    private String mapMessageTypeToRole(MessageType messageType) {
        return switch (messageType) {
            case USER -> "user";
            case ASSISTANT -> "assistant";
            case SYSTEM -> "system";
            case TOOL -> "tool";
        };
    }

    /**
     * Convert DynamoDB entity to Spring AI Message
     */
    private Message entityToMessage(ChatMessageEntity entity) {
        String role = entity.getRole();
        String content = entity.getContent();
        Map<String, Object> metadata = entity.getMetadata() != null
                ? entity.getMetadata()
                : new HashMap<>();

        Message message = switch (role) {
            case "user" -> new UserMessage(content);
            case "assistant" -> new AssistantMessage(content);
            case "system" -> new SystemMessage(content);
            case "tool" -> new FunctionMessage(content, (String) metadata.getOrDefault("name", "unknown"));
            default -> {
                log.warn("Unknown role '{}', defaulting to UserMessage", role);
                yield new UserMessage(content);
            }
        };

        // Add metadata to the message
        if (metadata != null && !metadata.isEmpty()) {
            metadata.forEach((key, value) -> message.getMetadata().put(key, value));
        }

        return message;
    }
}