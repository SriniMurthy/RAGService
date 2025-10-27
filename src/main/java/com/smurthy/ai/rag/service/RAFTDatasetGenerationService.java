package com.smurthy.ai.rag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

/**
 * Service to generate a raw dataset for RAFT (Retrieval-Augmented Fine-Tuning).
 * This service is designed to be called by a scheduler and uses a thread pool
 * to process document chunks in parallel for higher throughput.
 */
@Service
@Profile("dataset-generation")
public class RAFTDatasetGenerationService {

    private static final Logger log = LoggerFactory.getLogger(RAFTDatasetGenerationService.class);

    private final VectorStore vectorStore;
    private final ChatModel chatModel;
    private final JdbcTemplate jdbcTemplate;
    private final ExecutorService taskExecutor;

    // Rate limiting: Control concurrent OpenAI API calls to avoid hitting rate limits
    private final Semaphore rateLimiter;

    @Value("${raft.max-concurrent-llm-calls:10}")
    private int maxConcurrentLlmCalls;

    public RAFTDatasetGenerationService(VectorStore vectorStore, ChatModel chatModel, JdbcTemplate jdbcTemplate, ExecutorService taskExecutor) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.jdbcTemplate = jdbcTemplate;
        this.taskExecutor = taskExecutor;
        // Will be updated by @Value
        this.rateLimiter = new Semaphore(10);
        log.info("Rate limiter initialized with max concurrent LLM calls: {}", maxConcurrentLlmCalls);
    }

    /**
     * Generates datasets for all categories found in the vector store.
     * Uses virtual threads to process multiple categories concurrently.
     */
    public void generateForAllCategories() {
        log.info("Starting RAFT dataset generation for ALL categories...");

        List<String> categories = getAllCategories();

        if (categories.isEmpty()) {
            log.warn("No categories found in vector store. Aborting.");
            return;
        }

        log.info("Found {} distinct categories: {}", categories.size(), categories);
        log.info("Processing categories in parallel using virtual threads...");

        long startTime = System.currentTimeMillis();

        List<CompletableFuture<Void>> futures = categories.stream()
                .map(category -> CompletableFuture.runAsync(() -> {
                    try {
                        log.info("[Virtual Thread: {}] Starting dataset generation for category: '{}'",
                                Thread.currentThread().getName(), category);
                        generateForCategory(category);
                        log.info("[Virtual Thread: {}] Completed dataset generation for category: '{}'",
                                Thread.currentThread().getName(), category);
                    } catch (Exception e) {
                        log.error("[Virtual Thread: {}] Failed to generate dataset for category '{}': {}",
                                Thread.currentThread().getName(), category, e.getMessage(), e);
                    }
                }, taskExecutor) // Use the shared virtual thread executor
                ).toList();

        // CompletableFuture.allOf() creates a single future that completes when all others are done.
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Completed RAFT dataset generation for all {} categories in {} seconds.",
                categories.size(), duration / 1000.0);
    }

    /**
     * Retrieves all distinct categories from the vector store.
     * @return List of category names
     */
    private List<String> getAllCategories() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT metadata ->> 'category' as category FROM vector_store WHERE metadata ->> 'category' IS NOT NULL ORDER BY category",
                String.class
        );
    }

    /**
     * Generates a dataset for a specific document category.
     * @param category The category to filter documents by.
     */
    public void generateForCategory(String category) {
        log.info("Starting RAFT dataset generation for category: '{}'...", category);

        List<String> allChunks = jdbcTemplate.queryForList(
                "SELECT content FROM vector_store WHERE metadata ->> 'category' = ?",
                String.class,
                category
        );

        if (allChunks.isEmpty()) {
            log.warn("No document chunks found for category '{}'. Aborting.", category);
            return;
        }

        log.info("Found {} document chunks to process for category '{}'.", allChunks.size(), category);

        // Use CompletableFuture for non-blocking, efficient parallel execution.
        List<CompletableFuture<Map<String, Object>>> futures = allChunks.stream()
                .map(chunk -> CompletableFuture.supplyAsync(() -> processChunk(chunk), taskExecutor)
                        .exceptionally(ex -> {
                            log.error("Error processing a chunk future: {}", ex.getMessage(), ex);
                            return null; // Return null for failed chunks
                        }))
                .toList();

        //  Wait for all futures to complete and then collect the results.
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        List<Map<String, Object>> rawDataset = futures.stream()
                .map(CompletableFuture::join) // Safe to join now as all are complete
                .filter(result -> result != null) // Filter out failed chunks
                .collect(Collectors.toList());

        log.info("Completed processing all chunks. {} data points were generated.", rawDataset.size());

        // Create a unique filename with a timestamp
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filename = String.format("raft_dataset_%s_raw_%s.json", category, timestamp);

        saveDatasetToFile(rawDataset, filename);
    }

    private Map<String, Object> processChunk(String chunk) {
        String question = generateQuestionForChunk(chunk);
        if (question == null || question.trim().isEmpty()) {
            return null;
        }

        List<Document> searchResults = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(5).build()
        );

        if (searchResults.isEmpty() || !searchResults.get(0).getText().equals(chunk)) {
            return null;
        }

        String goldenDocument = searchResults.get(0).getText();
        List<String> distractorDocuments = searchResults.stream()
                .skip(1)
                .map(Document::getText)
                .collect(Collectors.toList());

        Map<String, Object> dataPoint = new LinkedHashMap<>();
        dataPoint.put("question", question);
        dataPoint.put("golden_document", goldenDocument);
        dataPoint.put("distractor_documents", distractorDocuments);
        dataPoint.put("ideal_answer_human_written", "");

        log.info("Successfully created data point for question: '{}'", question);
        return dataPoint;
    }

    private String generateQuestionForChunk(String chunkContent) {
        String promptTemplate = """
            You are an expert at creating high-quality questions for a fine-tuning dataset.
            Based ONLY on the following text, create one specific and clear question that this text can answer.
            The question should be something a user would realistically ask.
            Do not ask a question that requires any outside knowledge.
            Return only the question itself, with no preamble or quotation marks.

            TEXT:
            ---
            {chunk}
            ---
            """;

        // Rate limiting: Acquire permit before making OpenAI API call
        try {
            rateLimiter.acquire(); // Blocks if too many concurrent calls
            log.debug("Acquired rate limit permit. Available permits: {}", rateLimiter.availablePermits());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while waiting for rate limit permit");
            return null;
        }

        try {
            Prompt prompt = new Prompt(new UserMessage(promptTemplate.replace("{chunk}", chunkContent)));
            return chatModel.call(prompt).getResult().getOutput().getText();
        } catch (Exception e) {
            // Check if it's a rate limit error (HTTP 429 or contains "rate_limit_exceeded")
            String errorMessage = e.getMessage() != null ? e.getMessage() : "";

            if (isIsRateLimitError(errorMessage)) {
                log.warn("⚠ OpenAI rate limit hit despite semaphore control. Consider reducing raft.max-concurrent-llm-calls from {}. Error: {}",
                        maxConcurrentLlmCalls, errorMessage);
            } else {
                log.error("Failed to generate question for chunk: {}", errorMessage);
            }
            return null;
        } finally {
            rateLimiter.release();
            log.debug("Released rate limit permit. Available permits: {}", rateLimiter.availablePermits());
        }
    }

    private static boolean isIsRateLimitError(String errorMessage) {
        return errorMessage.contains("429") ||
                errorMessage.contains("rate_limit_exceeded") ||
                errorMessage.contains("Rate limit reached");
    }

    private void saveDatasetToFile(List<Map<String, Object>> dataset, String filename) {
        if (dataset.isEmpty()) {
            log.warn("Dataset is empty. No file will be written.");
            return;
        }
        File outputFile = new File(filename);
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(outputFile, dataset);
            log.info("Successfully saved dataset with {} entries to: {}", dataset.size(), outputFile.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to write dataset to file: {}", outputFile.getAbsolutePath(), e);
        }
    }
}
