package com.smurthy.ai.rag.readers;

import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;

import java.util.List;

/**
 * Defines a strategy for reading a specific type of document from a resource.
 */
public interface DocumentReader {

    /**
     * Checks if this reader can handle the given resource.
     * @param resource The resource to check.
     * @return true if the resource is supported, false otherwise.
     */
    boolean supports(Resource resource);

    /**
     * Reads the content of the resource and converts it into a list of Documents.
     * @param resource The resource to read.
     * @return A list of {@link Document} objects.
     */
    List<Document> read(Resource resource);
}