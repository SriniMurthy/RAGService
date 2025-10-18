package com.smurthy.ai.rag.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TemporalQueryService.
 *
 * Tests temporal document filtering by:
 * - Year (e.g., 2021)
 * - Specific date (e.g., 01-15-2021)
 * - Date range (e.g., 2021-01-01 to 2021-12-31)
 */
@ExtendWith(MockitoExtension.class)
class TemporalQueryServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private EmbeddingModel embeddingModel;

    private TemporalQueryService temporalQueryService;

    @BeforeEach
    void setUp() {
        temporalQueryService = new TemporalQueryService(vectorStore, jdbcTemplate, embeddingModel);
    }

    @Test
    @DisplayName("Should query documents by year")
    void testFindDocumentsByYear() {
        // Given
        String query = "revenue";
        int year = 2023;
        float[] mockEmbedding = new float[]{0.1f, 0.2f, 0.3f};

        when(embeddingModel.embed(query)).thenReturn(mockEmbedding);

        List<Document> mockDocuments = List.of(
                createDocument("Q1 2023 revenue was $1M", "2023-01-01", "2023-03-31"),
                createDocument("Q2 2023 revenue was $1.2M", "2023-04-01", "2023-06-30")
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(mockDocuments);

        // When
        List<Document> results = temporalQueryService.findDocumentsByYear(query, year);

        // Then
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getText()).contains("Q1 2023");
        assertThat(results.get(1).getText()).contains("Q2 2023");

        // Verify SQL query was called with correct date range
        ArgumentCaptor<String> endDateCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> startDateCaptor = ArgumentCaptor.forClass(String.class);

        verify(jdbcTemplate).query(
                anyString(),
                any(RowMapper.class),
                anyString(),
                endDateCaptor.capture(),    // endDate = 2023-12-31
                startDateCaptor.capture(),   // startDate = 2023-01-01
                anyString()
        );

        assertThat(endDateCaptor.getValue()).isEqualTo("2023-12-31");
        assertThat(startDateCaptor.getValue()).isEqualTo("2023-01-01");
    }

    @Test
    @DisplayName("Should query documents by date range")
    void testFindDocumentsByDateRange() {
        // Given
        String query = "sales";
        LocalDate startDate = LocalDate.of(2023, 1, 1);
        LocalDate endDate = LocalDate.of(2023, 6, 30);
        float[] mockEmbedding = new float[]{0.1f, 0.2f, 0.3f};

        when(embeddingModel.embed(query)).thenReturn(mockEmbedding);

        List<Document> mockDocuments = List.of(
                createDocument("Q1 sales data", "2023-01-01", "2023-03-31"),
                createDocument("Q2 sales data", "2023-04-01", "2023-06-30")
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(mockDocuments);

        // When
        List<Document> results = temporalQueryService.findDocumentsByDateRange(query, startDate, endDate);

        // Then
        assertThat(results).hasSize(2);

        // Verify correct date parameters
        verify(jdbcTemplate).query(
                anyString(),
                any(RowMapper.class),
                anyString(),
                eq("2023-06-30"),  // endDate
                eq("2023-01-01"),  // startDate
                anyString()
        );
    }

    @Test
    @DisplayName("Should parse flexible date as year")
    void testFlexibleDateAsYear() {
        // Given
        String query = "projects";
        String dateInput = "2021";  // Year format
        float[] mockEmbedding = new float[]{0.1f, 0.2f, 0.3f};

        when(embeddingModel.embed(query)).thenReturn(mockEmbedding);

        List<Document> mockDocuments = List.of(
                createDocument("Project A completed in 2021", "2021-01-01", "2021-12-31")
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(mockDocuments);

        // When
        List<Document> results = temporalQueryService.findDocumentsByFlexibleDate(query, dateInput);

        // Then
        assertThat(results).hasSize(1);

        // Verify it used year range (2021-01-01 to 2021-12-31)
        verify(jdbcTemplate).query(
                anyString(),
                any(RowMapper.class),
                anyString(),
                eq("2021-12-31"),
                eq("2021-01-01"),
                anyString()
        );
    }

    @Test
    @DisplayName("Should parse flexible date as MM-dd-yyyy")
    void testFlexibleDateAsSpecificDate() {
        // Given
        String query = "meeting notes";
        String dateInput = "01-15-2023";  // MM-dd-yyyy format
        float[] mockEmbedding = new float[]{0.1f, 0.2f, 0.3f};

        when(embeddingModel.embed(query)).thenReturn(mockEmbedding);

        List<Document> mockDocuments = List.of(
                createDocument("Meeting notes from Jan 15", "2023-01-15", "2023-01-15")
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any(), any()))
                .thenReturn(mockDocuments);

        // When
        List<Document> results = temporalQueryService.findDocumentsByFlexibleDate(query, dateInput);

        // Then
        assertThat(results).hasSize(1);

        // Verify it used specific date (2023-01-15)
        verify(jdbcTemplate).query(
                anyString(),
                any(RowMapper.class),
                anyString(),
                eq("2023-01-15"),  // Same date for both start and end
                eq("2023-01-15"),
                anyString()
        );
    }

    @Test
    @DisplayName("Should throw exception for invalid date format")
    void testInvalidDateFormat() {
        // Given
        String query = "test";
        String invalidDateInput = "invalid-date";

        // When & Then
        assertThatThrownBy(() -> temporalQueryService.findDocumentsByFlexibleDate(query, invalidDateInput))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid date format");
    }

    @Test
    @DisplayName("Should return empty list when no documents found")
    void testNoDocumentsFound() {
        // Given
        String query = "nonexistent";
        int year = 2099;
        float[] mockEmbedding = new float[]{0.1f, 0.2f, 0.3f};

        when(embeddingModel.embed(query)).thenReturn(mockEmbedding);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());

        // When
        List<Document> results = temporalQueryService.findDocumentsByYear(query, year);

        // Then
        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("Should handle embedding generation correctly")
    void testEmbeddingGeneration() {
        // Given
        String query = "test query";
        int year = 2023;
        float[] expectedEmbedding = new float[]{0.1f, 0.2f, 0.3f, 0.4f, 0.5f};

        when(embeddingModel.embed(query)).thenReturn(expectedEmbedding);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of());

        // When
        temporalQueryService.findDocumentsByYear(query, year);

        // Then
        verify(embeddingModel).embed(query);

        // Verify embedding was passed to SQL query as vector string
        ArgumentCaptor<String> vectorCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(
                anyString(),
                any(RowMapper.class),
                vectorCaptor.capture(),  // First parameter is the vector string
                anyString(),
                anyString(),
                anyString()  // Last parameter is also the vector (for sorting)
        );

        String vectorString = vectorCaptor.getValue();
        assertThat(vectorString).startsWith("[");
        assertThat(vectorString).endsWith("]");
        assertThat(vectorString).contains("0.1,0.2,0.3,0.4,0.5");
    }

    // ========== HELPER METHODS ==========

    private Document createDocument(String content, String startDate, String endDate) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("start_date", startDate);
        metadata.put("end_date", endDate);
        metadata.put("source", "test_document.pdf");

        return new Document(content, metadata);
    }
}