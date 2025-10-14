package com.smurthy.ai.rag.controllers;

import com.smurthy.ai.rag.service.DocumentIngestionService;
import com.smurthy.ai.rag.service.DocumentQueryService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * REST Controller for document management. Upload your documents
 * for ingestion and embedding here.
 */
@RestController
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentIngestionService embeddingService;
    private final DocumentQueryService documentQueryService;

    public DocumentController(DocumentIngestionService embeddingService, DocumentQueryService documentQueryService) {
        this.embeddingService = embeddingService;
        this.documentQueryService = documentQueryService;
    }

    /**
     * Upload a document for ingestion into the vector store with a specified category.
     * @param file The document to upload.
     * @param category The category to assign to the document (e.g., 'finance', 'textbooks').
     * @return A status message.
     */
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
            // Adapt the MultipartFile to a Resource that includes the filename
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

    /**
     * Lists the names of all documents that have been ingested into the vector store.
     * @return A list of document filenames.
     */
    @GetMapping
    public List<String> listAllDocuments() {
        return documentQueryService.listIngestedDocuments();
    }

    /**
     * Gets a count of ingested document chunks for each category.
     * @return A map where the key is the category name and the value is the count.
     */
    @GetMapping("/categories")
    public Map<String, Long> getCategoryCounts() {
        return documentQueryService.getCategoryCounts();
    }
}
