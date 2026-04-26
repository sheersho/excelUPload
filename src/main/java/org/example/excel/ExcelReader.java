package org.example.excel;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.example.config.AppConfig;
import org.example.model.ColumnMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads Excel files row by row.
 *
 * XLSX: uses SAX streaming parser (XSSFSheetXMLHandler) - processes one row at a time,
 *       no full workbook loaded into heap. Handles files of any size.
 * XLS:  falls back to DOM (HSSFWorkbook) since SAX model is not available for legacy format.
 */
public class ExcelReader {

    private static final Logger log = LoggerFactory.getLogger(ExcelReader.class);

    // ─────────────────────── Public API ────────────────────────

    public List<String> readHeaders(Path excelFile, String sheetName, int headerRowIndex) throws IOException {
        if (isXls(excelFile)) {
            return readHeadersDom(excelFile, sheetName, headerRowIndex);
        }
        try {
            HeaderCollector collector = new HeaderCollector(headerRowIndex);
            runStreamingParser(excelFile, sheetName != null ? sheetName : "", collector);
            trimTrailingEmpty(collector.headers);
            log.info("Headers read from file={}: count={}", excelFile.getFileName(), collector.headers.size());
            return Collections.unmodifiableList(collector.headers);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to read headers from " + excelFile.getFileName() + ": " + e.getMessage(), e);
        }
    }

    public List<Map<String, String>> readRows(Path excelFile, AppConfig config) throws IOException {
        if (isXls(excelFile)) {
            return readRowsDom(excelFile, config);
        }
        try {
            RowCollector collector = new RowCollector(
                    config.headerRowIndex(),
                    config.startDataRowIndex(),
                    config.columnMappings()
            );
            runStreamingParser(excelFile, config.sheetName(), collector);
            if (collector.missedHeader != null) {
                throw new IllegalArgumentException("Excel header not found: '" + collector.missedHeader + "'");
            }
            log.info("Rows read from file={}: rowsRead={}", excelFile.getFileName(), collector.rows.size());
            return collector.rows;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to read rows from " + excelFile.getFileName() + ": " + e.getMessage(), e);
        }
    }

    // ─────────────────────── Streaming XLSX ────────────────────────

    private void runStreamingParser(Path excelFile, String sheetName, SheetContentsHandler handler) throws Exception {
        // PackageAccess.READ avoids write-temp-file buffer allocation — saves significant heap
        try (OPCPackage pkg = OPCPackage.open(excelFile.toString(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(pkg);
            StylesTable styles = reader.getStylesTable();
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            try (InputStream sheetStream = resolveSheetStream(reader, sheetName != null ? sheetName : "")) {
                DataFormatter formatter = new DataFormatter();
                ContentHandler contentHandler = new XSSFSheetXMLHandler(
                        styles, null, strings, handler, formatter, false);
                XMLReader parser = XMLHelper.newXMLReader();
                parser.setContentHandler(contentHandler);
                parser.parse(new InputSource(sheetStream));
            }
        }
    }

    private InputStream resolveSheetStream(XSSFReader reader, String sheetName) throws Exception {
        XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
        if (!sheetName.isBlank()) {
            while (sheets.hasNext()) {
                InputStream is = sheets.next();
                if (sheetName.equalsIgnoreCase(sheets.getSheetName())) {
                    return is;
                }
                is.close();
            }
            throw new IllegalArgumentException("Sheet '" + sheetName + "' was not found.");
        }
        if (sheets.hasNext()) {
            return sheets.next();
        }
        throw new IllegalArgumentException("Workbook contains no sheets.");
    }

    // ─────────────────────── SAX Handlers ────────────────────────

    /** Collects only the header row values. */
    private static final class HeaderCollector implements SheetContentsHandler {
        private final int targetRow;
        final List<String> headers = new ArrayList<>();
        private int currentRow = -1;

        HeaderCollector(int targetRow) { this.targetRow = targetRow; }

        @Override public void startRow(int rowNum) { currentRow = rowNum; }
        @Override public void endRow(int rowNum) {}

        @Override
        public void cell(String cellRef, String formattedValue, XSSFComment comment) {
            if (currentRow == targetRow && cellRef != null) {
                int col = new CellReference(cellRef).getCol();
                while (headers.size() <= col) headers.add("");
                headers.set(col, formattedValue != null ? formattedValue.trim() : "");
            }
        }
    }

    /**
     * Single-pass handler: captures header row to build column map,
     * then maps each data row to DB columns on the fly.
     */
    private static final class RowCollector implements SheetContentsHandler {
        private final int headerRowIndex;
        private final int startDataRowIndex;
        private final List<ColumnMapping> mappings;

        final List<Map<String, String>> rows = new ArrayList<>();
        String missedHeader = null;

        private Map<Integer, String> colIndexToDbColumn;
        private final List<String> rawHeaders = new ArrayList<>();

        private int currentRow = -1;
        private Map<String, String> currentMappedRow;
        private boolean currentHasData;

        RowCollector(int headerRowIndex, int startDataRowIndex, List<ColumnMapping> mappings) {
            this.headerRowIndex = headerRowIndex;
            this.startDataRowIndex = startDataRowIndex;
            this.mappings = mappings;
        }

        @Override
        public void startRow(int rowNum) {
            currentRow = rowNum;
            currentMappedRow = null;
            currentHasData = false;
            if (rowNum >= startDataRowIndex && colIndexToDbColumn != null) {
                currentMappedRow = new LinkedHashMap<>();
                for (ColumnMapping m : mappings) currentMappedRow.put(m.dbColumn(), "");
            }
        }

        @Override
        public void endRow(int rowNum) {
            if (rowNum == headerRowIndex) buildColumnMap();
            if (currentMappedRow != null && currentHasData) rows.add(currentMappedRow);
            currentMappedRow = null;
        }

        @Override
        public void cell(String cellRef, String formattedValue, XSSFComment comment) {
            if (cellRef == null) return;
            int col = new CellReference(cellRef).getCol();
            if (currentRow == headerRowIndex) {
                while (rawHeaders.size() <= col) rawHeaders.add("");
                rawHeaders.set(col, formattedValue != null ? formattedValue.trim() : "");
            } else if (currentMappedRow != null && colIndexToDbColumn != null) {
                String dbCol = colIndexToDbColumn.get(col);
                if (dbCol != null) {
                    String val = formattedValue != null ? formattedValue.trim() : "";
                    currentMappedRow.put(dbCol, val);
                    if (!val.isEmpty()) currentHasData = true;
                }
            }
        }

        private void buildColumnMap() {
            Map<String, Integer> headerToCol = new HashMap<>();
            for (int i = 0; i < rawHeaders.size(); i++) {
                String h = rawHeaders.get(i);
                if (!h.isEmpty()) headerToCol.put(h, i);
            }
            colIndexToDbColumn = new HashMap<>();
            for (ColumnMapping m : mappings) {
                Integer colIdx = headerToCol.get(m.excelHeader());
                if (colIdx == null) {
                    missedHeader = m.excelHeader();
                    colIndexToDbColumn = null;
                    return;
                }
                colIndexToDbColumn.put(colIdx, m.dbColumn());
            }
        }
    }

    // ─────────────────────── DOM fallback (XLS) ────────────────────────

    private List<String> readHeadersDom(Path excelFile, String sheetName, int headerRowIndex) throws IOException {
        DataFormatter fmt = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(excelFile.toFile(), null, true)) {
            Sheet sheet = resolveSheetDom(workbook, sheetName != null ? sheetName : "");
            Row headerRow = sheet.getRow(headerRowIndex);
            if (headerRow == null) return List.of();
            List<String> headers = new ArrayList<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                String h = fmt.formatCellValue(headerRow.getCell(i)).trim();
                if (!h.isEmpty()) headers.add(h);
            }
            log.info("Headers read from file={}: count={}", excelFile.getFileName(), headers.size());
            return headers;
        }
    }

    private List<Map<String, String>> readRowsDom(Path excelFile, AppConfig config) throws IOException {
        DataFormatter fmt = new DataFormatter();
        try (Workbook workbook = WorkbookFactory.create(excelFile.toFile(), null, true)) {
            Sheet sheet = resolveSheetDom(workbook, config.sheetName());
            Row headerRow = sheet.getRow(config.headerRowIndex());
            if (headerRow == null) {
                throw new IllegalArgumentException("Header row was not found at index " + config.headerRowIndex());
            }
            Map<String, Integer> headerIndex = new HashMap<>();
            for (int i = 0; i < headerRow.getLastCellNum(); i++) {
                String h = fmt.formatCellValue(headerRow.getCell(i)).trim();
                if (!h.isEmpty()) headerIndex.put(h, i);
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (int rowIndex = config.startDataRowIndex(); rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue;
                Map<String, String> mapped = new LinkedHashMap<>();
                boolean hasData = false;
                for (ColumnMapping mapping : config.columnMappings()) {
                    Integer colIdx = headerIndex.get(mapping.excelHeader());
                    if (colIdx == null) {
                        throw new IllegalArgumentException("Excel header not found: '" + mapping.excelHeader() + "'");
                    }
                    String val = fmt.formatCellValue(row.getCell(colIdx)).trim();
                    if (!val.isEmpty()) hasData = true;
                    mapped.put(mapping.dbColumn(), val);
                }
                if (hasData) rows.add(mapped);
            }
            log.info("Rows read from file={}: rowsRead={}, sheet={}", excelFile.getFileName(), rows.size(), sheet.getSheetName());
            return rows;
        }
    }

    private Sheet resolveSheetDom(Workbook workbook, String sheetName) {
        if (!sheetName.isBlank()) {
            Sheet s = workbook.getSheet(sheetName);
            if (s == null) throw new IllegalArgumentException("Sheet '" + sheetName + "' was not found.");
            return s;
        }
        if (workbook.getNumberOfSheets() > 0) return workbook.getSheetAt(0);
        throw new IllegalArgumentException("Workbook contains no sheets.");
    }

    // ─────────────────────── Utilities ────────────────────────

    private boolean isXls(Path path) {
        return path.getFileName().toString().toLowerCase().endsWith(".xls");
    }

    private void trimTrailingEmpty(List<String> list) {
        while (!list.isEmpty() && list.get(list.size() - 1).isEmpty()) {
            list.remove(list.size() - 1);
        }
    }
}
