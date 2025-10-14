package com.smurthy.ai.rag.service;

import com.smurthy.ai.rag.readers.DocumentReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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

    /**
     * Checks if a document from a specific resource already exists in the vector store
     * and loads it if it does not, tagging it with the provided category.
     *
     * @param resource The resource to load.
     * @param category The category to assign to the ingested document chunks.
     */
    public void ingest(Resource resource, String category) {
        String resourceName = resource.getFilename();
        if (resourceName == null) {
            log.warn("Cannot process a resource without a filename: {}", resource);
            return;
        }

        // Check if this specific file has already been ingested.
        String sql = "SELECT COUNT(*) FROM vector_store WHERE metadata ->> 'file_name' = :fileName";
        Integer count = jdbcClient.sql(sql)
                .param("fileName", resourceName)
                .query(Integer.class)
                .single();

        if (count == 0) {
            log.info("Ingesting new document: '{}' into category '{}'", resourceName, category);
            documentReaders.stream()
                    .filter(reader -> reader.supports(resource))
                    .findFirst()
                    .ifPresentOrElse(reader -> {
                        cleanAndEmbedDocument(resource, reader, textSplitter, category);
                    }, () -> log.warn("No reader found for resource '{}'. Skipping.", resourceName));
        } else {
            log.debug("Documents from '{}' are already loaded ({} records found). Skipping.", resourceName, count);
        }
    }

    private void cleanAndEmbedDocument(Resource resource, DocumentReader reader, TextSplitter textSplitter, String category) {
        // Read all documents from the resource
        List<Document> documents = reader.read(resource);

        // Clean and split all documents into a single list of chunks
        List<Document> allChunks = new ArrayList<>();
        log.debug("✓ Cleaning and Splitting documents...");
        documents.parallelStream().forEach(doc -> {
            var cleanedDocument = this.cleanDocument(doc);
            List<Document> chunks = textSplitter.apply(List.of(cleanedDocument));

            // Add metadata (category and filename) to each chunk
            chunks.forEach(chunk -> {
                chunk.getMetadata().put("category", category);
                if (resource.getFilename() != null) {
                    chunk.getMetadata().put("file_name", resource.getFilename());
                }
            });

            allChunks.addAll(chunks);
        });
        log.debug("✓ Created {} chunks with category '{}'.", allChunks.size(), category);

        // Ingest all chunks in a single, efficient batch operation
        log.debug("✓ Handing off to fast embedding service...");
        embeddingService.ingestFast(allChunks);
    }

    protected static Document cleanDocument(Document doc) {
        String cleanedText = cleanText(doc.getText());
        return new Document(cleanedText, doc.getMetadata());
    }

    private static String cleanText(String text) {
        if (text == null) return "";
        return text
                .replaceAll("[^\\x00-\\x7F]", " ")  // Remove non-ASCII
                .replaceAll("\\p{C}", " ")           // Remove control characters
                .replaceAll("\\s+", " ")             // Normalize whitespace
                .trim();
    }
}
