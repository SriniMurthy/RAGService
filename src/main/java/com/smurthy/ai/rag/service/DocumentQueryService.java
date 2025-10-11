package com.smurthy.ai.rag.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

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
     * This query works by selecting the 'file_name' key from the JSONB metadata
     * field in the 'vector_store' table.
     *
     * @return A list of unique document names.
     */
    public List<String> listIngestedDocuments() {
        // The table name 'vector_store' is the default used by Spring AI's PgVectorStore.
        // The metadata->>'file_name' expression extracts the value of the 'file_name' key
        // from the JSONB 'metadata' column.
        return jdbcTemplate.queryForList("SELECT DISTINCT metadata ->> 'file_name' FROM vector_store ORDER BY 1", String.class);
    }
}
