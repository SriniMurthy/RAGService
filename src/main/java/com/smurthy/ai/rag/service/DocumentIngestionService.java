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
import java.util.concurrent.CopyOnWriteArrayList;
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
        List<Document> allChunks = new CopyOnWriteArrayList<>();

        String fileName = resource.getFilename();
        String personName = fileName != null ? extractPersonName(fileName) : null;

        documents.parallelStream().forEach(doc -> {
            var cleanedDocument = this.cleanDocument(doc);
            List<Document> chunks = textSplitter.apply(List.of(cleanedDocument));

            Map<String, String> dateInfo = extractDatesFromDocument(doc.getText());
            String header = buildChunkHeader(fileName, personName, category);

            List<Document> stampedChunks = chunks.stream().map(chunk -> {
                chunk.getMetadata().put("category", category);
                if (fileName != null) {
                    chunk.getMetadata().put("file_name", fileName);
                    chunk.getMetadata().put("source", fileName);
                }
                if (personName != null) {
                    chunk.getMetadata().put("person_name", personName);
                }
                chunk.getMetadata().putAll(dateInfo);
                return new Document(header + chunk.getText(), chunk.getMetadata());
            }).toList();

            allChunks.addAll(stampedChunks);
        });

        embeddingService.ingestFast(new ArrayList<>(allChunks));
    }

    /**
     * Fix 2: Build a short identity header that is prepended to every chunk's text before
     * embedding. The header survives retrieval and tells the LLM exactly which document
     * each piece of content belongs to — critical when multiple people's resumes are in
     * the store and share companies, dates, or skills.
     */
    private String buildChunkHeader(String fileName, String personName, String category) {
        StringBuilder header = new StringBuilder("[DOCUMENT: ");
        header.append(fileName != null ? fileName : "unknown");
        if (personName != null) {
            header.append(" | PERSON: ").append(personName);
        }
        header.append(" | CATEGORY: ").append(category);
        header.append("]\n\n");
        return header.toString();
    }

    /**
     * Fix 3: Derive a human-readable person name from a resume filename.
     * Handles common conventions:
     *   alice_smith_resume.pdf   → "Alice Smith"
     *   bob-jones-cv.pdf         → "Bob Jones"
     *   JohnDoe.pdf              → "John Doe"  (CamelCase split)
     * Returns null if the name cannot be extracted (non-resume docs pass through cleanly).
     */
    static String extractPersonName(String fileName) {
        if (fileName == null) return null;

        // Strip extension
        String base = fileName.replaceAll("\\.[^.]+$", "");

        // Remove common resume suffixes
        base = base.replaceAll("(?i)[_\\-\\s]*(resume|cv|curriculum.vitae|profile).*$", "");

        // Replace separators with spaces
        base = base.replace('_', ' ').replace('-', ' ');

        // Split CamelCase: "JohnDoe" → "John Doe"
        base = base.replaceAll("([a-z])([A-Z])", "$1 $2");

        // Collapse whitespace and trim
        base = base.replaceAll("\\s+", " ").trim();

        if (base.isEmpty()) return null;

        // Title-case each word
        String[] words = base.split(" ");
        StringBuilder name = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                if (!name.isEmpty()) name.append(" ");
                name.append(Character.toUpperCase(word.charAt(0)));
                if (word.length() > 1) name.append(word.substring(1).toLowerCase());
            }
        }
        return name.isEmpty() ? null : name.toString();
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
