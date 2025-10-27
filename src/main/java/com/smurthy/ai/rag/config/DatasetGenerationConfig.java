package com.smurthy.ai.rag.config;

import com.smurthy.ai.rag.service.RAFTDatasetGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * This configuration is active only when the 'dataset-generation' profile is enabled.
 * It provides a command-line runner to manually trigger the RAFT dataset generation process.
 */
@Configuration
@Profile("dataset-generation")
public class DatasetGenerationConfig {

    private static final Logger log = LoggerFactory.getLogger(DatasetGenerationConfig.class);

    /**
     * Creates an ExecutorService for parallel processing of dataset generation tasks.
     * Uses a fixed thread pool sized based on available processors.
     *
     * @return ExecutorService for parallel task execution
     */
    @Bean
    public ExecutorService datasetGenerationExecutor() {
        int threadPoolSize = Math.max(4, Runtime.getRuntime().availableProcessors());
        log.info("Creating ExecutorService with {} threads for dataset generation", threadPoolSize);
        return Executors.newFixedThreadPool(threadPoolSize);
    }

    /**
     * Creates a CommandLineRunner that triggers the dataset generation logic.
     * The process is manually triggered by running the application with this profile
     * and specifying a category via a system property.
     *
     * Example: ./mvnw spring-boot:run -Dspring-boot.run.profiles=dataset-generation -Dgenerate.category=finance
     */
    @Bean
    public CommandLineRunner generateRaftDataset(RAFTDatasetGenerationService service) {
        return args -> {
            String category = System.getProperty("generate.category");

            if (category == null || category.trim().isEmpty()) {
                log.error("Missing required system property 'generate.category'.");
                log.error("Please run with: -Dgenerate.category=<your_category_name>");
                return;
            }

            service.generateForCategory(category);
        };
    }
}
