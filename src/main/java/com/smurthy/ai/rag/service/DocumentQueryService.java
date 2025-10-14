package com.smurthy.ai.rag.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service to query document information from the vector store.
 */
@Service
public class DocumentQueryService {

    private final JdbcTemplate jdbcTemplate;

    public DocumentQueryService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Retrieves a distinct list of ingested document file names from the vector store.
     *
     * @return A list of unique document names.
     */
    public List<String> listIngestedDocuments() {
        return jdbcTemplate.queryForList("SELECT DISTINCT metadata ->> 'file_name' FROM vector_store ORDER BY 1", String.class);
    }

    /**
     * Retrieves a map of all distinct categories and the number of document chunks in each.
     *
     * @return A map where the key is the category name and the value is the count.
     */
    public Map<String, Long> getCategoryCounts() {
        String sql = "SELECT metadata ->> 'category' as category, COUNT(*) as count " +
                     "FROM vector_store " +
                     "GROUP BY category " +
                     "ORDER BY category";

        return jdbcTemplate.queryForList(sql).stream()
                .collect(Collectors.toMap(
                        map -> {
                            String category = (String) map.get("category");
                            // If a document has no category tag, the key will be null.
                            // Replace it with a placeholder to prevent JSON serialization errors.
                            return category == null ? "uncategorized" : category;
                        },
                        map -> (Long) map.get("count")
                ));
    }
}
