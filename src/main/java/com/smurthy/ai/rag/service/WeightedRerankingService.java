package com.smurthy.ai.rag.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class WeightedRerankingService implements RerankingService {

    private static final Logger log = LoggerFactory.getLogger(WeightedRerankingService.class);

    // Re-balanced weights to give more importance to phrase matching
    private static final double VECTOR_SCORE_WEIGHT = 0.5;
    private static final double KEYWORD_SCORE_WEIGHT = 0.4;
    private static final double METADATA_SCORE_WEIGHT = 0.1;

    @Override
    public List<Document> rerank(String query, List<Document> documents, int topK) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }

        long startTime = System.currentTimeMillis();
        String normalizedQuery = query.toLowerCase();
        Set<String> queryTerms = extractTerms(normalizedQuery);

        List<ScoredDocument> scoredDocs = documents.stream()
                .map(doc -> scoreDocument(doc, normalizedQuery, queryTerms))
                .sorted(Comparator.comparingDouble(ScoredDocument::score).reversed())
                .limit(topK)
                .toList();

        long duration = System.currentTimeMillis() - startTime;
        log.debug("Reranked {} documents to top {} in {}ms.", documents.size(), topK, duration);

        return scoredDocs.stream()
                .map(sd -> {
                    Document doc = sd.document();
                    Map<String, Object> enrichedMetadata = new HashMap<>(doc.getMetadata());
                    enrichedMetadata.put("rerank_score", sd.score());
                    enrichedMetadata.put("original_similarity", sd.originalScore());
                    enrichedMetadata.put("keyword_boost", sd.keywordScore());
                    return Document.builder()
                            .id(doc.getId())
                            .text(doc.getText())
                            .metadata(enrichedMetadata)
                            .build();
                })
                .collect(Collectors.toList());
    }

    private ScoredDocument scoreDocument(Document doc, String normalizedQuery, Set<String> queryTerms) {
        double vectorScore = extractSimilarityScore(doc);
        // Pass the full normalized query to calculate a more accurate keyword score
        double keywordScore = calculateKeywordScore(doc.getText().toLowerCase(), normalizedQuery, queryTerms);
        double metadataScore = calculateMetadataScore(doc);

        double finalScore = (vectorScore * VECTOR_SCORE_WEIGHT) +
                           (keywordScore * KEYWORD_SCORE_WEIGHT) +
                           (metadataScore * METADATA_SCORE_WEIGHT);

        return new ScoredDocument(doc, finalScore, vectorScore, keywordScore, metadataScore);
    }

    private double extractSimilarityScore(Document doc) {
        Object similarityObj = doc.getMetadata().get("similarity");
        return (similarityObj instanceof Number) ? ((Number) similarityObj).doubleValue() : 0.5;
    }

    /**
     * Calculates a keyword score based on term overlap and a powerful phrase-matching boost.
     */
    private double calculateKeywordScore(String content, String normalizedQuery, Set<String> queryTerms) {
        if (queryTerms.isEmpty()) {
            return 0.0;
        }

        // 1. Term Overlap (simple count)
        Set<String> contentTerms = extractTerms(content);
        long matchingTerms = queryTerms.stream().filter(contentTerms::contains).count();
        double overlapRatio = (double) matchingTerms / queryTerms.size();

        // 2. Phrase Matching Boost (more intelligent)
        // Generate 2-word and 3-word phrases from the query to find exact matches.
        List<String> phrases = generateNgrams(normalizedQuery, 2, 3);
        double phraseBoost = 0.0;
        for (String phrase : phrases) {
            if (content.contains(phrase)) {
                // Give a significant boost for each matching phrase
                phraseBoost += 0.25; // Increased boost for strong signal
            }
        }

        // Combine scores, giving more weight to the phrase boost
        return Math.min(1.0, (overlapRatio * 0.4) + (phraseBoost * 0.6));
    }

    private double calculateMetadataScore(Document doc) {
        // This logic remains unchanged
        double score = 0.5;
        if (doc.getMetadata().containsKey("source")) score += 0.1;
        if (doc.getMetadata().containsKey("date") || doc.getMetadata().containsKey("timestamp")) score += 0.2;
        return Math.min(1.0, score);
    }

    private Set<String> extractTerms(String text) {
        String[] tokens = text.trim().replaceAll("[^a-z0-9\\s]", " ").split("\\s+");
        Set<String> stopWords = Set.of("the", "a", "an", "and", "or", "in", "on", "what", "was", "for");
        return Arrays.stream(tokens)
                .filter(token -> token.length() > 1)
                .filter(token -> !stopWords.contains(token))
                .collect(Collectors.toSet());
    }

    /**
     * Generates n-grams (phrases) from a text to be used for phrase matching.
     */
    private List<String> generateNgrams(String text, int minSize, int maxSize) {
        List<String> ngrams = new ArrayList<>();
        String[] words = text.split("\\s+");

        for (int n = minSize; n <= maxSize; n++) {
            for (int i = 0; i <= words.length - n; i++) {
                StringBuilder sb = new StringBuilder();
                for (int j = 0; j < n; j++) {
                    sb.append(words[i + j]).append(" ");
                }
                String ngram = sb.toString().trim();
                // Add the ngram if it contains meaningful terms
                if (!extractTerms(ngram).isEmpty()) {
                    ngrams.add(ngram);
                }
            }
        }
        return ngrams;
    }

    private record ScoredDocument(Document document, double score, double originalScore, double keywordScore, double metadataScore) {}
}