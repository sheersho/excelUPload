package org.example.excel;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.example.config.AppConfig;
import org.example.model.ColumnMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ExcelReader {

    private static final Logger log = LoggerFactory.getLogger(ExcelReader.class);
    private final DataFormatter dataFormatter = new DataFormatter();

    public List<String> readHeaders(Path excelFile, String sheetName, int headerRowIndex) throws IOException {
        try (InputStream in = Files.newInputStream(excelFile);
             Workbook workbook = WorkbookFactory.create(in)) {
            Sheet sheet = resolveSheet(workbook, sheetName != null ? sheetName : "");
            Row headerRow = sheet.getRow(headerRowIndex);
            if (headerRow == null) {
                log.warn("No header row found in file={} at rowIndex={}", excelFile, headerRowIndex);
                return List.of();
            }
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                String h = dataFormatter.formatCellValue(headerRow.getCell(i)).trim();
                if (!h.isEmpty()) headers.add(h);
            }
            log.info("Headers read from file={}: count={}", excelFile.getFileName(), headers.size());
            return headers;
        }
    }

    public List<Map<String, String>> readRows(Path excelFile, AppConfig config) throws IOException {
        try (InputStream in = Files.newInputStream(excelFile);
             Workbook workbook = WorkbookFactory.create(in)) {

            Sheet sheet = resolveSheet(workbook, config.sheetName());
            Row headerRow = sheet.getRow(config.headerRowIndex());
            if (headerRow == null) {
                throw new IllegalArgumentException("Header row was not found at index " + config.headerRowIndex());
            }

            Map<String, Integer> headerIndex = indexHeaders(headerRow);
            List<Map<String, String>> rows = new ArrayList<>();

            for (int rowIndex = config.startDataRowIndex(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) {
                    continue;
                }

                Map<String, String> mappedRow = mapRow(row, config.columnMappings(), headerIndex);
                if (!mappedRow.isEmpty()) {
                    rows.add(mappedRow);
                }
            }

            log.info("Rows read from file={}: rowsRead={}, sheet={}", excelFile.getFileName(), rows.size(), sheet.getSheetName());
            return rows;
        }
    }

    private Sheet resolveSheet(Workbook workbook, String sheetName) {
        if (!sheetName.isBlank()) {
            Sheet namedSheet = workbook.getSheet(sheetName);
            if (namedSheet == null) {
                throw new IllegalArgumentException("Sheet '" + sheetName + "' was not found.");
            }
            return namedSheet;
        }

        Sheet firstSheet = workbook.getNumberOfSheets() > 0 ? workbook.getSheetAt(0) : null;
        if (firstSheet == null) {
            throw new IllegalArgumentException("Workbook contains no sheets.");
        }
        return firstSheet;
    }

    private Map<String, Integer> indexHeaders(Row headerRow) {
        Map<String, Integer> index = new HashMap<>();
        for (int cellIndex = 0; cellIndex < headerRow.getLastCellNum(); cellIndex++) {
            String header = dataFormatter.formatCellValue(headerRow.getCell(cellIndex)).trim();
            if (!header.isEmpty()) {
                index.put(header, cellIndex);
            }
        }
        return index;
    }

    private Map<String, String> mapRow(Row row, List<ColumnMapping> mappings, Map<String, Integer> headerIndex) {
        Map<String, String> values = new LinkedHashMap<>();
        boolean hasData = false;

        for (ColumnMapping mapping : mappings) {
            Integer columnIndex = headerIndex.get(mapping.excelHeader());
            if (columnIndex == null) {
                throw new IllegalArgumentException("Excel header not found: '" + mapping.excelHeader() + "'");
            }

            String value = dataFormatter.formatCellValue(row.getCell(columnIndex)).trim();
            if (!value.isEmpty()) {
                hasData = true;
            }
            values.put(mapping.dbColumn(), value);
        }

        return hasData ? values : Map.of();
    }
}
