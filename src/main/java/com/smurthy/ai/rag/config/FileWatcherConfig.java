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
import org.springframework.integration.file.filters.AcceptAllFileListFilter;
import org.springframework.integration.file.filters.FileSystemPersistentAcceptOnceFileListFilter;
import org.springframework.integration.metadata.SimpleMetadataStore;

import java.io.File;

/**
 * FileWatcherConfig sets up a file-watching mechanism using Spring Integration.
 * It monitors a directory for new files and triggers an ingestion process.
 * The parent directory of a file is used as its category.
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

        var adapter = Files.inboundAdapter(directory)
                .recursive(true) // Scan subdirectories
                .preventDuplicates(true)
                .useWatchService(true);

        // Use a persistent filter to remember processed files across restarts
        var persistentFilter = new FileSystemPersistentAcceptOnceFileListFilter(new SimpleMetadataStore(), "rag-file-watcher-");

        // This filter ensures we only process files, not directories
        adapter.filter(new AcceptAllFileListFilter<>() {
            @Override
            public boolean accept(File file) {
                return file.isFile();
            }
        });

        return adapter.getObject();
    }

    @Bean
    public IntegrationFlow fileIngestionFlow(DocumentIngestionService ingestionService) {
        return IntegrationFlow.from(fileReadingMessageSource(), spec -> spec.poller(poller -> poller.fixedDelay(5000)))
                .enrichHeaders(h -> h.header("file_originalFile", "payload"))
                .handle(File.class, (file, headers) -> {
                    // The parent directory's name is used as the category
                    String category = file.getParentFile().getName();
                    log.info("👁️ File detected in watch directory: '{}' with category: '{}'", file.getName(), category);
                    ingestionService.ingest(new FileSystemResource(file), category);
                    return null; // No further processing needed
                })
                .get();
    }
}
