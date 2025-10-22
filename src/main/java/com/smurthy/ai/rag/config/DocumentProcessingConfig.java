package com.smurthy.ai.rag.config;

import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Document Processing Configuration
 *
 * Configures text splitting with optimized parameters for RAG:
 * - Chunk Size: 512 tokens (balanced between context and specificity)
 * - Overlap: 128 tokens (~25% overlap to preserve context across chunks)
 * - Min Chunk Size: 100 tokens (prevents tiny meaningless chunks)
 *
 * These settings optimize for:
 * 1. Semantic coherence (chunks maintain complete thoughts)
 * 2. Retrieval accuracy (sufficient context for embedding similarity)
 * 3. LLM efficiency (fits multiple chunks in context window)
 */
@Configuration
public class DocumentProcessingConfig {

    /**
     * Optimized TokenTextSplitter for RAG retrieval quality.
     *
     * Rationale:
     * - 512 tokens ≈ 1-2 paragraphs, captures complete ideas
     * - 128 token overlap ensures no information loss at chunk boundaries
     * - Min 100 tokens filters out headers/footers that lack semantic value
     */
    @Bean
    public TextSplitter tokenTextSplitter() {
        return  new TokenTextSplitter(512, 100, 128, 800, true);
    }
}