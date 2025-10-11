package com.smurthy.ai.rag.readers;

import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ExcelDocumentReader implements DocumentReader {

    private static final Logger log = LoggerFactory.getLogger(ExcelDocumentReader.class);

    @Override
    public boolean supports(Resource resource) {
        String filename = resource.getFilename();
        return filename != null && (filename.toLowerCase().endsWith(".xls") || filename.toLowerCase().endsWith(".xlsx"));
    }

    @Override
    public List<Document> read(Resource resource) {
        List<Document> documents = new ArrayList<>();
        try (InputStream inputStream = resource.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                StringBuilder sheetContent = new StringBuilder();
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        sheetContent.append(getCellValueAsString(cell)).append("\t");
                    }
                    sheetContent.append(System.lineSeparator());
                }

                if (!sheetContent.toString().isBlank()) {
                    Map<String, Object> metadata = Map.of(
                            "source", resource.getFilename(),
                            "sheet_name", sheet.getSheetName(),
                            "sheet_index", i
                    );
                    documents.add(new Document(sheetContent.toString(), metadata));
                }
            }
        } catch (Exception e) {
            log.error("Failed to read Excel file: {}", resource.getFilename(), e);
            throw new RuntimeException("Failed to read Excel file: " + resource.getFilename(), e);
        }
        return documents;
    }

    private String getCellValueAsString(Cell cell) {
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell);
    }
}