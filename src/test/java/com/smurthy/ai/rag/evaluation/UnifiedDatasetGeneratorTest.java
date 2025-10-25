package com.smurthy.ai.rag.evaluation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Unified Dataset Generator - Demonstrates both RAFT and Evaluation modes
 *
 * TWO USE CASES:
 *
 * 1. RAFT MODE - For Fine-tuning LLMs
 *    - Generates training data to improve model performance
 *    - Output: question + golden doc + distractors
 *    - Use when: You want to fine-tune a model on your domain
 *
 * 2. EVALUATION MODE - For RAG Quality Testing
 *    - Generates test questions to measure retrieval quality
 *    - Output: question + chunk IDs + question types
 *    - Use when: You want to test chunking/retrieval strategies
 *
 * USAGE:
 *   ./mvnw test -Dtest=UnifiedDatasetGeneratorTest#generateRAFTDataset
 *   ./mvnw test -Dtest=UnifiedDatasetGeneratorTest#generateEvaluationDataset
 */
@SpringBootTest
public class UnifiedDatasetGeneratorTest {

    @Autowired
    private UnifiedDatasetGenerator generator;

    @Autowired
    private GoldenDatasetValidator validator;

    // ========================================================================
    // MODE 1: RAFT DATASET (For Model Training)
    // ========================================================================

    @Test
    public void generateRAFTDataset() {
        System.out.println("\n╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║       MODE 1: RAFT DATASET (Fine-tuning Training Data)        ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝\n");

        System.out.println("Purpose: Create training data to fine-tune LLMs on your domain");
        System.out.println("Output:  question + golden_document + distractor_documents\n");

        // Generate RAFT dataset for a specific category
        String category = "resume"; // Change to your category
        int numSamples = 20;

        UnifiedDatasetGenerator.RAFTDataset dataset =
                generator.generateRAFTDataset(category, numSamples);

        // Save to file
        String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        String outputPath = String.format("src/test/resources/raft_dataset_%s_%s.json", category, timestamp);
        generator.saveToFile(dataset, outputPath);

        // Print summary
        System.out.println("\n┌───────────────────────────────────────────────────────────────┐");
        System.out.println("│ RAFT DATASET SUMMARY                                          │");
        System.out.println("├───────────────────────────────────────────────────────────────┤");
        System.out.printf("│ Category:        %-44s │%n", dataset.category());
        System.out.printf("│ Data Points:     %-44d │%n", dataset.dataPoints().size());
        System.out.printf("│ Output File:     %-44s │%n", outputPath);
        System.out.println("└───────────────────────────────────────────────────────────────┘");

        // Print sample
        System.out.println("\n┌───────────────────────────────────────────────────────────────┐");
        System.out.println("│ SAMPLE DATA POINT                                             │");
        System.out.println("└───────────────────────────────────────────────────────────────┘\n");

        if (!dataset.dataPoints().isEmpty()) {
            UnifiedDatasetGenerator.RAFTDataPoint sample = dataset.dataPoints().get(0);
            System.out.println("Question:");
            System.out.println("  " + sample.question());
            System.out.println();
            System.out.println("Golden Document:");
            System.out.println("  " + truncate(sample.golden_document(), 200));
            System.out.println();
            System.out.println("Distractor Documents: " + sample.distractor_documents().size());
            System.out.println();
        }

        System.out.println("✓ RAFT dataset generated!");
        System.out.println("✓ Use this for fine-tuning your LLM");
        System.out.println("✓ Next: Annotate 'ideal_answer' fields for training\n");
    }

    // ========================================================================
    // MODE 2: EVALUATION DATASET (For RAG Testing)
    // ========================================================================

    @Test
    public void generateEvaluationDataset() {
        System.out.println("\n╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║     MODE 2: EVALUATION DATASET (RAG Quality Testing)          ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝\n");

        System.out.println("Purpose: Test chunking and retrieval strategies");
        System.out.println("Output:  question + chunk IDs + question types\n");

        // Generate evaluation dataset
        int numQuestions = 20;

        UnifiedDatasetGenerator.EvaluationDataset dataset =
                generator.generateEvaluationDataset(numQuestions);

        // Save to file
        String outputPath = "src/test/resources/evaluation-dataset.json";
        generator.saveToFile(dataset, outputPath);

        // Print summary
        System.out.println("\n┌───────────────────────────────────────────────────────────────┐");
        System.out.println("│ EVALUATION DATASET SUMMARY                                    │");
        System.out.println("├───────────────────────────────────────────────────────────────┤");
        System.out.printf("│ Total Questions:  %-43d │%n", dataset.questions().size());
        System.out.printf("│ Unique Files:     %-43d │%n", dataset.metadata().get("unique_files"));
        System.out.printf("│ Output File:      %-43s │%n", outputPath);
        System.out.println("└───────────────────────────────────────────────────────────────┘");

        // Group by type
        var byType = dataset.questions().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        UnifiedDatasetGenerator.EvaluationQuestion::type,
                        java.util.stream.Collectors.counting()
                ));

        System.out.println("\nQuestions by type:");
        byType.forEach((type, count) -> System.out.printf("  - %s: %d%n", type, count));

        // Print samples
        System.out.println("\n┌───────────────────────────────────────────────────────────────┐");
        System.out.println("│ SAMPLE QUESTIONS (First 3)                                    │");
        System.out.println("└───────────────────────────────────────────────────────────────┘\n");

        dataset.questions().stream()
                .limit(3)
                .forEach(q -> {
                    System.out.println("Type:     " + q.type());
                    System.out.println("Question: " + q.question());
                    System.out.println("Source:   " + q.sourceFile());
                    System.out.println("Chunks:   " + q.relevantChunkIds().size() + " relevant");
                    System.out.println();
                });

        System.out.println("✓ Evaluation dataset generated!");
        System.out.println("✓ Next: Run validateEvaluationDataset() to cross-check\n");
    }

    /**
     * VALIDATE EVALUATION DATASET - Cross-check questions match their chunks
     */
    @Test
    public void validateEvaluationDataset() {
        System.out.println("\n╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║         VALIDATING EVALUATION DATASET                         ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝\n");

        String datasetPath = "src/test/resources/evaluation-dataset.json";

        System.out.println("This will display each question with its source chunk content.");
        System.out.println("Manually review to ensure questions are answerable from chunks.\n");

        // Load and convert to GoldenDataset format for validation
        UnifiedDatasetGenerator.EvaluationDataset evalDataset =
                generator.loadFromFile(datasetPath, UnifiedDatasetGenerator.EvaluationDataset.class);

        // Convert to TestDataGenerator.GoldenDataset for validator
        List<TestDataGenerator.GeneratedQuestion> questions = evalDataset.questions().stream()
                .map(eq -> new TestDataGenerator.GeneratedQuestion(
                        eq.question(),
                        eq.relevantChunkIds(),
                        eq.type(),
                        eq.sourceFile()
                ))
                .toList();

        TestDataGenerator.GoldenDataset goldenDataset = new TestDataGenerator.GoldenDataset(
                questions,
                evalDataset.generatedAt(),
                evalDataset.metadata()
        );

        // Save as golden-dataset.json for validator
        String goldenPath = "src/test/resources/golden-dataset.json";
        generator.saveToFile(goldenDataset, goldenPath);

        // Now validate using existing validator
        validator.validateDataset(goldenPath);

        System.out.println("\nMANUAL REVIEW CHECKLIST:");
        System.out.println("  [ ] Does each chunk actually answer its question?");
        System.out.println("  [ ] Are questions realistic (would a user ask this)?");
        System.out.println("  [ ] Are there any obvious wrong mappings?");
        System.out.println("  [ ] Should any questions be removed or refined?");
        System.out.println();
    }

    // ========================================================================
    // COMPARISON: When to Use Each Mode
    // ========================================================================

    @Test
    public void explainUseCases() {
        System.out.println("\n╔═══════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║              RAFT vs EVALUATION - WHEN TO USE EACH                        ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════════════╝\n");

        System.out.println("┌───────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│ MODE 1: RAFT DATASET (Training)                                           │");
        System.out.println("├───────────────────────────────────────────────────────────────────────────┤");
        System.out.println("│                                                                           │");
        System.out.println("│ Use when:                                                                 │");
        System.out.println("│  - You want to fine-tune an LLM on your specific domain                  │");
        System.out.println("│  - You have a lot of documents and want better answers                   │");
        System.out.println("│  - You're willing to annotate 'ideal_answer' for training                │");
        System.out.println("│                                                                           │");
        System.out.println("│ Output format:                                                            │");
        System.out.println("│  {                                                                        │");
        System.out.println("│    \"question\": \"What is the Q4 revenue?\",                               │");
        System.out.println("│    \"golden_document\": \"Q4 revenue was $10M...\",                         │");
        System.out.println("│    \"distractor_documents\": [\"Q3 had...\", \"Q2 saw...\"],                 │");
        System.out.println("│    \"ideal_answer\": \"\"  // You annotate this                             │");
        System.out.println("│  }                                                                        │");
        System.out.println("│                                                                           │");
        System.out.println("│ Next steps:                                                               │");
        System.out.println("│  1. Generate dataset with generateRAFTDataset()                           │");
        System.out.println("│  2. Manually annotate 'ideal_answer' fields                               │");
        System.out.println("│  3. Fine-tune your LLM (e.g., GPT-3.5, Llama, etc.)                      │");
        System.out.println("│  4. Deploy fine-tuned model                                               │");
        System.out.println("│                                                                           │");
        System.out.println("└───────────────────────────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("┌───────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│ MODE 2: EVALUATION DATASET (Testing)                                      │");
        System.out.println("├───────────────────────────────────────────────────────────────────────────┤");
        System.out.println("│                                                                           │");
        System.out.println("│ Use when:                                                                 │");
        System.out.println("│  - You want to test different chunking strategies                        │");
        System.out.println("│  - You want to measure retrieval quality (precision/recall)              │");
        System.out.println("│  - You want to compare BM25 vs Vector vs Hybrid retrieval                │");
        System.out.println("│  - You want to optimize similarity thresholds                            │");
        System.out.println("│                                                                           │");
        System.out.println("│ Output format:                                                            │");
        System.out.println("│  {                                                                        │");
        System.out.println("│    \"question\": \"What companies did X work for?\",                        │");
        System.out.println("│    \"relevantChunkIds\": [\"uuid-123\"],                                    │");
        System.out.println("│    \"type\": \"factual\",                                                   │");
        System.out.println("│    \"sourceFile\": \"resume.pdf\"                                           │");
        System.out.println("│  }                                                                        │");
        System.out.println("│                                                                           │");
        System.out.println("│ Next steps:                                                               │");
        System.out.println("│  1. Generate dataset with generateEvaluationDataset()                     │");
        System.out.println("│  2. Validate with validateEvaluationDataset()                             │");
        System.out.println("│  3. Run RAG experiments (next: #2 Chunking Strategy Tests)               │");
        System.out.println("│  4. Measure precision/recall (next: #1 Metrics Framework)                │");
        System.out.println("│                                                                           │");
        System.out.println("└───────────────────────────────────────────────────────────────────────────┘");
        System.out.println();

        System.out.println("┌───────────────────────────────────────────────────────────────────────────┐");
        System.out.println("│ CAN YOU USE BOTH?                                                         │");
        System.out.println("├───────────────────────────────────────────────────────────────────────────┤");
        System.out.println("│                                                                           │");
        System.out.println("│ YES! Common workflow:                                                     │");
        System.out.println("│                                                                           │");
        System.out.println("│  1. Use EVALUATION mode to test chunking strategies                      │");
        System.out.println("│  2. Find optimal chunk size/overlap/retrieval method                     │");
        System.out.println("│  3. Re-ingest documents with optimal settings                            │");
        System.out.println("│  4. Use RAFT mode to generate fine-tuning data                           │");
        System.out.println("│  5. Fine-tune your LLM on this domain-specific data                      │");
        System.out.println("│  6. Deploy fine-tuned model with optimized RAG pipeline                  │");
        System.out.println("│                                                                           │");
        System.out.println("│ Result: Best of both worlds - optimized retrieval + domain-tuned model   │");
        System.out.println("│                                                                           │");
        System.out.println("└───────────────────────────────────────────────────────────────────────────┘");
        System.out.println();
    }

    private String truncate(String text, int maxLength) {
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }
}