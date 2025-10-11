package com.smurthy.ai.rag.config;

import com.smurthy.ai.rag.service.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.util.Arrays;

@Configuration
@Profile("data-ingestion")
// This configuration will only be active when the 'data-ingestion' profile is used
public class StartupIngestionConfig {

    private static final Logger log = LoggerFactory.getLogger(StartupIngestionConfig.class);

    @Value("${rag.documents.path}")
    private Resource[] documentResources;

    @Bean
    public CommandLineRunner startupIngestionRunner(DocumentIngestionService ingestionService) {
        return args -> {
            log.info("Performing startup ingestion of documents from classpath...");
            Arrays.stream(documentResources).forEach(ingestionService::ingest);
        };
    }
}