package com.smurthy.ai.rag.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Unified Dataset Generator - supports both RAFT (training) and Evaluation (testing) modes
 *
 * TWO MODES:
 *
 * 1. RAFT MODE (Fine-tuning training data):
 *    - Purpose: Create datasets to fine-tune LLMs
 *    - Output: question + golden_document + distractor_documents
 *    - Format: Standard RAFT format for model training
 *    - Use: generateRAFTDataset()
 *
 * 2. EVALUATION MODE (RAG quality testing):
 *    - Purpose: Test chunking/retrieval strategies
 *    - Output: question + chunk IDs + question types
 *    - Format: Golden dataset with diverse question types
 *    - Use: generateEvaluationDataset()
 *
 * USAGE:
 *   UnifiedDatasetGenerator generator = ...;
 *
 *   // For model training
 *   generator.generateRAFTDataset("finance", 100);
 *
 *   // For RAG evaluation
 *   generator.generateEvaluationDataset(30);
 */
@Service
public class UnifiedDatasetGenerator {

    private static final Logger log = LoggerFactory.getLogger(UnifiedDatasetGenerator.class);

    private final JdbcClient jdbcClient;
    private final ChatModel chatModel;
    private final VectorStore vectorStore;
    private final ObjectMapper objectMapper;

    public UnifiedDatasetGenerator(
            JdbcClient jdbcClient,
            ChatModel chatModel,
            VectorStore vectorStore) {
        this.jdbcClient = jdbcClient;
        this.chatModel = chatModel;
        this.vectorStore = vectorStore;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    // ========================================================================
    // MODE 1: RAFT DATASET GENERATION (Training Data)
    // ========================================================================

    /**
     * Generate RAFT dataset for fine-tuning.
     *
     * Format:
     * {
     *   "question": "What is the revenue for Q4?",
     *   "golden_document": "Q4 revenue was $10M...",
     *   "distractor_documents": ["Q3 had...", "Q2 saw..."],
     *   "ideal_answer": ""
     * }
     */
    public RAFTDataset generateRAFTDataset(String category, int numSamples) {
        log.info("Generating RAFT dataset for category '{}' with {} samples...", category, numSamples);

        List<DocumentChunk> chunks = sampleChunksByCategory(category, numSamples);
        List<RAFTDataPoint> dataPoints = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            log.info("Processing chunk {}/{} for RAFT (category: {})", i + 1, chunks.size(), category);

            RAFTDataPoint dataPoint = generateRAFTDataPoint(chunk);
            if (dataPoint != null) {
                dataPoints.add(dataPoint);
            }

            // Rate limit protection
            sleep(500);
        }

        RAFTDataset dataset = new RAFTDataset(
                dataPoints,
                category,
                new Date().toString(),
                Map.of("mode", "RAFT", "total_samples", dataPoints.size())
        );

        log.info("Generated {} RAFT data points for category '{}'", dataPoints.size(), category);
        return dataset;
    }

    private RAFTDataPoint generateRAFTDataPoint(DocumentChunk chunk) {
        // Generate a realistic question
        String question = generateRealisticQuestion(chunk.content());
        if (question == null || question.trim().isEmpty()) {
            log.warn("Failed to generate question for chunk {}", chunk.id());
            return null;
        }

        // Verify this chunk is retrieved for the question
        List<Document> searchResults = vectorStore.similaritySearch(
                SearchRequest.builder().query(question).topK(5).build()
        );

        if (searchResults.isEmpty()) {
            log.warn("No search results for question: {}", question);
            return null;
        }

        // Check if our chunk is the top result
        String topChunkContent = searchResults.get(0).getText();
        if (!topChunkContent.equals(chunk.content())) {
            log.debug("Chunk {} not top result for its question - skipping", chunk.id());
            return null;
        }

        // Collect distractor documents
        List<String> distractors = searchResults.stream()
                .skip(1)
                .map(Document::getText)
                .limit(4)
                .collect(Collectors.toList());

        return new RAFTDataPoint(
                question,
                chunk.content(),
                distractors,
                "" // ideal_answer left blank for human annotation
        );
    }

    private String generateRealisticQuestion(String chunkContent) {
        String prompt = """
            You are an expert at creating realistic questions for a dataset.
            Based ONLY on the following text, create ONE specific question that this text can answer.
            The question should be something a real user would ask.
            Return only the question itself, with no preamble or quotation marks.

            TEXT:
            %s
            """.formatted(truncate(chunkContent, 1000));

        try {
            return chatModel.call(new Prompt(prompt)).getResult().getOutput().getText().trim();
        } catch (Exception e) {
            log.error("Failed to generate question: {}", e.getMessage());
            return null;
        }
    }

    // ========================================================================
    // MODE 2: EVALUATION DATASET GENERATION (Testing Data)
    // ========================================================================

    /**
     * Generate evaluation dataset for RAG quality testing.
     *
     * Format:
     * {
     *   "question": "What companies did X work for?",
     *   "relevantChunkIds": ["uuid-123"],
     *   "type": "factual",
     *   "sourceFile": "resume.pdf"
     * }
     */
    public EvaluationDataset generateEvaluationDataset(int numQuestions) {
        log.info("Generating evaluation dataset with {} questions...", numQuestions);

        // Sample diverse chunks
        int chunksToSample = Math.min(numQuestions / 2, 50); // Generate 2-3 questions per chunk
        List<DocumentChunk> chunks = sampleDiverseChunks(chunksToSample);

        List<EvaluationQuestion> allQuestions = new ArrayList<>();

        for (int i = 0; i < chunks.size() && allQuestions.size() < numQuestions; i++) {
            DocumentChunk chunk = chunks.get(i);
            log.info("Generating evaluation questions {}/{} from file: {}",
                    i + 1, chunks.size(), chunk.fileName());

            List<EvaluationQuestion> questions = generateDiverseQuestions(chunk);
            allQuestions.addAll(questions);

            sleep(500);
        }

        // Trim to exact count
        if (allQuestions.size() > numQuestions) {
            allQuestions = allQuestions.subList(0, numQuestions);
        }

        EvaluationDataset dataset = new EvaluationDataset(
                allQuestions,
                new Date().toString(),
                Map.of(
                        "mode", "EVALUATION",
                        "total_questions", allQuestions.size(),
                        "unique_files", allQuestions.stream()
                                .map(EvaluationQuestion::sourceFile)
                                .distinct()
                                .count()
                )
        );

        log.info("Generated {} evaluation questions from {} unique files",
                dataset.questions().size(),
                dataset.metadata().get("unique_files"));

        return dataset;
    }

    private List<EvaluationQuestion> generateDiverseQuestions(DocumentChunk chunk) {
        String prompt = """
            You are a test data generator for a RAG system. Generate diverse test questions.

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
            - Include specific entities/numbers from the chunk

            Respond in this EXACT format:
            FACTUAL: <question>
            CONCEPTUAL: <question>
            DETAIL: <question>
            """.formatted(
                    truncate(chunk.content(), 1000),
                    chunk.fileName(),
                    chunk.category());

        try {
            String response = chatModel.call(new Prompt(prompt)).getResult().getOutput().getText();
            return parseDiverseQuestions(response, chunk);
        } catch (Exception e) {
            log.warn("Failed to generate questions for chunk {}: {}", chunk.id(), e.getMessage());
            return List.of();
        }
    }

    private List<EvaluationQuestion> parseDiverseQuestions(String response, DocumentChunk chunk) {
        List<EvaluationQuestion> questions = new ArrayList<>();
        String[] lines = response.split("\n");

        for (String line : lines) {
            if (line.startsWith("FACTUAL:")) {
                questions.add(new EvaluationQuestion(
                        line.substring("FACTUAL:".length()).trim(),
                        Set.of(chunk.id()),
                        "factual",
                        chunk.fileName()
                ));
            } else if (line.startsWith("CONCEPTUAL:")) {
                questions.add(new EvaluationQuestion(
                        line.substring("CONCEPTUAL:".length()).trim(),
                        Set.of(chunk.id()),
                        "conceptual",
                        chunk.fileName()
                ));
            } else if (line.startsWith("DETAIL:")) {
                questions.add(new EvaluationQuestion(
                        line.substring("DETAIL:".length()).trim(),
                        Set.of(chunk.id()),
                        "detail",
                        chunk.fileName()
                ));
            }
        }

        return questions;
    }

    // ========================================================================
    // COMMON UTILITIES
    // ========================================================================

    /**
     * Sample chunks from a specific category (for RAFT)
     */
    private List<DocumentChunk> sampleChunksByCategory(String category, int count) {
        String sql = """
            SELECT id::text as id, content, metadata->>'file_name' as file_name, metadata->>'category' as category
            FROM vector_store
            WHERE metadata->>'category' = :category
              AND LENGTH(content) > 200
            ORDER BY RANDOM()
            LIMIT :count
            """;

        return jdbcClient.sql(sql)
                .param("category", category)
                .param("count", count)
                .query((rs, rowNum) -> new DocumentChunk(
                        rs.getString("id"),
                        rs.getString("content"),
                        rs.getString("file_name"),
                        rs.getString("category")
                ))
                .list();
    }

    /**
     * Sample diverse chunks across all files (for evaluation)
     */
    private List<DocumentChunk> sampleDiverseChunks(int count) {
        String sql = """
            WITH categorized AS (
                SELECT
                    id::text as id,
                    content,
                    metadata->>'file_name' as file_name,
                    metadata->>'category' as category,
                    ROW_NUMBER() OVER (
                        PARTITION BY metadata->>'file_name'
                        ORDER BY RANDOM()
                    ) as rn
                FROM vector_store
                WHERE LENGTH(content) > 200
            )
            SELECT id, content, file_name, category
            FROM categorized
            WHERE rn <= 3
            LIMIT :count
            """;

        return jdbcClient.sql(sql)
                .param("count", count)
                .query((rs, rowNum) -> new DocumentChunk(
                        rs.getString("id"),
                        rs.getString("content"),
                        rs.getString("file_name"),
                        rs.getString("category")
                ))
                .list();
    }

    /**
     * Save dataset to JSON file
     */
    public void saveToFile(Object dataset, String filePath) {
        try {
            File file = new File(filePath);
            file.getParentFile().mkdirs();
            objectMapper.writeValue(file, dataset);
            log.info("Saved dataset to: {}", file.getAbsolutePath());
        } catch (IOException e) {
            log.error("Failed to save dataset to {}: {}", filePath, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    /**
     * Load dataset from JSON file
     */
    public <T> T loadFromFile(String filePath, Class<T> type) {
        try {
            File file = new File(filePath);
            T dataset = objectMapper.readValue(file, type);
            log.info("Loaded dataset from {}", filePath);
            return dataset;
        } catch (IOException e) {
            log.error("Failed to load dataset from {}: {}", filePath, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private String truncate(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ========================================================================
    // DATA CLASSES
    // ========================================================================

    public record DocumentChunk(String id, String content, String fileName, String category) {}

    // RAFT Mode (Training)
    public record RAFTDataPoint(
            String question,
            String golden_document,
            List<String> distractor_documents,
            String ideal_answer
    ) {}

    public record RAFTDataset(
            List<RAFTDataPoint> dataPoints,
            String category,
            String generatedAt,
            Map<String, Object> metadata
    ) {}

    // Evaluation Mode (Testing)
    public record EvaluationQuestion(
            String question,
            Set<String> relevantChunkIds,
            String type,
            String sourceFile
    ) {}

    public record EvaluationDataset(
            List<EvaluationQuestion> questions,
            String generatedAt,
            Map<String, Object> metadata
    ) {}
}