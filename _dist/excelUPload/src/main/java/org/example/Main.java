package org.example;

import org.example.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("Usage: java -jar excelUPload-1.0-SNAPSHOT.jar <excel-file-or-folder-path> [config-file-path]");
            return;
        }

        Path inputPath = Path.of(args[0]);
        Path configFile = args.length > 1 ? Path.of(args[1]) : null;

        try {
            log.info("Starting CLI upload: inputPath={}, configFile={}", inputPath, configFile);
            AppConfig config = AppConfig.load(configFile);
            ExcelToSqlService service = new ExcelToSqlService();
            ImportResult result = service.importPath(inputPath, config);

            log.info("Upload completed: filesProcessed={}, rowsRead={}, rowsInserted={}, rowsSkipped={}",
                    result.filesProcessed(), result.rowsRead(), result.rowsInserted(), result.rowsSkipped());
            System.out.println("Import completed.");
            System.out.println("Files processed: " + result.filesProcessed());
            System.out.println("Rows read: " + result.rowsRead());
            System.out.println("Rows inserted: " + result.rowsInserted());
            System.out.println("Rows skipped: " + result.rowsSkipped());
        } catch (Exception ex) {
            log.error("Upload failed for inputPath={}: {}", inputPath, ex.getMessage(), ex);
            System.err.println("Import failed: " + ex.getMessage());
            ex.printStackTrace(System.err);
            System.exit(1);
        }
    }
}