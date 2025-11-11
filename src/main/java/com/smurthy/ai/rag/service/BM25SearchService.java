package com.smurthy.ai.rag.service;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * BM25 Sparse Search Service for keyword based retrieval
 * BM25 parameters: k1=1.2, b=0.75 (standard tuning)
 */
@Service
public class BM25SearchService {

    private static final Logger log = LoggerFactory.getLogger(BM25SearchService.class);

    private final Directory indexDirectory;
    private final Analyzer analyzer;
    private IndexWriter indexWriter;
    private DirectoryReader indexReader;
    private IndexSearcher indexSearcher;

    // Track indexed document IDs to prevent duplicates
    private final Set<String> indexedDocIds = new HashSet<>();

    public BM25SearchService() {
        this.indexDirectory = new ByteBuffersDirectory(); // In-memory index
        this.analyzer = new StandardAnalyzer();

        try {
            initializeIndex();
        } catch (IOException e) {
            log.error("Failed to initialize BM25 index", e);
            throw new RuntimeException("BM25 index initialization failed", e);
        }
    }

    /**
     * Initialize the Lucene index with BM25 similarity
     */
    private void initializeIndex() throws IOException {
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        config.setSimilarity(new BM25Similarity(1.2f, 0.75f)); // Standard BM25 parameters
        config.setOpenMode(IndexWriterConfig.OpenMode.CREATE_OR_APPEND);

        this.indexWriter = new IndexWriter(indexDirectory, config);
        this.indexWriter.commit();

        refreshReader();
        log.info("BM25 search index initialized with BM25Similarity");
    }

    /**
     * Index a batch of Spring AI documents for BM25 search
     */
    public synchronized void indexDocuments(List<org.springframework.ai.document.Document> documents) throws IOException {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        long startTime = System.currentTimeMillis();
        int indexed = 0;

        for (org.springframework.ai.document.Document doc : documents) {
            // Skip if already indexed
            if (indexedDocIds.contains(doc.getId())) {
                continue;
            }

            Document luceneDoc = new Document();
            luceneDoc.add(new StringField("id", doc.getId(), Field.Store.YES));
            assert doc.getText() != null;
            luceneDoc.add(new TextField("content", doc.getText(), Field.Store.YES));

            // Index metadata for filtering
            doc.getMetadata().forEach((key, value) -> {
                if (value != null) {
                    luceneDoc.add(new TextField("metadata_" + key, value.toString(), Field.Store.YES));
                }
            });

            indexWriter.addDocument(luceneDoc);
            indexedDocIds.add(doc.getId());
            indexed++;
        }

        indexWriter.commit();
        refreshReader();

        long duration = System.currentTimeMillis() - startTime;
        log.info("Indexed {} documents for BM25 search in {}ms (total: {})",
                indexed, duration, indexedDocIds.size());
    }

    /**
     * Search documents using BM25 scoring
     *
     * @param query The search query
     * @param topK Number of results to return
     * @return List of document IDs with BM25 scores
     */
    public List<BM25Result> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        try {
            long startTime = System.currentTimeMillis();

            QueryParser parser = new QueryParser("content", analyzer);
            Query luceneQuery = parser.parse(QueryParser.escape(query));

            TopDocs topDocs = indexSearcher.search(luceneQuery, topK);

            List<BM25Result> results = new ArrayList<>();
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = indexSearcher.storedFields().document(scoreDoc.doc);
                String docId = doc.get("id");
                float score = scoreDoc.score;

                results.add(new BM25Result(docId, score));
            }

            long duration = System.currentTimeMillis() - startTime;
            log.debug("BM25 search for '{}' returned {} results in {}ms",
                    query, results.size(), duration);

            return results;

        } catch (ParseException | IOException e) {
            log.error("BM25 search failed for query: {}", query, e);
            return List.of();
        }
    }

    /**
     * Clear the index and reset
     */
    public synchronized void clearIndex() throws IOException {
        indexWriter.deleteAll();
        indexWriter.commit();
        indexedDocIds.clear();
        refreshReader();
        log.info("BM25 index cleared");
    }

    /**
     * Refresh the index reader to see new documents
     */
    private void refreshReader() throws IOException {
        if (indexReader != null) {
            DirectoryReader newReader = DirectoryReader.openIfChanged(indexReader);
            if (newReader != null) {
                indexReader.close();
                indexReader = newReader;
            }
        } else {
            indexReader = DirectoryReader.open(indexDirectory);
        }

        this.indexSearcher = new IndexSearcher(indexReader);
        this.indexSearcher.setSimilarity(new BM25Similarity(1.2f, 0.75f));
    }

    /**
     * Get index statistics
     */
    public Map<String, Object> getIndexStats() {
        try {
            return Map.of(
                    "totalDocuments", indexedDocIds.size(),
                    "indexSizeBytes", indexDirectory.listAll().length,
                    "similarity", "BM25(k1=1.2, b=0.75)"
            );
        } catch (Exception e) {
            log.error("Failed to get index stats", e);
            return Map.of("error", e.getMessage());
        }
    }

    /**
     * BM25 search result with document ID and score
     */
    public record BM25Result(String documentId, float score) {}
}