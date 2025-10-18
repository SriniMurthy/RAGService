package com.smurthy.ai.rag.dynamodb;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;

import java.util.Map;
import java.util.HashMap;

/**
 * DynamoDB entity for storing chat messages.
 *
 * Table Design:
 * - Partition Key: conversationId (allows querying all messages in a conversation)
 * - Sort Key: timestamp (maintains message ordering)
 * - TTL: expirationTime (auto-delete old conversations)
 *
 * Example DynamoDB Item:
 * {
 *   "conversationId": "user123-session456",
 *   "timestamp": 1704672000000,
 *   "messageId": "msg-abc123",
 *   "role": "user",
 *   "content": "What is the weather in SF?",
 *   "metadata": { "model": "gpt-4", "tokens": 15 },
 *   "expirationTime": 1705276800  // TTL in seconds (7 days from now)
 * }
 */
@DynamoDbBean
public class ChatMessageEntity {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private String conversationId;
    private Long timestamp;
    private String messageId;
    private String role;  // "user", "assistant", "system"
    private String content;
    private String metadataJson;  // Serialized JSON string for DynamoDB
    private Long expirationTime;  // TTL in epoch seconds

    // Default constructor (required by DynamoDB Enhanced Client)
    public ChatMessageEntity() {
    }

    public ChatMessageEntity(String conversationId, Long timestamp, String messageId,
                             String role, String content, Map<String, Object> metadata,
                             Long expirationTime) {
        this.conversationId = conversationId;
        this.timestamp = timestamp;
        this.messageId = messageId;
        this.role = role;
        this.content = content;
        setMetadata(metadata);  // Use setter to serialize
        this.expirationTime = expirationTime;
    }

    @DynamoDbPartitionKey
    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    @DynamoDbSortKey
    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    // DynamoDB persisted field (JSON string)
    public String getMetadataJson() {
        return metadataJson;
    }

    public void setMetadataJson(String metadataJson) {
        this.metadataJson = metadataJson;
    }

    // Application-facing methods (not persisted to DynamoDB)
    @DynamoDbIgnore
    public Map<String, Object> getMetadata() {
        if (metadataJson == null || metadataJson.isEmpty()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(metadataJson,
                    new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            // Return empty map if deserialization fails
            return new HashMap<>();
        }
    }

    public void setMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            this.metadataJson = "{}";
            return;
        }
        try {
            this.metadataJson = objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            // Store empty JSON object if serialization fails
            this.metadataJson = "{}";
        }
    }

    public Long getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(Long expirationTime) {
        this.expirationTime = expirationTime;
    }

    @Override
    public String toString() {
        return "ChatMessageEntity{" +
                "conversationId='" + conversationId + '\'' +
                ", timestamp=" + timestamp +
                ", messageId='" + messageId + '\'' +
                ", role='" + role + '\'' +
                ", content='" + (content != null && content.length() > 50 ? content.substring(0, 50) + "..." : content) + '\'' +
                ", expirationTime=" + expirationTime +
                '}';
    }
}