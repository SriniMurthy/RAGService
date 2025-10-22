package com.smurthy.ai.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * A service dedicated to performing temporal queries against the VectorStore.
 */
@Service
public class TemporalQueryService {

    private static final Logger log = LoggerFactory.getLogger(TemporalQueryService.class);
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingModel embeddingModel;

    public TemporalQueryService(VectorStore vectorStore, JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        this.vectorStore = vectorStore;
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Finds documents that are semantically similar to the query and fall within a specific year.
     */
    public List<Document> findDocumentsByYear(String query, int year) {
        // The filter expression syntax does not require the "metadata." prefix.
        String filterExpression = String.format("start_date >= '%d-01-01' AND end_date <= '%d-12-31'", year, year);
        return executeTemporalQuery(query, filterExpression);
    }

    /**
     * Finds documents that are semantically similar to the query and fall within a given date range.
     * This acts as a logical BETWEEN operator for the document's date range.
     */
    public List<Document> findDocumentsByDateRange(String query, LocalDate startDate, LocalDate endDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        // The filter expression syntax does not require the "metadata." prefix.
        String filterExpression = String.format("start_date >= '%s' AND end_date <= '%s'",
                startDate.format(formatter),
                endDate.format(formatter));
        return executeTemporalQuery(query, filterExpression);
    }

    /**
     * Flexible temporal search that accepts either a year (e.g., "2021") or a date (e.g., "01-02-2021").
     * If a year is provided, searches for documents within that entire year.
     * If a date is provided, searches for documents that overlap with that specific date.
     *
     * @param query The user's question or query text.
     * @param dateInput A string representing either a year (yyyy) or a date (MM-dd-yyyy).
     * @return A list of documents that match the criteria.
     */
    public List<Document> findDocumentsByFlexibleDate(String query, String dateInput) {
        dateInput = dateInput.trim();

        // Check if it's a 4-digit year (e.g., "2021")
        if (dateInput.matches("^\\d{4}$")) {
            int year = Integer.parseInt(dateInput);
            log.info("Parsed as year: {}", year);
            return findDocumentsByYear(query, year);
        }

        // Check if it's MM-dd-yyyy format (e.g., "01-02-2021")
        if (dateInput.matches("^\\d{2}-\\d{2}-\\d{4}$")) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd-yyyy");
            LocalDate date = LocalDate.parse(dateInput, formatter);
            log.info("Parsed as date: {}", date);

            // Search for documents that contain this specific date
            String filterExpression = String.format(
                "metadata[\"start_date\"] <= '%s' && metadata[\"end_date\"] >= '%s'",
                date.format(DateTimeFormatter.ISO_LOCAL_DATE),
                date.format(DateTimeFormatter.ISO_LOCAL_DATE)
            );
            return executeTemporalQuery(query, filterExpression);
        }

        throw new IllegalArgumentException(
            "Invalid date format: '" + dateInput + "'. Expected formats: 'yyyy' (e.g., 2021) or 'MM-dd-yyyy' (e.g., 01-02-2021)"
        );
    }

    /**
     * Private helper method that bypasses Spring AI's filter expression system
     * and uses direct SQL with proper JSONB filtering and vector similarity search.
     */
    private List<Document> executeTemporalQuery(String query, String filterExpression) {
        log.info("=== TEMPORAL QUERY DEBUG ===");
        log.info("Query: [{}]", query);
        log.info("Spring AI Filter Expression (NOT USED): [{}]", filterExpression);

        // Extract date range from filter expression (hacky but works)
        String startDate = null;
        String endDate = null;

        if (filterExpression.contains(">=") && filterExpression.contains("<=")) {
            // Extract dates from format: "start_date >= '2021-01-01' AND end_date <= '2021-12-31'"
            // or: "metadata[\"start_date\"] <= '2023-01-15' && metadata[\"end_date\"] >= '2023-01-15'"
            String[] parts = filterExpression.split("\\s+(AND|&&)\\s+");
            for (String part : parts) {
                if (part.contains("start_date")) {
                    startDate = part.replaceAll(".*'([^']+)'.*", "$1");
                } else if (part.contains("end_date")) {
                    endDate = part.replaceAll(".*'([^']+)'.*", "$1");
                }
            }
        }

        log.info("Extracted date range: {} to {}", startDate, endDate);

        try {
            // Generate embedding for the query
            log.info("Generating embedding for query...");
            float[] queryEmbedding = embeddingModel.embed(query);
            log.info("Embedding generated, size: {}", queryEmbedding.length);

            // Convert float array to PostgreSQL vector format string
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < queryEmbedding.length; i++) {
                if (i > 0) sb.append(",");
                sb.append(queryEmbedding[i]);
            }
            sb.append("]");
            String vectorString = sb.toString();
            log.info("Vector string length: {}", vectorString.length());

            // Direct SQL query with JSONB filtering and vector similarity
            // Logic: Find documents where the document's date range OVERLAPS with the query range
            // Document overlaps with query range if:
            //   - Document starts before or at query end: doc_start <= query_end
            //   - Document ends after or at query start: doc_end >= query_start
            String sql = """
                SELECT id, content, metadata,
                       1 - (embedding <=> ?::vector) as similarity
                FROM vector_store
        
                WHERE (metadata->>'start_date')::date <= ?::date
                  AND (metadata->>'end_date')::date >= ?::date
                ORDER BY embedding <=> ?::vector
                LIMIT 5
                """;

            log.info("Executing direct SQL query with temporal filter");
            log.info("SQL params: vectorString (length={}), endDate={}, startDate={}", vectorString.length(), endDate, startDate);

            List<Document> results = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> {
                    String content = rs.getString("content");
                    String metadataJson = rs.getString("metadata");
                    double similarity = rs.getDouble("similarity");

                    // Parse metadata JSON (simple approach)
                    Map<String, Object> metadata = new HashMap<>();
                    metadata.put("similarity", similarity);
                    // Parse full JSON here in case it's  needed

                    return new Document(content, metadata);
                },
                vectorString, endDate, startDate, vectorString
            );

            log.info("Found {} documents matching the temporal query.", results.size());

            if (results.isEmpty()) {
                log.warn("No results found. Possible reasons:");
                log.warn("1. No documents exist for date range: {} to {}", startDate, endDate);
                log.warn("2. Check if metadata has correct date format");
            }

            return results;

        } catch (Exception e) {
            log.error("Error executing temporal query", e);
            throw new RuntimeException("Temporal query failed: " + e.getMessage(), e);
        }
    }
}
