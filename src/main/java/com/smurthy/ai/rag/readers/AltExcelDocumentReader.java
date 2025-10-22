package com.smurthy.ai.rag.readers;

import org.apache.poi.ss.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class AltExcelDocumentReader implements DocumentReader {

    private static final Logger log = LoggerFactory.getLogger(AltExcelDocumentReader.class);

    @Override
    public boolean supports(Resource resource) {
        String filename = resource.getFilename();
        return filename != null && (filename.toLowerCase().endsWith(".xls") || filename.toLowerCase().endsWith(".xlsx"));
    }

    /**
     * Reads an Excel file and creates a separate Document for each row,
     * transforming the row's data into a context-rich sentence.
     */
    @Override
    public List<Document> read(Resource resource) {
        List<Document> documents = new ArrayList<>();
        try (InputStream inputStream = resource.getInputStream();
             Workbook workbook = WorkbookFactory.create(inputStream)) {

            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                Sheet sheet = workbook.getSheetAt(i);
                if (sheet.getPhysicalNumberOfRows() > 1) { // Ensure there's at least a header and one data row
                    documents.addAll(processSheet(sheet, resource));
                }
            }
        } catch (Exception e) {
            log.error("Failed to read Excel file: {}", resource.getFilename(), e);
            throw new RuntimeException("Failed to read Excel file: " + resource.getFilename(), e);
        }
        return documents;
    }

    /**
     * Processes a single sheet, creating one Document per data row.
     */
    private List<Document> processSheet(Sheet sheet, Resource resource) {
        List<Document> sheetDocuments = new ArrayList<>();
        Row headerRow = sheet.getRow(0);
        if (headerRow == null) {
            return sheetDocuments; // Cannot process without a header
        }

        // Read header titles
        List<String> headers = new ArrayList<>();
        for (Cell cell : headerRow) {
            headers.add(getCellValueAsString(cell));
        }

        // Process each data row (starting from the second row)
        for (int j = 1; j <= sheet.getLastRowNum(); j++) {
            Row dataRow = sheet.getRow(j);
            if (dataRow == null) continue;

            // Create a key-value map for the row
            Map<String, String> rowData = new HashMap<>();
            for (int k = 0; k < headers.size(); k++) {
                Cell cell = dataRow.getCell(k, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                if (cell != null) {
                    rowData.put(headers.get(k), getCellValueAsString(cell));
                }
            }

            if (!rowData.isEmpty()) {
                // Transform the row data into a natural language sentence
                String rowContent = formatRowAsSentence(rowData);

                // Create metadata for this specific row
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("source", resource.getFilename());
                metadata.put("sheet_name", sheet.getSheetName());
                metadata.put("row_index", j + 1);
                // Add all cell values to metadata for potential structured filtering
                rowData.forEach((key, value) -> metadata.put("excel_" + key.replaceAll("\\s+", "_").toLowerCase(), value));

                sheetDocuments.add(new Document(rowContent, metadata));
            }
        }
        log.info("Processed {} rows from sheet '{}' in file '{}'", sheetDocuments.size(), sheet.getSheetName(), resource.getFilename());
        return sheetDocuments;
    }

    /**
     * Formats a map of row data into a human-readable sentence.
     * Example: "For the record where Period is Q4 2023: the Metric is Sales and the Value is $500,000."
     */
    private String formatRowAsSentence(Map<String, String> rowData) {
        if (rowData.isEmpty()) {
            return "";
        }
        // Use the first column as the primary identifier for the sentence structure
        String firstColumnKey = rowData.keySet().iterator().next();
        String firstColumnValue = rowData.get(firstColumnKey);

        String details = rowData.entrySet().stream()
                .skip(1) // Skip the first entry as it's used in the intro
                .map(entry -> String.format("the %s is %s", entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(" and "));

        if (details.isEmpty()) {
            return String.format("Record: %s is %s.", firstColumnKey, firstColumnValue);
        }

        return String.format("For the record where %s is %s: %s.", firstColumnKey, firstColumnValue, details);
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }
}