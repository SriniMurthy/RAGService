package com.smurthy.ai.rag.config;

import com.smurthy.ai.rag.service.DocumentIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.FileSystemResource;
import org.springframework.integration.config.EnableIntegration;
import org.springframework.integration.core.MessageSource;
import org.springframework.integration.dsl.IntegrationFlow;
import org.springframework.integration.file.dsl.Files;
import org.springframework.integration.file.filters.FileSystemPersistentAcceptOnceFileListFilter;
import org.springframework.integration.metadata.SimpleMetadataStore;

import java.io.File;

/**
 * FileWatcherConfig is a Spring @Configuration class that sets up a file-watching
 * mechanism using Spring Integration. Its primary purpose is to monitor
 * a directory for new files and trigger an ingestion process when a new file is detected.
 */
@Configuration
@EnableIntegration
@Profile("data-ingestion")
public class FileWatcherConfig {

    private static final Logger log = LoggerFactory.getLogger(FileWatcherConfig.class);

    @Value("${rag.documents.watch-dir}")
    private String watchDir;

    @Bean
    public MessageSource<File> fileReadingMessageSource() {
        File directory = new File(watchDir);
        if (!directory.exists()) {
            log.info("Creating watch directory: {}", directory.getAbsolutePath());
            directory.mkdirs();
        }
        // Use a persistent filter to remember processed files across restarts
        var filter = new FileSystemPersistentAcceptOnceFileListFilter(new SimpleMetadataStore(), "rag-file-watcher-");
        return Files.inboundAdapter(directory).filter(filter).getObject();
    }

    @Bean
    public IntegrationFlow fileIngestionFlow(DocumentIngestionService ingestionService) {
        return IntegrationFlow.from(fileReadingMessageSource(), spec -> spec.poller(poller -> poller.fixedDelay(5000)))
                // When a file is processed, move it to a 'processed' subdirectory to prevent re-scanning
                .enrichHeaders(h -> h.header("file_originalFile", "payload"))
                .transform(File.class, file -> new FileSystemResource(file))
                .handle(ingestionService, "ingest")
                .get();
    }
}