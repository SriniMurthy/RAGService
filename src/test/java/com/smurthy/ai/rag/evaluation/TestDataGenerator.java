package com.smurthy.ai.rag.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Generates test data from actual documents in the vector store.
 *
 * Strategy:
 * 1. Sample diverse chunks from vector store
 * 2. Use LLM to generate questions that each chunk can answer
 * 3. Store as golden dataset: Question → Set<RelevantChunkIDs>
 * 4. Optionally generate expected answers
 *
 * Usage:
 *   TestDataGenerator generator = ...;
 *   GoldenDataset dataset = generator.generateTestDataset(30); // 30 test cases
 *   generator.saveToFile(dataset, "src/test/resources/golden-dataset.json");
 */
@Service
public class TestDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(TestDataGenerator.class);

    private final JdbcClient jdbcClient;
    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public TestDataGenerator(JdbcClient jdbcClient, ChatModel chatModel) {
        this.jdbcClient = jdbcClient;
        this.chatModel = chatModel;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Sample chunks from vector store using stratified sampling.
     * Ensures diversity across files, categories, and time periods.
     */
    public List<DocumentChunk> sampleChunks(int count) {
        log.info("Sampling {} diverse chunks from vector store...", count);

        // Strategy: Sample from different categories/files to ensure diversity
        String sql = """
            WITH categorized AS (
                SELECT
                    id::text as id,
                    content,
                    metadata,
                    metadata->>'file_name' as file_name,
                    metadata->>'category' as category,
                    ROW_NUMBER() OVER (
                        PARTITION BY metadata->>'file_name'
                        ORDER BY RANDOM()
                    ) as rn
                FROM vector_store
                WHERE LENGTH(content) > 200  -- Filter out tiny chunks
            )
            SELECT id, content, metadata, file_name, category
            FROM categorized
            WHERE rn <= 3  -- Max 3 chunks per file for diversity
            LIMIT :count
            """;

        List<DocumentChunk> chunks = jdbcClient.sql(sql)
                .param("count", count)
                .query((rs, rowNum) -> new DocumentChunk(
                        rs.getString("id"),
                        rs.getString("content"),
                        rs.getString("file_name"),
                        rs.getString("category")
                ))
                .list();

        log.info("Sampled {} chunks from {} unique files",
                chunks.size(),
                chunks.stream().map(DocumentChunk::fileName).distinct().count());

        return chunks;
    }

    /**
     * Generate test questions for a single chunk using LLM.
     * Returns multiple question types for comprehensive testing.
     */
    public List<GeneratedQuestion> generateQuestionsForChunk(DocumentChunk chunk) {
        String prompt = String.format("""
            You are a test data generator for a RAG system. Given a document chunk, generate diverse test questions.

            CHUNK CONTENT:
            %s

            CHUNK METADATA:
            - File: %s
            - Category: %s

            Generate exactly 3 diverse questions that this chunk can answer:
            1. A factual question (who/what/when/where)
            2. A conceptual question (why/how/explain)
            3. A specific detail question (numbers, dates, names)

            CRITICAL RULES:
            - Questions MUST be answerable from this chunk alone
            - Questions should be natural, as a user would ask
            - Vary question complexity (easy, medium, hard)
            - Include specific entities/numbers from the chunk when possible

            Respond in this EXACT format:
            FACTUAL: <question>
            CONCEPTUAL: <question>
            DETAIL: <question>
            """,
            truncate(chunk.content(), 1000),  // Limit to avoid token overflow
            chunk.fileName(),
            chunk.category());

        try {
            String response = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
            return parseGeneratedQuestions(response, chunk);
        } catch (Exception e) {
            log.warn("Failed to generate questions for chunk {}: {}", chunk.id(), e.getMessage());
            return List.of();
        }
    }

    /**
     * Parse LLM response into structured questions
     */
    private List<GeneratedQuestion> parseGeneratedQuestions(String response, DocumentChunk chunk) {
        List<GeneratedQuestion> questions = new ArrayList<>();

        String[] lines = response.split("\n");
        for (String line : lines) {
            if (line.startsWith("FACTUAL:")) {
                String question = line.substring("FACTUAL:".length()).trim();
                questions.add(new GeneratedQuestion(
                        question,
                        Set.of(chunk.id()),
                        "factual",
                        chunk.fileName()));
            } else if (line.startsWith("CONCEPTUAL:")) {
                String question = line.substring("CONCEPTUAL:".length()).trim();
                questions.add(new GeneratedQuestion(
                        question,
                        Set.of(chunk.id()),
                        "conceptual",
                        chunk.fileName()));
            } else if (line.startsWith("DETAIL:")) {
                String question = line.substring("DETAIL:".length()).trim();
                questions.add(new GeneratedQuestion(
                        question,
                        Set.of(chunk.id()),
                        "detail",
                        chunk.fileName()));
            }
        }

        return questions;
    }

    /**
     * Generate complete golden dataset for testing
     */
    public GoldenDataset generateTestDataset(int numQuestions) {
        log.info("Generating golden dataset with {} questions...", numQuestions);

        // Sample more chunks than questions to allow for diversity
        int chunksToSample = Math.min(numQuestions, 50);
        List<DocumentChunk> chunks = sampleChunks(chunksToSample);

        List<GeneratedQuestion> allQuestions = new ArrayList<>();

        for (int i = 0; i < chunks.size() && allQuestions.size() < numQuestions; i++) {
            DocumentChunk chunk = chunks.get(i);
            log.info("Generating questions for chunk {}/{} (file: {})",
                    i + 1, chunks.size(), chunk.fileName());

            List<GeneratedQuestion> questions = generateQuestionsForChunk(chunk);
            allQuestions.addAll(questions);

            // Small delay to avoid rate limiting
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Trim to exact count if we over-generated
        if (allQuestions.size() > numQuestions) {
            allQuestions = allQuestions.subList(0, numQuestions);
        }

        GoldenDataset dataset = new GoldenDataset(
                allQuestions,
                new Date().toString(),
                Map.of(
                        "total_questions", allQuestions.size(),
                        "unique_files", allQuestions.stream()
                                .map(GeneratedQuestion::sourceFile)
                                .distinct()
                                .count(),
                        "question_types", allQuestions.stream()
                                .map(GeneratedQuestion::type)
                                .distinct()
                                .count()
                )
        );

        log.info("Generated {} questions from {} unique files",
                dataset.questions().size(),
                dataset.metadata().get("unique_files"));

        return dataset;
    }

    /**
     * Save dataset to JSON file
     */
    public void saveToFile(GoldenDataset dataset, String filePath) {
        try {
            File file = new File(filePath);
            file.getParentFile().mkdirs();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, dataset);
            log.info("Saved golden dataset to: {}", file.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save dataset to {}: {}", filePath, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Load dataset from JSON file
     */
    public GoldenDataset loadFromFile(String filePath) {
        try {
            File file = new File(filePath);
            GoldenDataset dataset = objectMapper.readValue(file, GoldenDataset.class);
            log.info("Loaded {} questions from {}", dataset.questions().size(), filePath);
            return dataset;
        } catch (IOException e) {
            log.error("Failed to load dataset from {}: {}", filePath, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private String truncate(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    // ========== Data Classes ==========

    public record DocumentChunk(
            String id,
            String content,
            String fileName,
            String category
    ) {}

    public record GeneratedQuestion(
            String question,
            Set<String> relevantChunkIds,  // IDs of chunks that contain the answer
            String type,                   // factual, conceptual, detail
            String sourceFile              // Which file this question came from
    ) {}

    public record GoldenDataset(
            List<GeneratedQuestion> questions,
            String generatedAt,
            Map<String, Object> metadata
    ) {}
}