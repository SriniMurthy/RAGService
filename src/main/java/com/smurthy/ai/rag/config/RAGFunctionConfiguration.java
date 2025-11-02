package com.smurthy.ai.rag.config;

import com.smurthy.ai.rag.service.HybridRetrievalService;
import com.smurthy.ai.rag.service.TemporalQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Configuration class that exposes RAG and Temporal query capabilities as function tools.
 * This allows the AI agent to decide when to query documents vs. call external APIs.
 * Features:
 * - Documents can be PDFs, Word docs, Excel spreadsheets with rich metadata
 * - Each document chunk includes: source file, sheet name (for Excel), dates, page numbers
 * - Functions return FULL CONTEXT including metadata so LLM can cite sources
 * - Temporal queries work across ALL document types (Excel, PDF, Word)
 * - TODO: XL Spreadsheets yet to be tested for disparate data
 * - Uses HybridRetrievalService for improved retrieval quality and metrics tracking
 */
@Configuration
public class RAGFunctionConfiguration {

    private static final Logger log = LoggerFactory.getLogger(RAGFunctionConfiguration.class);

    private final VectorStore vectorStore;
    private final TemporalQueryService temporalQueryService;

    @Autowired(required = false)
    private HybridRetrievalService hybridRetrievalService;

    public RAGFunctionConfiguration(VectorStore vectorStore, TemporalQueryService temporalQueryService) {
        this.vectorStore = vectorStore;
        this.temporalQueryService = temporalQueryService;
    }

    // ========== REQUEST/RESPONSE RECORDS ==========

    /**
     * Standard document query - searches ALL documents (PDFs, Excel, Word, etc.)
     */
    public record DocumentQueryRequest(String question) {}

    /**
     * Advanced query with custom similarity threshold and result count
     */
    public record AdvancedDocumentQueryRequest(
            String question,
            int topK,
            double similarityThreshold
    ) {}

    /**
     * Rich response with full document context including metadata
     */
    public record DocumentQueryResponse(
            String question,
            int documentsFound,
            List<DocumentChunk> documents,
            String summary
    ) {
        /**
         * Each document chunk includes full context for the LLM
         */
        public record DocumentChunk(
                String content,              // The actual text content
                String sourceFile,           // e.g., "Q2_Sales_Report.xlsx"
                String documentType,         // e.g., "Excel", "PDF", "Word"
                String sheetName,            // For Excel: "Sales Data", "Summary"
                Integer sheetIndex,          // For Excel: sheet number
                String startDate,            // From metadata (if available)
                String endDate,              // From metadata (if available)
                Map<String, Object> rawMetadata  // Full metadata for debugging
        ) {
            /**
             * Format this chunk for display to the LLM with full context
             */
            public String toFormattedString() {
                StringBuilder sb = new StringBuilder();
                sb.append("=== DOCUMENT CHUNK ===\n");
                sb.append("Source: ").append(sourceFile).append("\n");
                sb.append("Type: ").append(documentType).append("\n");

                if (sheetName != null && !sheetName.isEmpty()) {
                    sb.append("Sheet: ").append(sheetName);
                    if (sheetIndex != null) {
                        sb.append(" (Sheet #").append(sheetIndex + 1).append(")");
                    }
                    sb.append("\n");
                }

                if (startDate != null || endDate != null) {
                    sb.append("Date Range: ");
                    if (startDate != null) sb.append(startDate);
                    if (startDate != null && endDate != null) sb.append(" to ");
                    if (endDate != null) sb.append(endDate);
                    sb.append("\n");
                }

                sb.append("\nCONTENT:\n");
                sb.append(content);
                sb.append("\n");

                return sb.toString();
            }
        }
    }

    /**
     * Temporal query by year
     */
    public record TemporalYearQueryRequest(String question, int year) {}

    /**
     * Temporal query by flexible date (year or MM-dd-yyyy)
     */
    public record TemporalQueryRequest(String question, String date) {}

    /**
     * Temporal query by date range
     */
    public record TemporalRangeQueryRequest(String question, String startDate, String endDate) {}

    // ========== CORE RAG FUNCTIONS ==========

    /**
     * PRIMARY RAG TOOL: Query all documents in the system.
     * This searches across ALL document types:
     * - Excel spreadsheets (with sheet context)
     * - PDF documents (with page numbers)
     * - Word documents
     * - Plain text files
     *
     * Returns rich context including source files, sheet names, dates, etc.
     */
    @Bean("queryDocuments")
    @Description("Search ALL documents in the system (Excel, PDF, Word, text files) for information. " +
            "Use this for questions about: people, resumes, companies, projects, financial data, reports, or ANY content in uploaded documents. " +
            "Returns full context including source files, Excel sheet names, dates, and metadata. " +
            "Examples: 'Who is John Doe?', 'What were Q2 sales?', 'Find projects from last year', 'What experience does Jane have?'")
    public Function<DocumentQueryRequest, DocumentQueryResponse> queryDocuments() {
        return request -> {
            log.info(" TOOL CALLED: queryDocuments(question='{}')", request.question());

            try {
                List<Document> documents;

                // Use hybrid retrieval if available, otherwise fall back to vector-only
                if (hybridRetrievalService != null) {
                    log.debug("Using HybridRetrievalService for enhanced retrieval");
                    HybridRetrievalService.HybridRetrievalResult result =
                        hybridRetrievalService.retrieve(request.question(), 10, 0.30);
                    documents = result.documents();
                } else {
                    log.debug("Using VectorStore for vector-only retrieval");
                    SearchRequest searchRequest = SearchRequest.builder()
                            .query(request.question())
                            .topK(10)
                            .similarityThreshold(0.30)
                            .build();
                    documents = vectorStore.similaritySearch(searchRequest);
                }

                return buildRichResponse(request.question(), documents);

            } catch (Exception e) {
                log.error("Error querying documents", e);
                return new DocumentQueryResponse(
                        request.question(),
                        0,
                        List.of(),
                        "Error querying documents: " + e.getMessage()
                );
            }
        };
    }

    /**
     * ADVANCED RAG TOOL: Query with custom similarity and result count.
     * Use when you need more control over the search parameters.
     */
    @Bean("queryDocumentsAdvanced")
    @Description("Advanced document search with custom similarity threshold and result count. " +
            "Use when you need: (1) More precise results (higher threshold), (2) More results (higher topK), or (3) Broader search (lower threshold). " +
            "Parameters: topK (1-20, default 10), similarityThreshold (0.0-1.0, default 0.30)")
    public Function<AdvancedDocumentQueryRequest, DocumentQueryResponse> queryDocumentsAdvanced() {
        return request -> {
            log.info(" TOOL CALLED: queryDocumentsAdvanced(question='{}', topK={}, threshold={})",
                    request.question(), request.topK(), request.similarityThreshold());

            try {
                int topK = Math.min(request.topK(), 20);  // Cap at 20 to avoid overwhelming context
                List<Document> documents;

                // Use hybrid retrieval if available, otherwise fall back to vector-only
                if (hybridRetrievalService != null) {
                    log.debug("Using HybridRetrievalService for enhanced retrieval");
                    HybridRetrievalService.HybridRetrievalResult result =
                        hybridRetrievalService.retrieve(request.question(), topK, request.similarityThreshold());
                    documents = result.documents();
                } else {
                    log.debug("Using VectorStore for vector-only retrieval");
                    SearchRequest searchRequest = SearchRequest.builder()
                            .query(request.question())
                            .topK(topK)
                            .similarityThreshold(request.similarityThreshold())
                            .build();
                    documents = vectorStore.similaritySearch(searchRequest);
                }

                return buildRichResponse(request.question(), documents);

            } catch (Exception e) {
                log.error("Error in advanced document query", e);
                return new DocumentQueryResponse(
                        request.question(),
                        0,
                        List.of(),
                        "Error: " + e.getMessage()
                );
            }
        };
    }

    // ========== TEMPORAL QUERY FUNCTIONS ==========

    /**
     * Query documents from a specific YEAR.
     * Works across all document types - filters by metadata dates.
     */
    @Bean("queryDocumentsByYear")
    @Description("Search documents from a specific YEAR (e.g., 2021, 2023). " +
            "Works with Excel, PDF, Word - any document with date metadata. " +
            "Use for questions like: 'What happened in 2021?', 'Show me 2023 sales data', 'Projects from 2022'. " +
            "Returns documents where the date range overlaps with the specified year.")
    public Function<TemporalYearQueryRequest, DocumentQueryResponse> queryDocumentsByYear() {
        return request -> {
            log.info(" TOOL CALLED: queryDocumentsByYear(question='{}', year={})",
                    request.question(), request.year());

            try {
                List<Document> documents = temporalQueryService.findDocumentsByYear(
                        request.question(),
                        request.year()
                );

                return buildRichResponse(
                        request.question() + " [YEAR: " + request.year() + "]",
                        documents
                );

            } catch (Exception e) {
                log.error("Error in temporal query by year", e);
                return new DocumentQueryResponse(
                        request.question(),
                        0,
                        List.of(),
                        " Error querying year " + request.year() + ": " + e.getMessage()
                );
            }
        };
    }

    /**
     * Query documents by flexible date format.
     * Accepts: "2021" (year) OR "01-02-2021" (MM-dd-yyyy specific date)
     */
    @Bean("queryDocumentsByDate")
    @Description("Search documents by flexible date: either YEAR ('2021') or SPECIFIC DATE ('01-15-2021' in MM-dd-yyyy format). " +
            "Auto-detects format. Use for: 'What happened on 01-15-2021?', 'Documents from 2022', 'Show me March 2023 data'. " +
            "Works across Excel, PDF, Word documents with date metadata.")
    public Function<TemporalQueryRequest, DocumentQueryResponse> queryDocumentsByDate() {
        return request -> {
            log.info(" TOOL CALLED: queryDocumentsByDate(question='{}', date='{}')",
                    request.question(), request.date());

            try {
                List<Document> documents = temporalQueryService.findDocumentsByFlexibleDate(
                        request.question(),
                        request.date()
                );

                return buildRichResponse(
                        request.question() + " [DATE: " + request.date() + "]",
                        documents
                );

            } catch (Exception e) {
                log.error("Error in flexible date query", e);
                return new DocumentQueryResponse(
                        request.question(),
                        0,
                        List.of(),
                        " Error with date '" + request.date() + "': " + e.getMessage()
                );
            }
        };
    }

    /**
     * Query documents within a DATE RANGE.
     * Use ISO dates (YYYY-MM-DD)
     */
    @Bean("queryDocumentsByDateRange")
    @Description("Search documents within a DATE RANGE using ISO format (YYYY-MM-DD). " +
            "Example: startDate='2021-01-01', endDate='2021-06-30' for first half of 2021. " +
            "Use for questions like: 'What happened between Jan and Jun 2021?', 'Q1 2023 reports', 'Documents from last quarter'. " +
            "Finds documents where date ranges overlap with the query range.")
    public Function<TemporalRangeQueryRequest, DocumentQueryResponse> queryDocumentsByDateRange() {
        return request -> {
            log.info("TOOL CALLED: queryDocumentsByDateRange(question='{}', from='{}', to='{}')",
                    request.question(), request.startDate(), request.endDate());

            try {
                LocalDate startDate = LocalDate.parse(request.startDate(), DateTimeFormatter.ISO_LOCAL_DATE);
                LocalDate endDate = LocalDate.parse(request.endDate(), DateTimeFormatter.ISO_LOCAL_DATE);

                List<Document> documents = temporalQueryService.findDocumentsByDateRange(
                        request.question(),
                        startDate,
                        endDate
                );

                return buildRichResponse(
                        request.question() + " [RANGE: " + request.startDate() + " to " + request.endDate() + "]",
                        documents
                );

            } catch (Exception e) {
                log.error("Error in date range query", e);
                return new DocumentQueryResponse(
                        request.question(),
                        0,
                        List.of(),
                        " Error with date range: " + e.getMessage()
                );
            }
        };
    }

    // ========== HELPER METHODS ==========

    /**
     * Build a rich response with full document context.
     * Extracts metadata like source file, sheet names, dates, etc.
     */
    private DocumentQueryResponse buildRichResponse(String question, List<Document> documents) {
        if (documents.isEmpty()) {
            log.warn("No documents found for query: {}", question);
            return new DocumentQueryResponse(
                    question,
                    0,
                    List.of(),
                    "No relevant documents found in the system for this query. " +
                    "The document store may not contain information about this topic."
            );
        }

        // Convert Spring AI Documents to rich DocumentChunks
        List<DocumentQueryResponse.DocumentChunk> chunks = documents.stream()
                .map(this::convertToDocumentChunk)
                .collect(Collectors.toList());

        // Build summary with statistics
        String summary = buildSummary(chunks);

        log.info("Found {} documents", documents.size());

        return new DocumentQueryResponse(
                question,
                documents.size(),
                chunks,
                summary
        );
    }

    /**
     * Convert Spring AI Document to rich DocumentChunk with extracted metadata
     */
    private DocumentQueryResponse.DocumentChunk convertToDocumentChunk(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();

        String sourceFile = extractMetadata(metadata, "source", "filename", "file");
        String sheetName = extractMetadata(metadata, "sheet_name", "sheet", "tab");
        Integer sheetIndex = extractIntMetadata(metadata, "sheet_index", "sheet_number");
        String startDate = extractMetadata(metadata, "start_date", "date_from", "from_date");
        String endDate = extractMetadata(metadata, "end_date", "date_to", "to_date");

        // Infer document type from source file extension or metadata
        String docType = inferDocumentType(sourceFile, metadata);

        return new DocumentQueryResponse.DocumentChunk(
                doc.getText(),
                sourceFile != null ? sourceFile : "Unknown Source",
                docType,
                sheetName,
                sheetIndex,
                startDate,
                endDate,
                metadata
        );
    }

    /**
     * Extract metadata value trying multiple possible keys
     */
    private String extractMetadata(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value != null) {
                return value.toString();
            }
        }
        return null;
    }

    /**
     * Extract integer metadata
     */
    private Integer extractIntMetadata(Map<String, Object> metadata, String... keys) {
        for (String key : keys) {
            Object value = metadata.get(key);
            if (value instanceof Integer) {
                return (Integer) value;
            } else if (value instanceof String) {
                try {
                    return Integer.parseInt((String) value);
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }
        }
        return null;
    }

    /**
     * Infer document type from filename or metadata
     */
    private String inferDocumentType(String filename, Map<String, Object> metadata) {
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".xlsx") || lower.endsWith(".xls")) return "Excel Spreadsheet";
            if (lower.endsWith(".pdf")) return "PDF Document";
            if (lower.endsWith(".docx") || lower.endsWith(".doc")) return "Word Document";
            if (lower.endsWith(".txt")) return "Text File";
            if (lower.endsWith(".csv")) return "CSV File";
        }

        // Check metadata for type hints
        Object typeHint = metadata.get("document_type");
        if (typeHint != null) {
            return typeHint.toString();
        }

        // Check if it has sheet_name (indicates Excel)
        if (metadata.containsKey("sheet_name")) {
            return "Excel Spreadsheet";
        }

        return "Document";
    }

    /**
     * Build a summary of the search results
     */
    private String buildSummary(List<DocumentQueryResponse.DocumentChunk> chunks) {
        StringBuilder summary = new StringBuilder();
        summary.append("Found ").append(chunks.size()).append(" relevant document chunk(s).\n\n");

        // Group by source file
        Map<String, Long> fileGroups = chunks.stream()
                .collect(Collectors.groupingBy(
                        DocumentQueryResponse.DocumentChunk::sourceFile,
                        Collectors.counting()
                ));

        summary.append("📁 Sources:\n");
        fileGroups.forEach((file, count) -> {
            summary.append("  - ").append(file).append(" (").append(count).append(" chunk");
            if (count > 1) summary.append("s");
            summary.append(")\n");
        });

        // List unique document types
        List<String> types = chunks.stream()
                .map(DocumentQueryResponse.DocumentChunk::documentType)
                .distinct()
                .toList();

        if (!types.isEmpty()) {
            summary.append("\n📄 Document Types: ").append(String.join(", ", types));
        }

        summary.append("\n\n💡 Use the information below to answer the user's question. Each chunk includes source citations.");

        return summary.toString();
    }
}
