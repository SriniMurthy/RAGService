package com.smurthy.ai.rag.scheduler;

import com.smurthy.ai.rag.service.RAFTDatasetGenerationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * NOTE: This scheduler is currently DISABLED.
 * The RAFT dataset generation process has been converted to a manual process
 * triggered by a CommandLineRunner in DatasetGenerationConfig.
 * This class is kept for future reference if a scheduled process is needed again.
 * TODO: Work In Progress, not tested (at all)
 */
// @Component
// @Profile("dataset-generation")
public class RAFTDatasetGenerationScheduler {

    private static final Logger log = LoggerFactory.getLogger(RAFTDatasetGenerationScheduler.class);
    private final RAFTDatasetGenerationService generationService;

    public RAFTDatasetGenerationScheduler(RAFTDatasetGenerationService generationService) {
        this.generationService = generationService;
    }

    /**
     * Runs the dataset generation process for a predefined list of categories at 2:00 AM every day.
     * This cron job ensures that new potential training data is generated during off-peak hours.
     */
   // @Scheduled(cron = "0 0 2 * * ?") // Runs at 2:00 AM every day
    public void runGenerationForAllCategories() {
        log.info("Kicking off scheduled nightly RAFT dataset generation...");

        // Define the list of categories you want to generate datasets for.
        List<String> categoriesToProcess = List.of("finance", "textbooks");

        for (String category : categoriesToProcess) {
            try {
                generationService.generateForCategory(category);
            } catch (Exception e) {
                log.error("Unhandled exception during generation for category '{}': {}", category, e.getMessage(), e);
            }
        }

        log.info(" Scheduled nightly RAFT dataset generation run finished.");
    }
}
