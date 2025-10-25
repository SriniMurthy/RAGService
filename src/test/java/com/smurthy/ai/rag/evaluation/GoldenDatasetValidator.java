package com.smurthy.ai.rag.evaluation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Validates golden dataset by cross-checking questions against their source chunks.
 *
 * Usage:
 *   GoldenDatasetValidator validator = ...;
 *   validator.validateDataset("src/test/resources/golden-dataset.json");
 *
 * Outputs:
 * - Question text
 * - Expected chunk IDs
 * - Actual chunk content
 * - Manual review: Does the chunk answer the question?
 */
@Service
public class GoldenDatasetValidator {

    private static final Logger log = LoggerFactory.getLogger(GoldenDatasetValidator.class);

    private final JdbcClient jdbcClient;
    private final TestDataGenerator testDataGenerator;

    public GoldenDatasetValidator(JdbcClient jdbcClient, TestDataGenerator testDataGenerator) {
        this.jdbcClient = jdbcClient;
        this.testDataGenerator = testDataGenerator;
    }

    /**
     * Validate entire dataset - prints each question with its source chunk for manual review
     */
    public ValidationReport validateDataset(String datasetPath) {
        TestDataGenerator.GoldenDataset dataset = testDataGenerator.loadFromFile(datasetPath);

        System.out.println("\n╔═══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              VALIDATING GOLDEN DATASET - MANUAL REVIEW                     ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════════╝\n");

        int totalQuestions = dataset.questions().size();
        int validQuestions = 0;
        int missingChunks = 0;

        for (int i = 0; i < dataset.questions().size(); i++) {
            TestDataGenerator.GeneratedQuestion question = dataset.questions().get(i);

            System.out.println("┌───────────────────────────────────────────────────────────────────────────┐");
            System.out.printf("│ QUESTION %d/%d%n", i + 1, totalQuestions);
            System.out.println("├───────────────────────────────────────────────────────────────────────────┤");
            System.out.println();

            System.out.println("Type:     " + question.type());
            System.out.println("Source:   " + question.sourceFile());
            System.out.println();
            System.out.println("Question:");
            System.out.println("  \"" + question.question() + "\"");
            System.out.println();

            // Retrieve the actual chunk content
            Set<String> chunkIds = question.relevantChunkIds();
            System.out.println("Expected to be answerable from " + chunkIds.size() + " chunk(s):");
            System.out.println();

            boolean allChunksFound = true;
            for (String chunkId : chunkIds) {
                ChunkContent chunk = getChunkById(chunkId);

                if (chunk != null) {
                    System.out.println("  Chunk ID: " + chunkId);
                    System.out.println("  ─────────────────────────────────────────────────────────────────────");
                    System.out.println(wrapText(chunk.content(), 75, "  "));
                    System.out.println("  ─────────────────────────────────────────────────────────────────────");
                    System.out.println();
                } else {
                    System.out.println("  WARNING: Chunk ID " + chunkId + " NOT FOUND in vector store!");
                    System.out.println();
                    allChunksFound = false;
                    missingChunks++;
                }
            }

            if (allChunksFound) {
                validQuestions++;
            }

            System.out.println("└───────────────────────────────────────────────────────────────────────────┘");
            System.out.println();
        }

        // Summary
        System.out.println("\n╔═══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                         VALIDATION SUMMARY                                 ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════════╝\n");

        System.out.printf("Total Questions:           %d%n", totalQuestions);
        System.out.printf("Questions with all chunks: %d (%.1f%%)%n",
                validQuestions, (validQuestions * 100.0 / totalQuestions));
        System.out.printf("Missing chunks:            %d%n", missingChunks);
        System.out.println();

        if (missingChunks > 0) {
            System.out.println(" WARNING: Some chunks are missing from the vector store!");
            System.out.println("   This could happen if documents were re-ingested with different chunk IDs.");
        } else {
            System.out.println("All chunks found in vector store!");
            System.out.println("Now manually review above output:");
            System.out.println("  - Does each chunk actually answer its question?");
            System.out.println("  - Are the questions clear and realistic?");
            System.out.println("  - Are there edge cases missing?");
        }
        System.out.println();

        return new ValidationReport(totalQuestions, validQuestions, missingChunks);
    }

    /**
     * Validate a single question - useful for debugging
     */
    public void validateQuestion(String question, Set<String> expectedChunkIds) {
        System.out.println("\n=== VALIDATING SINGLE QUESTION ===");
        System.out.println("Question: " + question);
        System.out.println();

        for (String chunkId : expectedChunkIds) {
            ChunkContent chunk = getChunkById(chunkId);
            if (chunk != null) {
                System.out.println("Chunk ID: " + chunkId);
                System.out.println("Content:");
                System.out.println(chunk.content());
                System.out.println();
            } else {
                System.out.println("⚠ Chunk " + chunkId + " not found!");
            }
        }
    }

    /**
     * Retrieve chunk content by ID from vector store
     */
    private ChunkContent getChunkById(String chunkId) {
        String sql = """
            SELECT id, content, metadata
            FROM vector_store
            WHERE id = :chunkId::uuid
            """;

        List<ChunkContent> results = jdbcClient.sql(sql)
                .param("chunkId", chunkId)
                .query((rs, rowNum) -> new ChunkContent(
                        rs.getString("id"),
                        rs.getString("content"),
                        rs.getString("metadata")
                ))
                .list();

        return results.isEmpty() ? null : results.get(0);
    }

    /**
     * Word-wrap text for better console output
     */
    private String wrapText(String text, int maxWidth, String prefix) {
        StringBuilder wrapped = new StringBuilder();
        String[] words = text.split("\\s+");
        int lineLength = 0;

        for (String word : words) {
            if (lineLength + word.length() + 1 > maxWidth) {
                wrapped.append("\n").append(prefix);
                lineLength = 0;
            }
            if (lineLength > 0) {
                wrapped.append(" ");
                lineLength++;
            }
            wrapped.append(word);
            lineLength += word.length();
        }

        return wrapped.toString();
    }

    // ========== Data Classes ==========

    public record ChunkContent(
            String id,
            String content,
            String metadata
    ) {}

    public record ValidationReport(
            int totalQuestions,
            int validQuestions,
            int missingChunks
    ) {}
}