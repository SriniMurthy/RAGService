package com.smurthy.ai.rag.service;

import com.smurthy.ai.rag.readers.DocumentReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TextSplitter;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DocumentIngestionService.class);

    private final VectorStore vectorStore;
    private final JdbcClient jdbcClient;
    private final TextSplitter textSplitter;
    private final List<DocumentReader> documentReaders;
    private final FastDocumentIngestionService embeddingService;

    public DocumentIngestionService(VectorStore vectorStore, JdbcClient jdbcClient, TextSplitter textSplitter, List<DocumentReader> documentReaders,
                                    FastDocumentIngestionService embeddingService) {
        this.vectorStore = vectorStore;
        this.jdbcClient = jdbcClient;
        this.textSplitter = textSplitter;
        this.documentReaders = documentReaders;
        this.embeddingService = embeddingService;
    }

    /**
     * Checks if documents from a specific resource already exist in the vector store
     * and loads them if they do not.
     *
     * @param resource The resource to load.
     */
    public void ingest(Resource resource) {
        String resourceName = resource.getFilename();
        if (resourceName == null) {
            log.warn("Cannot process a resource without a filename: {}", resource);
            return;
        }




        String sql = "SELECT COUNT(*) FROM vector_store WHERE metadata ->> 'source' LIKE :source";
        Integer count = jdbcClient.sql(sql)
                .param("source", "%" + resourceName + "%")
                .query(Integer.class)
                .single();

        if (count == 0) {
            log.info("Ingesting new document: '{}'", resourceName);
           // var textSplitter = new TokenTextSplitter();
            documentReaders.parallelStream()
                    .filter(reader -> reader.supports(resource))
                    .findFirst()
                    .ifPresentOrElse(reader -> {
                        cleanAndEmbedDocument(resource, reader, textSplitter);
                    }, () -> log.warn("No reader found for resource '{}'. Skipping.", resourceName));
        } else {
            log.debug("Documents from '{}' are already loaded ({} records found). Skipping.", resourceName, count);
        }
    }

    private void cleanAndEmbedDocument(Resource resource, DocumentReader reader, TextSplitter textSplitter) {
        // 1. Read all documents from the resource
        List<Document> documents = reader.read(resource);

        // 2. Clean and split all documents into a single list of chunks
        List<Document> allChunks = new ArrayList<>();
        log.debug("✓ Cleaning and Splitting documents...");
        documents.parallelStream().forEach(doc -> {
            var cleanedDocument = this.cleanDocument(doc);
            allChunks.addAll(textSplitter.apply(List.of(cleanedDocument)));
        });
        log.debug("✓ Created " + allChunks.size() + " chunks.");

        // 3. Ingest all chunks in a single, efficient batch operation
        log.debug("✓ Handing off to fast embedding service...");
        embeddingService.ingestFast(allChunks);
    }

    protected static  Document cleanDocument(Document doc) {
        String cleanedText = cleanText(doc.getText());
        return Document.builder()
                .text(cleanedText)
                .metadata(doc.getMetadata())
                .build();
    }

    private static String cleanText(String text) {
        if (text == null) return "";
        // Remove non-UTF-8 characters and normalize
        return text
                // Replace common problematic characters
                .replaceAll("[^\\x00-\\x7F]", " ")  // Remove non-ASCII
                .replaceAll("\\p{C}", " ")           // Remove control characters
                // OR keep some Unicode but remove problematic ones:
                // .replaceAll("[\\p{So}\\p{Cn}]", " ") // Remove symbols and undefined
                .replaceAll("\\s+", " ")             // Normalize whitespace
                .trim();
    }
}