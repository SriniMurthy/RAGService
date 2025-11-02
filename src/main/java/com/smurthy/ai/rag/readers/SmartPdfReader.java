package com.smurthy.ai.rag.readers;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Smart PDF Reader with 2-Tier Fallback Strategy
 * (Zero Cost as opposed to AWS Textract or other $$ options)
 *
 * Strategy:
 * 1. TIER 1: Try PDFBox (fast, free) for text-based PDFs
 * 2. TIER 2: Fall back to Tika/Tesseract (free OCR) for scanned PDFs
 *
 * When to use each tier:
 * - TIER 1 (PDFBox): Born-digital PDFs (most common)
 *   ✓ Fast: ~100ms per document
 *   ✓ Free: No API calls
 *   ✓ Good quality for text PDFs
 *
 * - TIER 2 (Tika/Tesseract): Scanned PDFs, images
 *   ✓ Free: No API costs
 *   ✓ Handles images and scanned documents
 *   ✗ Slower: 5-10 seconds per page
 *   ✗ Requires Tesseract installed
 *
 * Configuration (application.yaml):
 * pdf:
 *   reader:
 *     smart: true                    # Enable SmartPdfReader (default: false)
 *     enable-ocr: true                # Enable Tika/Tesseract fallback
 *     min-chars-per-page: 50          # Threshold to detect scanned PDFs
 *     ocr-improvement-threshold: 1.5  # Use OCR if it extracts 1.5x more text
 *
 * Example:
 * pdf:
 *   reader:
 *     smart: true
 *     enable-ocr: true
 */
@Component("smartPdfReader")
@ConditionalOnProperty(name = "pdf.reader.smart", havingValue = "true", matchIfMissing = false)
@org.springframework.core.annotation.Order(1)
public class SmartPdfReader implements DocumentReader {

    private static final Logger log = LoggerFactory.getLogger(SmartPdfReader.class);

    private final PdfDocumentReader standardReader;

    @Autowired(required = false)
    private TikaOcrPdfReader ocrReader;

    @Value("${pdf.reader.enable-ocr:true}")
    private boolean enableOcr;

    @Value("${pdf.reader.min-chars-per-page:50}")
    private int minCharsPerPage;

    @Value("${pdf.reader.ocr-improvement-threshold:1.5}")
    private double ocrImprovementThreshold;

    public SmartPdfReader(PdfDocumentReader standardReader) {
        this.standardReader = standardReader;
    }

    @Override
    public boolean supports(Resource resource) {
        return resource.getFilename() != null &&
               resource.getFilename().toLowerCase().endsWith(".pdf");
    }

    @Override
    public List<Document> read(Resource resource) {
        log.info("╔════════════════════════════════════════════════════════╗");
        log.info("║  SmartPdfReader: Processing {}",
                String.format("%-28s", resource.getFilename()) + " ║");
        log.info("╚════════════════════════════════════════════════════════╝");

        // TIER 1: Try standard PDFBox extraction (fast, free)
        List<Document> tier1Docs = tryTier1(resource);

        if (tier1Docs != null) {
            ExtractionQuality quality = analyzeQuality(tier1Docs);

            log.info("→ TIER 1 (PDFBox): {} pages extracted, quality: {}",
                    tier1Docs.size(), quality);

            // If good quality, return immediately
            if (quality == ExtractionQuality.GOOD) {
                log.info("✓ SUCCESS: Using TIER 1 results (high quality)");
                return enrichMetadata(tier1Docs, "Tier1-PDFBox");
            }

            // If low quality and OCR is enabled, try Tier 2
            if (quality == ExtractionQuality.LOW && enableOcr && ocrReader != null) {
                log.warn("Low quality detected - attempting TIER 2 (OCR)...");
                List<Document> tier2Docs = tryTier2(resource, tier1Docs);

                if (tier2Docs != null) {
                    return tier2Docs;
                }
            }

            // Return Tier 1 results even if low quality (OCR disabled or failed)
            if (quality == ExtractionQuality.LOW && !enableOcr) {
                log.warn("Low quality, but OCR disabled - using TIER 1 anyway");
            }
            return enrichMetadata(tier1Docs, "Tier1-PDFBox-LowQuality");
        }

        // Tier 1 completely failed - try Tier 2 directly
        if (enableOcr && ocrReader != null) {
            log.error("TIER 1 failed completely - attempting TIER 2 (OCR)...");
            return tryTier2Fallback(resource);
        }

        throw new RuntimeException("PDF reading failed (all tiers exhausted)");
    }

    /**
     * TIER 1: Standard PDFBox extraction
     */
    private List<Document> tryTier1(Resource resource) {
        try {
            long startTime = System.currentTimeMillis();
            List<Document> docs = standardReader.read(resource);
            long duration = System.currentTimeMillis() - startTime;

            log.debug("  TIER 1 completed in {}ms", duration);
            return docs;

        } catch (Exception e) {
            log.error("  TIER 1 failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * TIER 2: OCR with quality comparison
     */
    private List<Document> tryTier2(Resource resource, List<Document> tier1Docs) {
        try {
            log.info("→ TIER 2 (Tika/Tesseract): Starting OCR extraction...");
            long startTime = System.currentTimeMillis();

            List<Document> ocrDocs = ocrReader.read(resource);

            long duration = System.currentTimeMillis() - startTime;
            log.info("  TIER 2 completed in {}ms", duration);

            // Compare quality: OCR vs PDFBox
            double tier1AvgChars = calculateAvgCharsPerPage(tier1Docs);
            double tier2AvgChars = calculateAvgCharsPerPage(ocrDocs);

            log.info("  Quality comparison:");
            log.info("    TIER 1 (PDFBox):  {:.0f} chars/page", tier1AvgChars);
            log.info("    TIER 2 (OCR):     {:.0f} chars/page", tier2AvgChars);

            // Use OCR if it extracted significantly more text
            if (tier2AvgChars > tier1AvgChars * ocrImprovementThreshold) {
                log.info("SUCCESS: TIER 2 produced {:.1f}x more text - using OCR results",
                        tier2AvgChars / tier1AvgChars);
                return enrichMetadata(ocrDocs, "Tier2-OCR-Better");
            } else {
                log.info("SUCCESS: TIER 1 was comparable - using original PDFBox results");
                return enrichMetadata(tier1Docs, "Tier1-PDFBox-AfterOCRComparison");
            }

        } catch (Exception e) {
            log.error(" TIER 2 (OCR) failed: {}", e.getMessage());
            log.info("Falling back to TIER 1 results");
            return enrichMetadata(tier1Docs, "Tier1-PDFBox-OCRFailed");
        }
    }

    /**
     * TIER 2: Direct OCR (when Tier 1 completely failed)
     */
    private List<Document> tryTier2Fallback(Resource resource) {
        try {
            List<Document> ocrDocs = ocrReader.read(resource);
            log.info("SUCCESS: TIER 2 (OCR) recovered from TIER 1 failure");
            return enrichMetadata(ocrDocs, "Tier2-OCR-Fallback");
        } catch (Exception e) {
            log.error("TIER 2 also failed: {}", e.getMessage());
            throw new RuntimeException("All PDF reading tiers failed", e);
        }
    }

    /**
     * Analyze extraction quality
     */
    private ExtractionQuality analyzeQuality(List<Document> docs) {
        if (docs.isEmpty()) {
            return ExtractionQuality.LOW;
        }

        double avgChars = calculateAvgCharsPerPage(docs);

        if (avgChars < minCharsPerPage) {
            return ExtractionQuality.LOW;
        }

        return ExtractionQuality.GOOD;
    }

    /**
     * Calculate average characters per page
     */
    private double calculateAvgCharsPerPage(List<Document> docs) {
        return docs.stream()
                .mapToInt(d -> d.getText().length())
                .average()
                .orElse(0);
    }

    /**
     * Enrich metadata with tier information
     */
    private List<Document> enrichMetadata(List<Document> docs, String tierInfo) {
        return docs.stream()
                .map(doc -> {
                    var metadata = new java.util.HashMap<>(doc.getMetadata());
                    metadata.put("extraction_tier", tierInfo);
                    metadata.put("smart_reader", "enabled");
                    return Document.builder()
                            .id(doc.getId())
                            .text(doc.getText())
                            .metadata(metadata)
                            .build();
                })
                .toList();
    }

    /**
     * Quality assessment enum
     */
    private enum ExtractionQuality {
        GOOD,  // High-quality text extraction (>= minCharsPerPage)
        LOW    // Likely scanned/image PDF (< minCharsPerPage)
    }
}