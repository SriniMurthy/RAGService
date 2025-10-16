package com.smurthy.ai.rag.service;

import com.smurthy.ai.rag.readers.DocumentReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final JdbcClient jdbcClient;
    private final TextSplitter textSplitter;
    private final List<DocumentReader> documentReaders;
    private final FastDocumentIngestionService embeddingService;

    public DocumentIngestionService(JdbcClient jdbcClient, TextSplitter textSplitter, List<DocumentReader> documentReaders,
                                    FastDocumentIngestionService embeddingService) {
        this.jdbcClient = jdbcClient;
        this.textSplitter = textSplitter;
        this.documentReaders = documentReaders;
        this.embeddingService = embeddingService;
    }

    public void ingest(Resource resource, String category) {
        String resourceName = resource.getFilename();
        if (resourceName == null) {
            log.warn("Cannot process a resource without a filename: {}", resource);
            return;
        }

        String sql = "SELECT COUNT(*) FROM vector_store WHERE metadata ->> 'file_name' = :fileName";
        Integer count = jdbcClient.sql(sql).param("fileName", resourceName).query(Integer.class).single();

        if (count == 0) {
            log.info("Ingesting new document: '{}' into category '{}'", resourceName, category);
            documentReaders.stream()
                    .filter(reader -> reader.supports(resource))
                    .findFirst()
                    .ifPresentOrElse(reader -> {
                        cleanAndEmbedDocument(resource, reader, textSplitter, category);
                    }, () -> log.warn("No reader found for resource '{}'. Skipping.", resourceName));
        } else {
            log.debug("Documents from '{}' are already loaded. Skipping.", resourceName);
        }
    }

    private void cleanAndEmbedDocument(Resource resource, DocumentReader reader, TextSplitter textSplitter, String category) {
        List<Document> documents = reader.read(resource);
        List<Document> allChunks = new ArrayList<>();

        documents.parallelStream().forEach(doc -> {
            var cleanedDocument = this.cleanDocument(doc);
            List<Document> chunks = textSplitter.apply(List.of(cleanedDocument));

            // Attempt to extract date information from the document content
            Map<String, String> dateInfo = extractDatesFromDocument(doc.getText());

            chunks.forEach(chunk -> {
                chunk.getMetadata().put("category", category);
                if (resource.getFilename() != null) {
                    chunk.getMetadata().put("file_name", resource.getFilename());
                }
                // Add the extracted date info to each chunk
                chunk.getMetadata().putAll(dateInfo);
            });

            allChunks.addAll(chunks);
        });

        embeddingService.ingestFast(allChunks);
    }

    /**
     * Extracts date ranges from document content, particularly from resume job entries.
     * Handles formats like:
     * - "01.2020 - 05.2021"
     * - "06.2021 - Current"
     * - "2020 - 2021"
     * - "01/2020 - 05/2021"
     * Falls back to finding earliest and latest years in the content.
     */
    private Map<String, String> extractDatesFromDocument(String content) {
        // Pattern 1: Date ranges like "MM.YYYY - MM.YYYY" or "MM.YYYY - Current"
        Pattern dateRangePattern = Pattern.compile("(\\d{2})\\.(\\d{4})\\s*[-–—]\\s*(?:(\\d{2})\\.(\\d{4})|Current|Present)", Pattern.CASE_INSENSITIVE);
        Matcher dateRangeMatcher = dateRangePattern.matcher(content);

        String earliestStart = null;
        String latestEnd = null;

        while (dateRangeMatcher.find()) {
            String startMonth = dateRangeMatcher.group(1);
            String startYear = dateRangeMatcher.group(2);
            String endMonth = dateRangeMatcher.group(3);
            String endYear = dateRangeMatcher.group(4);

            String startDate = startYear + "-" + startMonth + "-01";
            String endDate;

            if (endYear != null) {
                // Normal date range
                endDate = endYear + "-" + endMonth + "-28"; // Use 28 to avoid month-end issues
            } else {
                // "Current" or "Present"
                endDate = java.time.LocalDate.now().toString();
            }

            // Track earliest start and latest end
            if (earliestStart == null || startDate.compareTo(earliestStart) < 0) {
                earliestStart = startDate;
            }
            if (latestEnd == null || endDate.compareTo(latestEnd) > 0) {
                latestEnd = endDate;
            }

            log.debug("Found date range: {} to {}", startDate, endDate);
        }

        if (earliestStart != null && latestEnd != null) {
            log.info("Extracted date range from {} to {} for document chunk", earliestStart, latestEnd);
            return Map.of("start_date", earliestStart, "end_date", latestEnd);
        }

        // Fallback: Pattern 2 - Look for year ranges like "2008 - 2017" or just collect all years
        Pattern yearPattern = Pattern.compile("\\b(19[89][0-9]|20[0-2][0-9])\\b");
        Matcher yearMatcher = yearPattern.matcher(content);

        int minYear = Integer.MAX_VALUE;
        int maxYear = Integer.MIN_VALUE;
        boolean foundYear = false;

        while (yearMatcher.find()) {
            int year = Integer.parseInt(yearMatcher.group(1));
            if (year < minYear) minYear = year;
            if (year > maxYear) maxYear = year;
            foundYear = true;
        }

        if (foundYear) {
            String startDate = minYear + "-01-01";
            String endDate = maxYear + "-12-31";
            log.debug("Extracted year range from {} to {} for document chunk", startDate, endDate);
            return Map.of("start_date", startDate, "end_date", endDate);
        }

        log.debug("No dates found in document chunk");
        return Map.of(); // Return empty map if no date is found
    }

    protected static Document cleanDocument(Document doc) {
        String cleanedText = cleanText(doc.getText());
        return new Document(cleanedText, doc.getMetadata());
    }

    private static String cleanText(String text) {
        if (text == null) return "";
        return text.replaceAll("[^\\x00-\\x7F]", " ").replaceAll("\\p{C}", " ").replaceAll("\\s+", " ").trim();
    }
}
