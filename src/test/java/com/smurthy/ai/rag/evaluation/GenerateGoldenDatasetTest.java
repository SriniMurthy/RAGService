package com.smurthy.ai.rag.evaluation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Generate golden test dataset from actual documents.
 *
 * USAGE:
 * 1. Ensure the vector store has documents ingested
 * 2. Run this test: ./mvnw test -Dtest=GenerateGoldenDatasetTest
 * 3. Review generated file: src/test/resources/golden-dataset.json
 * 4. Manually review and refine questions if needed
 * 5. Use this dataset for RAG quality testing
 *
 * OUTPUT:
 * - JSON file with questions and their relevant chunk IDs
 * - Each question tagged with type (factual/conceptual/detail)
 * - Metadata about dataset diversity
 */
@SpringBootTest
public class GenerateGoldenDatasetTest {

    @Autowired
    private TestDataGenerator testDataGenerator;

    @Autowired
    private GoldenDatasetValidator validator;

    @Test
    public void generateGoldenDataset() {
        System.out.println("\n╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║        GENERATING GOLDEN DATASET FOR RAG TESTING              ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝\n");

        // Generate 20 test questions from actual documents
        TestDataGenerator.GoldenDataset dataset = testDataGenerator.generateTestDataset(20);

        // Save to file
        String outputPath = "src/test/resources/golden-dataset.json";
        testDataGenerator.saveToFile(dataset, outputPath);

        // Print summary
        System.out.println("\n┌───────────────────────────────────────────────────────────────┐");
        System.out.println("│ DATASET SUMMARY                                               │");
        System.out.println("├───────────────────────────────────────────────────────────────┤");
        System.out.printf("│ Total Questions:    %-42d │%n", dataset.questions().size());
        System.out.printf("│ Unique Files:       %-42d │%n", dataset.metadata().get("unique_files"));
        System.out.printf("│ Question Types:     %-42d │%n", dataset.metadata().get("question_types"));
        System.out.printf("│ Output File:        %-42s │%n", outputPath);
        System.out.println("└───────────────────────────────────────────────────────────────┘");

        // Print sample questions
        System.out.println("\n┌───────────────────────────────────────────────────────────────┐");
        System.out.println("│ SAMPLE QUESTIONS (First 5)                                    │");
        System.out.println("└───────────────────────────────────────────────────────────────┘\n");

        dataset.questions().stream()
                .limit(5)
                .forEach(q -> {
                    System.out.println("Type:     " + q.type());
                    System.out.println("Question: " + q.question());
                    System.out.println("Source:   " + q.sourceFile());
                    System.out.println("Chunks:   " + q.relevantChunkIds().size() + " relevant");
                    System.out.println();
                });

        System.out.println("✓ Golden dataset generated successfully!");
        System.out.println("✓ Next step: Review the questions in " + outputPath);
        System.out.println("✓ Then run: ./mvnw test -Dtest=RAGQualityEvaluationTest\n");
    }

    /**
     * Load and inspect an existing dataset
     */
    @Test
    public void inspectGoldenDataset() {
        String datasetPath = "src/test/resources/golden-dataset.json";

        try {
            TestDataGenerator.GoldenDataset dataset = testDataGenerator.loadFromFile(datasetPath);

            System.out.println("\n╔═══════════════════════════════════════════════════════════════╗");
            System.out.println("║          INSPECTING EXISTING GOLDEN DATASET                    ║");
            System.out.println("╚═══════════════════════════════════════════════════════════════╝\n");

            System.out.println("Dataset generated: " + dataset.generatedAt());
            System.out.println("Total questions:   " + dataset.questions().size());

            // Group by type
            var byType = dataset.questions().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            TestDataGenerator.GeneratedQuestion::type,
                            java.util.stream.Collectors.counting()
                    ));

            System.out.println("\nQuestions by type:");
            byType.forEach((type, count) -> System.out.printf("  - %s: %d%n", type, count));

            // Group by source file
            var byFile = dataset.questions().stream()
                    .collect(java.util.stream.Collectors.groupingBy(
                            TestDataGenerator.GeneratedQuestion::sourceFile,
                            java.util.stream.Collectors.counting()
                    ));

            System.out.println("\nQuestions by source file:");
            byFile.forEach((file, count) -> System.out.printf("  - %s: %d%n", file, count));

        } catch (Exception e) {
            System.err.println("No dataset found at " + datasetPath);
            System.err.println("Run generateGoldenDataset() first!");
        }
    }

    /**
     * CROSS-CHECK: Validate that questions actually match their chunks
     *
     * This is THE KEY STEP - it shows  each question alongside the chunk
     * that's supposed to answer it, so it  can manually be verified for correctness.
     */
    @Test
    public void validateGoldenDataset() {
        String datasetPath = "src/test/resources/golden-dataset.json";

        System.out.println("\n");
        System.out.println("This will display each question with its source chunk content.");
        System.out.println("Manually review to ensure questions are answerable from the chunks.\n");

        validator.validateDataset(datasetPath);

        System.out.println("\nMANUAL REVIEW CHECKLIST:");
        System.out.println("  [ ] Does each chunk actually answer its question?");
        System.out.println("  [ ] Are questions realistic (would a user ask this)?");
        System.out.println("  [ ] Are there any obvious wrong mappings?");
        System.out.println("  [ ] Should any questions be removed or refined?");
        System.out.println();
    }
}