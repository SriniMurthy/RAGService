package com.smurthy.ai.rag.config;

import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DocumentProcessingConfig {

    /**
     * Defines a TokenTextSplitter bean for splitting documents into chunks.
     */
    @Bean
    public TextSplitter tokenTextSplitter() {
        return new TokenTextSplitter();
    }
}