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
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
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

    public RAFTDatasetGenerationService(VectorStore vectorStore, ChatModel chatModel, JdbcTemplate jdbcTemplate, ExecutorService taskExecutor) {
        this.vectorStore = vectorStore;
        this.chatModel = chatModel;
        this.jdbcTemplate = jdbcTemplate;
        this.taskExecutor = taskExecutor;
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

        List<Future<Map<String, Object>>> futures = new ArrayList<>();
        for (String chunk : allChunks) {
            Callable<Map<String, Object>> task = () -> processChunk(chunk);
            futures.add(taskExecutor.submit(task));
        }

        List<Map<String, Object>> rawDataset = new ArrayList<>();
        for (Future<Map<String, Object>> future : futures) {
            try {
                Map<String, Object> result = future.get();
                if (result != null) {
                    rawDataset.add(result);
                }
            } catch (InterruptedException | ExecutionException e) {
                log.error("Error processing a chunk future: {}", e.getMessage(), e);
            }
        }

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
        try {
            Prompt prompt = new Prompt(new UserMessage(promptTemplate.replace("{chunk}", chunkContent)));
            return chatModel.call(prompt).getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("Failed to generate question for chunk: {}", e.getMessage());
            return null;
        }
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
