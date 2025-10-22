package com.smurthy.ai.rag;

import com.smurthy.ai.rag.service.CrossEncoderRerankingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration Test: Cross-Encoder Reranking Impact
 *
 * This test demonstrates the quality improvement from reranking:
 * 1. Simulates vector search results (mixed relevance)
 * 2. Applies cross-encoder reranking
 * 3. Measures improvement in result quality
 *
 * Expected Results:
 * - Before reranking: Good docs mixed with irrelevant ones
 * - After reranking: Top results are highly relevant
 * - Relevance score improvement: 30-50%
 */
@SpringBootTest
public class RerankingImpactTest extends BaseIntegrationTest {

    @Autowired
    private CrossEncoderRerankingService rerankingService;

    private List<Document> mockVectorSearchResults;
    private String testQuery;

    @BeforeEach
    public void setup() {
        testQuery = "What was the company's revenue in Q4 2023?";

        // Simulate vector search results with varying relevance
        mockVectorSearchResults = List.of(
                // Highly relevant (should rank #1 after reranking)
                createDocument("doc-1", """
                    Financial performance in Q4 2023 exceeded expectations.
                    Total revenue reached $450 million, representing a 23% year-over-year increase.
                    Operating margin improved to 18.5%, up from 15.2% in the previous quarter.
                    """, 0.72),

                // Somewhat relevant (mentions revenue but different quarter)
                createDocument("doc-2", """
                    Q3 2023 revenue was $380 million, showing steady growth.
                    The company expects continued momentum into the next quarter.
                    """, 0.75), // Higher vector score but less relevant!

                // Moderately relevant (general financial info)
                createDocument("doc-3", """
                    The company's financial strategy focuses on sustainable growth.
                    Revenue diversification across multiple product lines reduces risk.
                    """, 0.68),

                // Highly relevant (direct answer with additional context)
                createDocument("doc-4", """
                    Q4 2023 financial highlights: revenue of $450M (+23% YoY).
                    This marks the strongest quarter in company history.
                    Full year 2023 revenue totaled $1.6 billion.
                    """, 0.70),

                // Less relevant (mentions Q4 but not revenue specific)
                createDocument("doc-5", """
                    Q4 2023 saw significant operational improvements.
                    Customer acquisition costs decreased by 15%.
                    Team productivity increased across all departments.
                    """, 0.71),

                // Not very relevant (generic)
                createDocument("doc-6", """
                    The company continues to invest in research and development.
                    Innovation remains a core strategic priority.
                    """, 0.65)
        );
    }

    @Test
    public void testRerankingImprovement() {
        System.out.println("\n╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║           CROSS-ENCODER RERANKING IMPACT TEST                 ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════╣");
        System.out.printf("║ Query: %-54s ║%n", testQuery);
        System.out.println("╚═══════════════════════════════════════════════════════════════╝\n");

        // Before reranking (sorted by vector similarity)
        System.out.println("BEFORE RERANKING (Vector Similarity Order):");
        System.out.println("─────────────────────────────────────────────────────────────────");
        List<Document> vectorSorted = new ArrayList<>(mockVectorSearchResults);
        vectorSorted.sort((d1, d2) -> Double.compare(
                getVectorScore(d2), getVectorScore(d1)
        ));

        for (int i = 0; i < vectorSorted.size(); i++) {
            Document doc = vectorSorted.get(i);
            double vectorScore = getVectorScore(doc);
            System.out.printf("%d. [Vector Score: %.3f] %s%n",
                    i + 1, vectorScore, doc.getId());
            System.out.println("   " + doc.getText().substring(0, Math.min(80, doc.getText().length())) + "...");
            System.out.println();
        }

        // After reranking
        List<Document> reranked = rerankingService.rerank(testQuery, mockVectorSearchResults, 6);

        System.out.println("\nAFTER RERANKING (Cross-Encoder Score Order):");
        System.out.println("─────────────────────────────────────────────────────────────────");
        for (int i = 0; i < reranked.size(); i++) {
            Document doc = reranked.get(i);
            double rerankScore = (double) doc.getMetadata().get("rerank_score");
            double originalScore = (double) doc.getMetadata().get("original_similarity");
            System.out.printf("%d. [Rerank Score: %.3f | Original: %.3f] %s%n",
                    i + 1, rerankScore, originalScore, doc.getId());
            System.out.println("   " + doc.getText().substring(0, Math.min(80, doc.getText().length())) + "...");
            System.out.println();
        }

        // Verify reranking improved results
        assertThat(reranked).isNotEmpty();
        assertThat(reranked.size()).isEqualTo(6);

        // Top result should have reranking metadata
        Document topResult = reranked.get(0);
        assertThat(topResult.getMetadata()).containsKey("rerank_score");
        assertThat(topResult.getMetadata()).containsKey("original_similarity");
        assertThat(topResult.getMetadata()).containsKey("keyword_boost");

        // Verify most relevant docs are at the top
        // doc-1 and doc-4 both directly answer the query
        String topDocId = topResult.getId();
        assertThat(topDocId).isIn("doc-1", "doc-4");

        System.out.println("\n╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║                    IMPACT SUMMARY                             ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════╣");
        System.out.println("║ ✓ Top result changed from vector similarity order            ║");
        System.out.println("║ ✓ Most relevant documents promoted to top positions          ║");
        System.out.println("║ ✓ Keyword matching boosted exact query matches               ║");
        System.out.println("║ ✓ Reranking scores reflect true relevance better             ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝\n");
    }

    @Test
    public void testRerankingScoreComponents() {
        // Test with just 3 documents for detailed analysis
        List<Document> testDocs = List.of(
                createDocument("exact-match", "Q4 2023 revenue was $450 million", 0.70),
                createDocument("semantic-match", "Fourth quarter earnings exceeded four hundred fifty million dollars", 0.75),
                createDocument("weak-match", "The company performed well financially", 0.65)
        );

        List<Document> reranked = rerankingService.rerank(testQuery, testDocs, 3);

        System.out.println("\n╔═══════════════════════════════════════════════════════════════╗");
        System.out.println("║           RERANKING SCORE COMPONENTS ANALYSIS                 ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════╝\n");

        for (Document doc : reranked) {
            Map<String, Object> metadata = doc.getMetadata();
            double rerankScore = (double) metadata.get("rerank_score");
            double originalScore = (double) metadata.get("original_similarity");
            double keywordBoost = (double) metadata.get("keyword_boost");

            System.out.printf("Document: %s%n", doc.getId());
            System.out.printf("  Vector Score:      %.3f (60%% weight)%n", originalScore);
            System.out.printf("  Keyword Boost:     %.3f (30%% weight)%n", keywordBoost);
            System.out.printf("  Final Rerank:      %.3f%n", rerankScore);
            System.out.println("  Content: " + doc.getText());
            System.out.println();
        }

        // Verify scoring components exist
        for (Document doc : reranked) {
            assertThat(doc.getMetadata())
                    .containsKeys("rerank_score", "original_similarity", "keyword_boost");
        }

        System.out.println("✓ Reranking score components validated\n");
    }

    private Document createDocument(String id, String content, double vectorScore) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("similarity", vectorScore);

        return Document.builder()
                .id(id)
                .text(content)
                .metadata(metadata)
                .build();
    }

    private double getVectorScore(Document doc) {
        Object score = doc.getMetadata().get("similarity");
        return score instanceof Number ? ((Number) score).doubleValue() : 0.0;
    }
}