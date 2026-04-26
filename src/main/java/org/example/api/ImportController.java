package org.example.api;

import org.example.ExcelToSqlService;
import org.example.ImportResult;
import org.example.config.AppConfig;
import org.example.excel.ExcelReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
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
        try {
            log.info("Records requested: limit={}", limit);
            AppConfig config = AppConfig.load(null);
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
        } catch (Exception ex) {
            log.error("Records request failed: {}", ex.getMessage(), ex);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", ex.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
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

    @PostMapping("/import")
    public ResponseEntity<Map<String, Object>> importExcel(
            @RequestParam("path") String path,
            @RequestParam(name = "configPath", required = false) String configPath) {

        try {
            log.info("Import request received: path={}, configPath={}", path, configPath);
            Path inputPath = Path.of(path);
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

            log.info("Import request completed: path={}, filesProcessed={}, rowsRead={}, rowsInserted={}, rowsSkipped={}",
                    path, result.filesProcessed(), result.rowsRead(), result.rowsInserted(), result.rowsSkipped());
            return ResponseEntity.ok(response);

        } catch (Exception ex) {
            log.error("Import request failed for path={}: {}", path, ex.getMessage(), ex);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", ex.getMessage());
            error.put("type", ex.getClass().getSimpleName());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
