package org.example.api;

import org.example.ExcelToSqlService;
import org.example.ImportResult;
import org.example.config.AppConfig;
import org.example.excel.ExcelReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);
    private final ExcelToSqlService excelToSqlService;
    private final ExcelReader excelReader;

    public ImportController() {
        this.excelToSqlService = new ExcelToSqlService();
        this.excelReader = new ExcelReader();
    }

    @GetMapping("/records")
    public ResponseEntity<Map<String, Object>> getRecords(
            @RequestParam(name = "limit", required = false, defaultValue = "100") int limit) {
        AppConfig config = null;
        try {
            log.info("Records requested: limit={}", limit);
            config = AppConfig.load(null);
            Class.forName(config.dbDriver());
            try (Connection conn = DriverManager.getConnection(config.dbUrl(), config.dbUser(), config.dbPassword());
                 Statement stmt = conn.createStatement()) {

                String sql = "SELECT * FROM " + config.tableName() + " LIMIT " + limit;
                ResultSet rs = stmt.executeQuery(sql);
                ResultSetMetaData meta = rs.getMetaData();
                int colCount = meta.getColumnCount();

                List<Map<String, String>> rows = new ArrayList<>();
                while (rs.next()) {
                    Map<String, String> row = new LinkedHashMap<>();
                    for (int i = 1; i <= colCount; i++) {
                        row.put(meta.getColumnName(i), rs.getString(i));
                    }
                    rows.add(row);
                }

                ResultSet countRs = stmt.executeQuery("SELECT COUNT(*) FROM " + config.tableName());
                int totalCount = countRs.next() ? countRs.getInt(1) : 0;

                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("table", config.tableName());
                response.put("totalRows", totalCount);
                response.put("returnedRows", rows.size());
                response.put("records", rows);
                log.info("Records response ready: table={}, totalRows={}, returnedRows={}", config.tableName(), totalCount, rows.size());
                return ResponseEntity.ok(response);
            }
        } catch (SQLException sqlEx) {
            if (isMissingTableError(sqlEx)) {
                String tableName = config.tableName();
                log.info("Records requested before table exists: table={}", tableName);
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("table", tableName);
                response.put("totalRows", 0);
                response.put("returnedRows", 0);
                response.put("records", List.of());
                response.put("message", "Table '" + tableName + "' does not exist yet. Import data first.");
                return ResponseEntity.ok(response);
            }
            log.error("Records request failed: {}", sqlEx.getMessage(), sqlEx);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", sqlEx.getMessage());
            return ResponseEntity.badRequest().body(error);
        } catch (Exception ex) {
            log.error("Records request failed: {}", ex.getMessage(), ex);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    private boolean isMissingTableError(SQLException ex) {
        String sqlState = ex.getSQLState();
        String message = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        return "42P01".equals(sqlState)
                || "42S02".equals(sqlState)
                || message.contains("does not exist")
                || message.contains("not found")
                || message.contains("relation");
    }


    @GetMapping("/headers")
    public ResponseEntity<Map<String, Object>> getHeaders(
            @RequestParam("path") String path,
            @RequestParam(name = "sheetName", required = false, defaultValue = "") String sheetName) {
        try {
            log.info("Header inspection requested: path={}, sheetName={}", path, sheetName);
            List<String> headers = excelReader.readHeaders(Path.of(path), sheetName, 0);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("file", Path.of(path).getFileName().toString());
            response.put("headers", headers);
            response.put("count", headers.size());
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Header inspection failed for path={}: {}", path, ex.getMessage(), ex);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    @PostMapping(value = "/import", params = "path")
    public ResponseEntity<Map<String, Object>> importExcel(
            @RequestParam("path") String path,
            @RequestParam(name = "configPath", required = false) String configPath) {
        return runImport(Path.of(path), path, configPath);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importExcelFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "configPath", required = false) String configPath) {
        if (file.isEmpty()) {
            return badRequest("Uploaded file is empty.", "IllegalArgumentException");
        }

        String originalName = file.getOriginalFilename() == null ? "uploaded.xlsx" : file.getOriginalFilename();
        if (!isExcelFile(originalName)) {
            return badRequest("Only .xlsx or .xls files are supported.", "IllegalArgumentException");
        }

        String extension = originalName.toLowerCase().endsWith(".xls") ? ".xls" : ".xlsx";
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("excel-upload-", extension);
            try (InputStream input = file.getInputStream()) {
                Files.copy(input, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            log.info("Import request received via upload: originalFile={}, tempFile={}, configPath={}",
                    originalName, tempFile, configPath);
            return runImport(tempFile, originalName, configPath);
        } catch (Exception ex) {
            log.error("Upload import failed for file={}: {}", originalName, ex.getMessage(), ex);
            return badRequest(ex.getMessage(), ex.getClass().getSimpleName());
        } finally {
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException cleanupEx) {
                    log.warn("Could not delete temp upload file={}: {}", tempFile, cleanupEx.getMessage());
                }
            }
        }
    }

    private ResponseEntity<Map<String, Object>> runImport(Path inputPath, String sourceLabel, String configPath) {
        try {
            Path config = configPath != null ? Path.of(configPath) : null;
            AppConfig appConfig = AppConfig.load(config);
            ImportResult result = excelToSqlService.importPath(inputPath, appConfig);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("filesProcessed", result.filesProcessed());
            response.put("rowsRead", result.rowsRead());
            response.put("rowsInserted", result.rowsInserted());
            response.put("rowsSkipped", result.rowsSkipped());
            response.put("message", String.format(
                    "Import completed. Processed %d file(s), read %d rows, inserted %d, skipped %d.",
                    result.filesProcessed(), result.rowsRead(), result.rowsInserted(), result.rowsSkipped()
            ));

            log.info("Import request completed: source={}, filesProcessed={}, rowsRead={}, rowsInserted={}, rowsSkipped={}",
                    sourceLabel, result.filesProcessed(), result.rowsRead(), result.rowsInserted(), result.rowsSkipped());
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            log.error("Import request failed for source={}: {}", sourceLabel, ex.getMessage(), ex);
            return badRequest(ex.getMessage(), ex.getClass().getSimpleName());
        }
    }

    private boolean isExcelFile(String fileName) {
        String lower = fileName.toLowerCase();
        return lower.endsWith(".xlsx") || lower.endsWith(".xls");
    }

    private ResponseEntity<Map<String, Object>> badRequest(String message, String type) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("error", message);
        error.put("type", type);
        return ResponseEntity.badRequest().body(error);
    }
}
