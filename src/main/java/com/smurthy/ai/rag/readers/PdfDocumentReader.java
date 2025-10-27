package com.smurthy.ai.rag.readers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.ParagraphPdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Enhanced PDF Document Reader with fallback mechanisms
 *
 * Handles problematic PDFs that cause sorting errors in Spring AI's layout-based reader
 * by falling back to simpler extraction strategies.
 *
 * Note: This reader has lower priority than SmartPdfReader when both are enabled.
 */
@Component
@org.springframework.core.annotation.Order(100)
public class PdfDocumentReader implements DocumentReader {

    private static final Logger log = LoggerFactory.getLogger(PdfDocumentReader.class);

    @Override
    public boolean supports(Resource resource) {
        String filename = resource.getFilename();
        return filename != null && filename.toLowerCase().endsWith(".pdf");
    }

    @Override
    public List<Document> read(Resource resource) {
        // Try multiple strategies in order of preference

        // First strategy: PagePdfDocumentReader (best layout preservation)
        try {
            return readWithPageReader(resource);
        } catch (Exception e) {
            log.warn("PagePdfDocumentReader failed for {}: {}. Trying ParagraphPdfDocumentReader.",
                    resource.getFilename(), e.getMessage());
        }

        // Second: ParagraphPdfDocumentReader (simpler, more robust)
        try {
            return readWithParagraphReader(resource);
        } catch (Exception e) {
            log.error("All PDF reading strategies failed for {}: {}",
                    resource.getFilename(), e.getMessage());

            // Return error document rather than failing completely
            return List.of(createErrorDocument(resource, e));
        }
        //Third strategy: Tika reader?
    }

    /**
     *  Use PagePdfDocumentReader with layout preservation
     */
    private List<Document> readWithPageReader(Resource resource) {
        var config = PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(1) // Process one page at a time for better chunking
                .build();

        var reader = new PagePdfDocumentReader(resource, config);
        List<Document> documents = reader.get();

        log.info("Successfully extracted {} pages from {} using PagePdfDocumentReader",
                documents.size(), resource.getFilename());

        return documents;
    }

    /**
     *  Use ParagraphPdfDocumentReader (simpler, avoids layout sorting issues)
     */
    private List<Document> readWithParagraphReader(Resource resource) {
        var config = PdfDocumentReaderConfig.builder()
                .withPagesPerDocument(1)
                .build();

        var reader = new ParagraphPdfDocumentReader(resource, config);
        List<Document> documents = reader.get();

        // Add metadata indicating fallback method was used
        documents.forEach(doc -> {
            Map<String, Object> metadata = new HashMap<>(doc.getMetadata());
            metadata.put("extractionMethod", "ParagraphPdfDocumentReader-Fallback");
            doc.getMetadata().putAll(metadata);
        });

        log.info("Successfully extracted {} pages from {} using ParagraphPdfDocumentReader (fallback)",
                documents.size(), resource.getFilename());

        return documents;
    }

    /**
     * Create an error document when all extraction methods fail
     */
    private Document createErrorDocument(Resource resource, Exception e) {
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", resource.getFilename());
        metadata.put("error", true);
        metadata.put("errorMessage", e.getMessage());

        String errorContent = String.format(
                "Failed to extract content from PDF: %s. Error: %s",
                resource.getFilename(),
                e.getMessage()
        );

        return new Document(errorContent, metadata);
    }
}