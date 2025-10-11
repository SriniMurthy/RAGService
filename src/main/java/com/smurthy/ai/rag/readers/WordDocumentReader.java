package com.smurthy.ai.rag.readers;

import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
public class WordDocumentReader implements DocumentReader {

    private static final Logger log = LoggerFactory.getLogger(WordDocumentReader.class);

    @Override
    public boolean supports(Resource resource) {
        String filename = resource.getFilename();
        return filename != null && (filename.toLowerCase().endsWith(".doc") || filename.toLowerCase().endsWith(".docx"));
    }

    @Override
    public List<Document> read(Resource resource) {
        String content;
        try (InputStream inputStream = resource.getInputStream()) {
            String filename = resource.getFilename().toLowerCase();
            if (filename.endsWith(".docx")) {
                try (XWPFDocument doc = new XWPFDocument(inputStream);
                     XWPFWordExtractor extractor = new XWPFWordExtractor(doc)) {
                    content = extractor.getText();
                }
            } else { // .doc
                try (WordExtractor extractor = new WordExtractor(inputStream)) {
                    content = extractor.getText();
                }
            }
            return List.of(new Document(content, Map.of("source", resource.getFilename())));
        } catch (Exception e) {
            log.error("Failed to read Word document: {}", resource.getFilename(), e);
            throw new RuntimeException("Failed to read Word document: " + resource.getFilename(), e);
        }
    }
}