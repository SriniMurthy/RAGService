package com.smurthy.ai.rag.controllers;

import com.smurthy.ai.rag.service.DocumentIngestionService;
import com.smurthy.ai.rag.service.DocumentQueryService;
import com.smurthy.ai.rag.service.TemporalQueryService;
import org.springframework.ai.document.Document;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST Controller for document management and querying.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService embeddingService;
    private final DocumentQueryService documentQueryService;
    private final TemporalQueryService temporalQueryService;

    public DocumentController(DocumentIngestionService embeddingService, DocumentQueryService documentQueryService, TemporalQueryService temporalQueryService) {
        this.embeddingService = embeddingService;
        this.documentQueryService = documentQueryService;
        this.temporalQueryService = temporalQueryService;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> uploadDocument(@RequestParam("file") MultipartFile file,
                                             @RequestParam("category") String category) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("File cannot be empty.");
        }
        if (category == null || category.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Category cannot be empty.");
        }

        try {
            var resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            embeddingService.ingest(resource, category);
            String successMessage = String.format("Successfully loaded document: %s into category: %s", file.getOriginalFilename(), category);
            return ResponseEntity.ok(successMessage);
        } catch (Exception e) {
            String errorMessage = String.format("Error loading document: %s", e.getMessage());
            return ResponseEntity.internalServerError().body(errorMessage);
        }
    }

    @GetMapping
    public List<String> listAllDocuments() {
        return documentQueryService.listIngestedDocuments();
    }

    @GetMapping("/categories")
    public Map<String, Long> getCategoryCounts() {
        return documentQueryService.getCategoryCounts();
    }

    /**
     * Flexible temporal search that accepts either a year or a date.
     * Examples:
     * - /temporal-search?query=budget&date=2021 (searches entire year 2021)
     * - /temporal-search?query=budget&date=01-02-2021 (searches specific date)
     * - /temporal-search?query=budget&date=2021-01-02 (searches specific date)
     * @param query The natural language query.
     * @param date Either a 4-digit year or a date in various formats (MM-dd-yyyy, yyyy-MM-dd, etc.)
     * @return A list of document contents that match the criteria.
     */
    @GetMapping("/temporalSearch")
    public ResponseEntity<?> temporalSearch(@RequestParam String query, @RequestParam String date) {
        try {
            List<Document> results = temporalQueryService.findDocumentsByFlexibleDate(query, date);
            List<String> documentTexts = results.stream()
                    .map(Document::getText)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(documentTexts);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Performs a hybrid search, filtering documents by a date range before running a semantic search.
     * @param query The natural language query.
     * @param startDate The start of the date range (format: YYYY-MM-DD).
     * @param endDate The end of the date range (format: YYYY-MM-DD).
     * @return A list of document contents that match the criteria.
     */
    @GetMapping("/temporalSearchRange")
    public ResponseEntity<?> temporalSearchByRange(
            @RequestParam String query,
            @RequestParam String startDate,
            @RequestParam String endDate) {
        try {
            // Parse flexible input (year or full date)
            LocalDate start = startDate.matches("\\d{4}")
                ? LocalDate.of(Integer.parseInt(startDate), 1, 1)
                : LocalDate.parse(startDate);
            LocalDate end = endDate.matches("\\d{4}")
                ? LocalDate.of(Integer.parseInt(endDate), 12, 31)
                : LocalDate.parse(endDate);

            List<Document> results = temporalQueryService.findDocumentsByDateRange(query, start, end);
            List<String> documentTexts = results.stream()
                    .map(Document::getText)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(documentTexts);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid date format: " + e.getMessage()));
        }
    }
}
