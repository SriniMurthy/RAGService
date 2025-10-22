package com.smurthy.ai.rag.readers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Tika-based PDF Reader with OCR Support (using Spring AI)
 *
 * Uses Spring AI's TikaDocumentReader which wraps Apache Tika + Tesseract.
 *
 * Prerequisites:
 * 1. Dependency already added: spring-ai-tika-document-reader
 *
 * 2. Install Tesseract OCR:
 *    macOS:   brew install tesseract
 *    Ubuntu:  sudo apt-get install tesseract-ocr
 *    Windows: Download from https://github.com/UB-Mannheim/tesseract/wiki
 *
 * 3. Set environment variable (optional):
 *    export TESSDATA_PREFIX=/usr/local/share/tessdata
 *
 * Features:
 * - Automatically detects and extracts text from scanned PDFs (images)
 * - Handles text embedded in images
 * - Falls back gracefully if Tesseract not installed
 * - Integrates seamlessly with Spring AI Document model
 */
@Component
@ConditionalOnClass(name = "org.springframework.ai.reader.tika.TikaDocumentReader")
public class TikaOcrPdfReader implements DocumentReader {

    private static final Logger log = LoggerFactory.getLogger(TikaOcrPdfReader.class);

    public TikaOcrPdfReader() {
        checkTesseractAvailability();
    }

    @Override
    public boolean supports(Resource resource) {
        return resource.getFilename() != null &&
               resource.getFilename().toLowerCase().endsWith(".pdf");
    }

    @Override
    public List<Document> read(Resource resource) {
        log.info("TikaOcrPdfReader: Processing PDF with OCR: {}", resource.getFilename());
        long startTime = System.currentTimeMillis();

        try {
            // Spring AI's TikaDocumentReader automatically:
            // - Detects if OCR is needed
            // - Uses Tesseract for scanned PDFs
            // - Returns Spring AI Documents
            TikaDocumentReader tikaReader = new TikaDocumentReader(resource);
            List<Document> documents = tikaReader.get();

            long duration = System.currentTimeMillis() - startTime;
            log.info("✓ Tika OCR extraction completed in {}ms: {} documents extracted",
                    duration, documents.size());

            // Enrich metadata
            return documents.stream()
                    .map(doc -> {
                        var metadata = new java.util.HashMap<>(doc.getMetadata());
                        metadata.put("extraction_method", "Tika-OCR-Tesseract");
                        metadata.put("char_count", doc.getText().length());
                        return Document.builder()
                                .id(doc.getId())
                                .text(doc.getText())
                                .metadata(metadata)
                                .build();
                    })
                    .toList();

        } catch (Exception e) {
            log.error("Tika OCR failed: {}", e.getMessage());

            if (e.getMessage() != null && e.getMessage().contains("Tesseract")) {
                throw new RuntimeException(
                    "Tesseract OCR not installed or not working. Install with:\n" +
                    "  macOS:   brew install tesseract\n" +
                    "  Ubuntu:  sudo apt-get install tesseract-ocr\n" +
                    "  Windows: https://github.com/UB-Mannheim/tesseract/wiki",
                    e
                );
            }

            throw new RuntimeException("Tika PDF reading failed", e);
        }
    }

    /**
     * Check if Tesseract is available on the system
     */
    private void checkTesseractAvailability() {
        try {
            ProcessBuilder pb = new ProcessBuilder("tesseract", "--version");
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                log.info("✓ Tesseract OCR detected and available");
            } else {
                log.warn("⚠ Tesseract OCR may not be properly installed (exit code: {})", exitCode);
            }
        } catch (Exception e) {
            log.warn("⚠ Tesseract OCR not found on system. OCR will not work. Install with:");
            log.warn("   macOS:   brew install tesseract");
            log.warn("   Ubuntu:  sudo apt-get install tesseract-ocr");
            log.warn("   Windows: https://github.com/UB-Mannheim/tesseract/wiki");
        }
    }
}