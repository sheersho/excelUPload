package org.example;

import org.example.config.AppConfig;
import org.example.db.DatabaseWriter;
import org.example.excel.ExcelReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class ExcelToSqlService {

    private static final Logger log = LoggerFactory.getLogger(ExcelToSqlService.class);

    private final ExcelReader excelReader;
    private final DatabaseWriter databaseWriter;

    public ExcelToSqlService() {
        this(new ExcelReader(), new DatabaseWriter());
    }

    public ExcelToSqlService(ExcelReader excelReader, DatabaseWriter databaseWriter) {
        this.excelReader = excelReader;
        this.databaseWriter = databaseWriter;
    }

    public ImportResult importPath(Path inputPath, AppConfig config) throws Exception {
        log.info("Upload started: path={}, table={}", inputPath, config.tableName());
        ImportResult result;
        if (Files.isDirectory(inputPath)) {
            result = importDirectory(inputPath, config);
        } else {
            result = importFile(inputPath, config, new LinkedHashSet<>());
        }
        log.info("Upload summary: path={}, filesProcessed={}, rowsRead={}, rowsInserted={}, rowsSkipped={}",
                inputPath, result.filesProcessed(), result.rowsRead(), result.rowsInserted(), result.rowsSkipped());
        return result;
    }

    public ImportResult importFile(Path excelFile, AppConfig config) throws Exception {
        return importFile(excelFile, config, new LinkedHashSet<>());
    }

    private ImportResult importDirectory(Path directory, AppConfig config) throws Exception {
        List<Path> excelFiles = listExcelFiles(directory);
        if (excelFiles.isEmpty()) {
            throw new IllegalArgumentException("No Excel files were found in directory: " + directory);
        }

        log.info("Directory upload: directory={}, fileCount={}", directory, excelFiles.size());
        ImportResult total = ImportResult.empty();
        Set<String> seenKeys = new LinkedHashSet<>();
        for (Path excelFile : excelFiles) {
            total = total.add(importFile(excelFile, config, seenKeys));
        }
        return total;
    }

    private ImportResult importFile(Path excelFile, AppConfig config, Set<String> seenKeys) throws Exception {
        List<Map<String, String>> rows = excelReader.readRows(excelFile, config);
        int seenBefore = seenKeys.size();
        List<Map<String, String>> uniqueRows = rows.stream()
                .filter(row -> seenKeys.add(buildUniqueKey(row, config.dedupeColumns())))
                .toList();

        int inMemoryDuplicates = rows.size() - (seenKeys.size() - seenBefore);
        int inserted = databaseWriter.insertRows(uniqueRows, config);
        int skipped = rows.size() - inserted;

        log.info("File upload processed: file={}, rowsRead={}, uniqueRows={}, inserted={}, skipped={}, inMemoryDuplicates={}",
                excelFile.getFileName(), rows.size(), uniqueRows.size(), inserted, Math.max(skipped, 0), Math.max(inMemoryDuplicates, 0));
        return new ImportResult(1, rows.size(), inserted, Math.max(skipped, 0));
    }

    private String buildUniqueKey(Map<String, String> row, List<String> dedupeColumns) {
        StringBuilder key = new StringBuilder();
        for (String column : dedupeColumns) {
            if (!key.isEmpty()) {
                key.append('\u001F');
            }
            key.append(column)
                    .append('=')
                    .append(row.getOrDefault(column, ""));
        }
        return key.toString();
    }

    private List<Path> listExcelFiles(Path directory) throws IOException {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> {
                        String fileName = path.getFileName().toString();
                        String lower = fileName.toLowerCase();
                        return !fileName.startsWith("~$") && (lower.endsWith(".xlsx") || lower.endsWith(".xls"));
                    })
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .toList();
        }
    }
}
