package com.smurthy.ai.rag.config;

import com.smurthy.ai.rag.dynamodb.ChatMessageEntity;
import com.smurthy.ai.rag.dynamodb.DynamoDBChatMemory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import java.net.URI;

/**
 * DynamoDB Configuration for Chat History Persistence.
 *
 * This configuration:
 * 1. Connects to DynamoDB Local (Docker) for development
 * 2. Creates the chat_history table if it doesn't exist
 * 3. Configures TTL for auto-expiring old conversations
 * 4. Provides a DynamoDBChatMemory bean as the primary ChatMemory implementation
 *
 * Configuration Properties (application.properties):
 * ```
 * # Enable DynamoDB chat memory
 * chat.memory.dynamodb.enabled=true
 *
 * # DynamoDB Local endpoint (Docker)
 * chat.memory.dynamodb.endpoint=http://localhost:8000
 *
 * # Table name
 * chat.memory.dynamodb.table-name=chat_history
 *
 * # TTL in days (0 = no expiration)
 * chat.memory.dynamodb.ttl-days=7
 * ```
 *
 * To use AWS DynamoDB instead of local:
 * - Remove the endpoint configuration
 * - Configure AWS credentials (AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY)
 * - Set region (e.g., us-east-1)
 */
@Configuration
@ConditionalOnProperty(name = "chat.memory.dynamodb.enabled", havingValue = "true", matchIfMissing = false)
public class DynamoDBConfig {

    private static final Logger log = LoggerFactory.getLogger(DynamoDBConfig.class);

    @Value("${chat.memory.dynamodb.endpoint:http://localhost:8000}")
    private String dynamodbEndpoint;

    @Value("${chat.memory.dynamodb.region:us-west-2}")
    private String region;

    @Value("${chat.memory.dynamodb.table-name:chat_history}")
    private String tableName;

    @Value("${chat.memory.dynamodb.ttl-days:7}")
    private int ttlDays;

    private DynamoDbClient dynamoDbClient;
    private DynamoDbEnhancedClient dynamoDbEnhancedClient;

    /**
     * DynamoDB Client Bean
     *
     * For DynamoDB Local: Uses fake credentials and local endpoint
     * For AWS DynamoDB: Remove endpoint override and use real credentials
     */
    @Bean
    public DynamoDbClient dynamoDbClient() {
        log.info("Initializing DynamoDB client for endpoint: {}", dynamodbEndpoint);

        var clientBuilder = DynamoDbClient.builder()
                .region(Region.of(region));

        // For DynamoDB Local, we need fake credentials and custom endpoint
        if (dynamodbEndpoint != null && !dynamodbEndpoint.isEmpty()) {
            clientBuilder
                    .endpointOverride(URI.create(dynamodbEndpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create("fakeAccessKey", "fakeSecretKey")));
            log.info("Configured for DynamoDB Local at {}", dynamodbEndpoint);
        } else {
            log.info("Configured for AWS DynamoDB in region {}", region);
        }

        DynamoDbClient client = clientBuilder.build();
        this.dynamoDbClient = client;
        return client;
    }

    /**
     * DynamoDB Enhanced Client Bean
     */
    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        DynamoDbEnhancedClient client = DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
        this.dynamoDbEnhancedClient = client;
        return client;
    }

    /**
     * Primary ChatMemory Bean - DynamoDB-backed implementation
     *
     * This replaces the in-memory ChatMemory with persistent storage
     */
    @Bean
    @Primary
    public ChatMemory dynamoDBChatMemory(DynamoDbEnhancedClient enhancedClient) {
        log.info("Creating DynamoDBChatMemory bean with table='{}', ttl={}days", tableName, ttlDays);
        return new DynamoDBChatMemory(enhancedClient, tableName, ttlDays);
    }

    /**
     * Initialize DynamoDB table after application is fully started
     *
     * This method is called AFTER all beans are created and the application context is ready.
     * Using ApplicationReadyEvent ensures beans are fully initialized.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeDynamoDBTable() {
        log.info("Initializing DynamoDB table '{}'", tableName);

        try {
            DynamoDbTable<ChatMessageEntity> table = dynamoDbEnhancedClient.table(
                    tableName,
                    TableSchema.fromBean(ChatMessageEntity.class)
            );

            // Check if table exists
            try {
                DescribeTableResponse response = dynamoDbClient.describeTable(DescribeTableRequest.builder()
                        .tableName(tableName)
                        .build());

                log.info("DynamoDB table '{}' already exists with status: {}",
                        tableName, response.table().tableStatus());

                // Enable TTL if configured
                if (ttlDays > 0) {
                    enableTTL(dynamoDbClient);
                }

            } catch (ResourceNotFoundException e) {
                // Table doesn't exist, create it
                log.info("Table '{}' does not exist, creating...", tableName);
                table.createTable();

                // Wait for table to become active
                dynamoDbClient.waiter().waitUntilTableExists(DescribeTableRequest.builder()
                        .tableName(tableName)
                        .build());

                log.info("Successfully created DynamoDB table '{}'", tableName);

                // Enable TTL if configured
                if (ttlDays > 0) {
                    enableTTL(dynamoDbClient);
                }
            }

        } catch (Exception e) {
            log.error("Error initializing DynamoDB table '{}'", tableName, e);
            throw new RuntimeException("Failed to initialize DynamoDB table", e);
        }
    }

    /**
     * Enable Time-To-Live (TTL) on the table for auto-expiring old conversations
     */
    private void enableTTL(DynamoDbClient client) {
        try {
            log.info("Enabling TTL on table '{}' with attribute 'expirationTime'", tableName);

            client.updateTimeToLive(UpdateTimeToLiveRequest.builder()
                    .tableName(tableName)
                    .timeToLiveSpecification(TimeToLiveSpecification.builder()
                            .enabled(true)
                            .attributeName("expirationTime")
                            .build())
                    .build());

            log.info("TTL enabled successfully. Messages will expire after {} days", ttlDays);

        } catch (Exception e) {
            log.warn("Could not enable TTL (this is normal if already enabled): {}", e.getMessage());
        }
    }
}