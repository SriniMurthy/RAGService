package com.smurthy.ai.rag;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the four RAG identity-confusion fixes:
 *
 *   Fix 1 – RAGFunctionConfiguration now tries "file_name" first in extractMetadata(),
 *            so sourceFile is never "Unknown Source" for properly ingested files.
 *
 *   Fix 2 – DocumentIngestionService stamps every chunk with a
 *            "[DOCUMENT: file | PERSON: name | CATEGORY: cat]" header before embedding,
 *            so the LLM always knows which document each chunk belongs to.
 *
 *   Fix 3 – person_name is extracted from the filename and stored as metadata,
 *            enabling per-person filtering and attribution.
 *
 *   Fix 4 – ResearchAgent system prompt enforces per-source attribution and
 *            explicitly forbids merging chunks from different source documents.
 *            (Tested indirectly: chunk-level identity is a precondition for Fix 4.)
 *
 * Scenario: Alice Smith and Bob Jones both worked at "Acme Corp" in overlapping
 * periods. Without the fixes the system would blend their profiles. With the fixes
 * every chunk is stamped with its owner, and the LLM prompt instructs strict separation.
 *
 * Requires: running pgvector container and OPENAI_API_KEY env var (for embeddings).
 */
public class ResumeIdentityIntegrationTest extends BaseIntegrationTest {

    // ------------------------------------------------------------------ fixtures

    private static final String ALICE_RESUME = """
            ALICE SMITH - Senior Software Engineer
            alice.smith@email.com | San Francisco, CA

            EXPERIENCE:
            Acme Corp - Software Engineer (2018-2020)
            Led backend development for payment processing system.
            Technologies: Java, Spring Boot, PostgreSQL.

            TechCorp - Senior Engineer (2020-2022)
            Architected microservices platform handling 10M requests/day.
            Technologies: Kubernetes, Go, gRPC, Redis.
            """;

    private static final String BOB_RESUME = """
            BOB JONES - Data Science Manager
            bob.jones@email.com | New York, NY

            EXPERIENCE:
            Acme Corp - Data Scientist (2019-2021)
            Built ML models for fraud detection achieving 98% precision.
            Technologies: Python, TensorFlow, Apache Spark.

            DataVentures - Machine Learning Lead (2021-2023)
            Delivered real-time recommendation engine for 5M users.
            Technologies: PyTorch, Kafka, AWS SageMaker.
            """;

    // Filenames are parsed by extractPersonName() → "Alice Smith" and "Bob Jones"
    private static final Document ALICE_DOC = new Document(
        ALICE_RESUME,
        Map.of("source_filename", "alice_smith_resume.txt", "test_fixture", "true")
    );

    private static final Document BOB_DOC = new Document(
        BOB_RESUME,
        Map.of("source_filename", "bob_jones_resume.txt", "test_fixture", "true")
    );

    // ------------------------------------------------------------------ cleanup

    @AfterEach
    void cleanup() {
        deleteAllChunksForFile("alice_smith_resume.txt");
        deleteAllChunksForFile("bob_jones_resume.txt");
    }

    /**
     * Deletes all vector store chunks whose file_name or source metadata matches fileName.
     * BaseIntegrationTest.deleteTestDocument() only deletes topK(1), which leaves stale
     * chunks and prevents re-ingestion on the next test run (the duplicate-check queries COUNT).
     */
    private void deleteAllChunksForFile(String fileName) {
        List<Document> all = vectorStore.similaritySearch(
            SearchRequest.builder().query(fileName).topK(100).build()
        );
        List<String> ids = all.stream()
            .filter(d -> fileName.equals(d.getMetadata().get("file_name"))
                      || fileName.equals(d.getMetadata().get("source")))
            .map(Document::getId)
            .toList();
        if (!ids.isEmpty()) {
            vectorStore.delete(ids);
        }
    }

    // ------------------------------------------------------------------ Fix 1: file_name metadata key

    @Test
    void fix1_ingested_chunk_carries_file_name_metadata() throws IOException {
        addTestDocument(ALICE_DOC);

        List<Document> results = vectorStore.similaritySearch(
            SearchRequest.builder().query("payment processing Java Spring Boot engineer").topK(20).build()
        );

        List<Document> aliceChunks = results.stream()
            .filter(d -> "alice_smith_resume.txt".equals(d.getMetadata().get("file_name")))
            .toList();

        assertThat(aliceChunks)
            .as("Fix 1: at least one chunk must carry file_name = alice_smith_resume.txt")
            .isNotEmpty();
    }

    @Test
    void fix1_ingested_chunk_carries_source_metadata() throws IOException {
        addTestDocument(ALICE_DOC);

        List<Document> results = vectorStore.similaritySearch(
            SearchRequest.builder().query("payment processing Java Spring Boot engineer").topK(20).build()
        );

        List<Document> aliceChunks = results.stream()
            .filter(d -> "alice_smith_resume.txt".equals(d.getMetadata().get("source")))
            .toList();

        assertThat(aliceChunks)
            .as("Fix 1: at least one chunk must carry source = alice_smith_resume.txt (both keys written)")
            .isNotEmpty();
    }

    // ------------------------------------------------------------------ Fix 2: identity header in chunk text

    @Test
    void fix2_every_chunk_starts_with_document_header() throws IOException {
        addTestDocument(ALICE_DOC);

        // Wait until alice-specific chunks (with headers) appear
        Awaitility.await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(2))
            .until(() -> vectorStore.similaritySearch(
                SearchRequest.builder().query("Alice Smith Senior Software Engineer").topK(10).build()
            ).stream().anyMatch(d -> "alice_smith_resume.txt".equals(d.getMetadata().get("file_name"))));

        List<Document> results = vectorStore.similaritySearch(
            SearchRequest.builder().query("Alice Smith Senior Software Engineer microservices").topK(20).build()
        );

        List<Document> aliceChunks = results.stream()
            .filter(d -> "alice_smith_resume.txt".equals(d.getMetadata().get("file_name")))
            .toList();

        assertThat(aliceChunks).isNotEmpty();
        aliceChunks.forEach(chunk -> {
            assertThat(chunk.getText())
                .as("Fix 2: chunk text must begin with [DOCUMENT:] header, got: %s",
                    chunk.getText().substring(0, Math.min(80, chunk.getText().length())))
                .startsWith("[DOCUMENT:");
            assertThat(chunk.getText())
                .as("Fix 2: [DOCUMENT:] header must name the source file")
                .contains("alice_smith_resume.txt");
        });
    }

    @Test
    void fix2_overlapping_employer_chunks_have_distinct_non_mixed_headers() throws IOException {
        addTestDocument(ALICE_DOC);
        addTestDocument(BOB_DOC);

        // Both Alice and Bob worked at Acme Corp — the core ambiguity scenario
        List<Document> acmeResults = vectorStore.similaritySearch(
            SearchRequest.builder().query("Acme Corp engineer scientist").topK(30).build()
        );

        List<Document> acmeChunks = acmeResults.stream()
            .filter(d -> {
                String text = d.getText();
                return text != null && text.contains("Acme Corp");
            })
            .toList();

        assertThat(acmeChunks)
            .as("Fix 2: Acme Corp chunks must exist for both people")
            .isNotEmpty();

        acmeChunks.forEach(chunk -> {
            String text = chunk.getText();
            boolean isAlice = text.contains("alice_smith_resume.txt");
            boolean isBob   = text.contains("bob_jones_resume.txt");

            assertThat(isAlice || isBob)
                .as("Fix 2: Acme Corp chunk must be attributed to Alice or Bob, not unknown.\nChunk: %s",
                    text.substring(0, Math.min(120, text.length())))
                .isTrue();

            assertThat(isAlice && isBob)
                .as("Fix 2: A single chunk must NOT reference both people's files — cross-contamination detected.\nChunk: %s",
                    text.substring(0, Math.min(120, text.length())))
                .isFalse();
        });
    }

    // ------------------------------------------------------------------ Fix 3: person_name metadata

    @Test
    void fix3_alice_chunks_have_person_name_metadata() throws IOException {
        addTestDocument(ALICE_DOC);

        Awaitility.await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(2))
            .until(() -> vectorStore.similaritySearch(
                SearchRequest.builder().query("Alice Smith engineer TechCorp").topK(10).build()
            ).stream().anyMatch(d -> "Alice Smith".equals(d.getMetadata().get("person_name"))));

        List<Document> results = vectorStore.similaritySearch(
            SearchRequest.builder().query("Alice Smith engineer TechCorp").topK(20).build()
        );

        boolean found = results.stream()
            .anyMatch(d -> "Alice Smith".equals(d.getMetadata().get("person_name")));

        assertThat(found)
            .as("Fix 3: Alice's chunks must have person_name = 'Alice Smith' derived from filename")
            .isTrue();
    }

    @Test
    void fix3_bob_chunks_have_person_name_metadata() throws IOException {
        addTestDocument(BOB_DOC);

        Awaitility.await()
            .atMost(Duration.ofSeconds(20))
            .pollInterval(Duration.ofSeconds(2))
            .until(() -> vectorStore.similaritySearch(
                SearchRequest.builder().query("Bob Jones fraud detection DataVentures").topK(10).build()
            ).stream().anyMatch(d -> "Bob Jones".equals(d.getMetadata().get("person_name"))));

        List<Document> results = vectorStore.similaritySearch(
            SearchRequest.builder().query("Bob Jones fraud detection DataVentures").topK(20).build()
        );

        boolean found = results.stream()
            .anyMatch(d -> "Bob Jones".equals(d.getMetadata().get("person_name")));

        assertThat(found)
            .as("Fix 3: Bob's chunks must have person_name = 'Bob Jones' derived from filename")
            .isTrue();
    }

    @Test
    void fix3_both_people_have_distinct_person_name_values() throws IOException {
        addTestDocument(ALICE_DOC);
        addTestDocument(BOB_DOC);

        List<Document> allResults = vectorStore.similaritySearch(
            SearchRequest.builder().query("Acme Corp engineer scientist resume").topK(30).build()
        );

        boolean alicePresent = allResults.stream()
            .anyMatch(d -> "Alice Smith".equals(d.getMetadata().get("person_name")));
        boolean bobPresent = allResults.stream()
            .anyMatch(d -> "Bob Jones".equals(d.getMetadata().get("person_name")));

        assertThat(alicePresent)
            .as("Fix 3: Alice Smith should appear with person_name metadata in Acme Corp results")
            .isTrue();
        assertThat(bobPresent)
            .as("Fix 3: Bob Jones should appear with person_name metadata in Acme Corp results")
            .isTrue();
    }

    // ------------------------------------------------------------------ Fix 1 end-to-end: no Unknown Source

    @Test
    void fix1_file_name_is_never_blank_after_ingestion() throws IOException {
        addTestDocument(ALICE_DOC);
        addTestDocument(BOB_DOC);

        List<Document> results = vectorStore.similaritySearch(
            SearchRequest.builder().query("engineer data scientist Acme Corp resume").topK(30).build()
        );

        // Every test chunk (both resumes) must have a non-blank file_name
        // because extractMetadata() now tries "file_name" first (Fix 1)
        List<Document> testChunks = results.stream()
            .filter(d -> "true".equals(d.getMetadata().get("test_fixture"))
                      || "alice_smith_resume.txt".equals(d.getMetadata().get("file_name"))
                      || "bob_jones_resume.txt".equals(d.getMetadata().get("file_name")))
            .toList();

        assertThat(testChunks).isNotEmpty();
        testChunks.forEach(chunk -> {
            Object fileNameMeta = chunk.getMetadata().get("file_name");
            assertThat(fileNameMeta)
                .as("Fix 1: file_name metadata must not be null — RAGFunctionConfiguration.extractMetadata() "
                    + "will return 'Unknown Source' if this key is absent")
                .isNotNull();
            assertThat(fileNameMeta.toString())
                .as("Fix 1: file_name metadata must not be blank")
                .isNotBlank();
        });
    }
}
