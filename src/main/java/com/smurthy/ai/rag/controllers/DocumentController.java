package com.smurthy.ai.rag.controllers;

import com.smurthy.ai.rag.service.DocumentIngestionService;
import com.smurthy.ai.rag.service.DocumentQueryService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;


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
     * Upload a document for ingestion into the vector store.
     * @param file The document to upload.
     * @return A status message.
     */
    @PostMapping("/upload")
    public String uploadDocument(@RequestParam("file") MultipartFile file) {
        try {
            // Adapt the MultipartFile to a Resource that includes the filename,
            // which is essential for the ingestion service's duplicate check.
            var resource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };

            embeddingService.ingest(resource);
            return "Successfully loaded document: " + file.getOriginalFilename();
        } catch (Exception e) {
            return "Error loading document: " + e.getMessage();
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
}
