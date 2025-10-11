package com.smurthy.ai.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("data-ingestion")
public class RAGDataIngester {

    private static final Logger log = LoggerFactory.getLogger(RAGDataIngester.class);

    private final DocumentIngestionService ingestionService;

    public RAGDataIngester(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
        log.info("RAGDataIngester initialized. Ready to process documents from the watch directory.");
    }
}
