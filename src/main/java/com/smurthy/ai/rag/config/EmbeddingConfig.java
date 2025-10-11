package com.smurthy.ai.rag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for embedding settings
 */
@ConfigurationProperties(prefix = "app.embedding")
public record EmbeddingConfig(
        @DefaultValue("text-embedding-3-small") String model,
        @DefaultValue("100") int batchSize,
        @DefaultValue("1000") long delayBetweenBatchesMs,
        @DefaultValue("8000") int maxTokensPerBatch
) {
}