package org.example.config;

import org.example.model.ColumnMapping;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

public record AppConfig(
        String dbUrl,
        String dbUser,
        String dbPassword,
        String dbDriver,
        String tableName,
        String sheetName,
        int headerRowIndex,
        int startDataRowIndex,
        boolean autoCreateTable,
        List<ColumnMapping> columnMappings,
        List<String> dedupeColumns
) {

    public static AppConfig load(Path externalConfigPath) throws IOException {
        Properties properties = new Properties();

        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in == null) {
                throw new IllegalStateException("Default application.properties not found in resources.");
            }
            properties.load(in);
        }

        if (externalConfigPath != null) {
            try (InputStream in = Files.newInputStream(externalConfigPath)) {
                Properties overrides = new Properties();
                overrides.load(in);
                properties.putAll(overrides);
            }
        }

        String dbUrl = required(properties, "db.url");
        String dbUser = properties.getProperty("db.username", "");
        String dbPassword = properties.getProperty("db.password", "");
        String dbDriver = properties.getProperty("db.driver", "").trim();
        String tableName = required(properties, "db.table");
        String sheetName = properties.getProperty("excel.sheetName", "").trim();
        int headerRowIndex = Integer.parseInt(properties.getProperty("excel.headerRowIndex", "0"));
        int startDataRowIndex = Integer.parseInt(properties.getProperty("excel.startDataRowIndex", "1"));
        boolean autoCreateTable = Boolean.parseBoolean(properties.getProperty("sql.autoCreateTable", "true"));
        List<ColumnMapping> mappings = parseMappings(required(properties, "mapping.columns"));
        List<String> dedupeColumns = parseDedupeColumns(properties.getProperty("dedupe.columns", ""), mappings);

        return new AppConfig(
                dbUrl,
                dbUser,
                dbPassword,
                dbDriver,
                tableName,
                sheetName,
                headerRowIndex,
                startDataRowIndex,
                autoCreateTable,
                mappings,
                dedupeColumns
        );
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required property: " + key);
        }
        return value.trim();
    }

    private static List<ColumnMapping> parseMappings(String rawMapping) {
        String[] tokens = rawMapping.split(",");
        List<ColumnMapping> mappings = new ArrayList<>();

        for (String token : tokens) {
            String[] pair = token.split(":", 2);
            if (pair.length != 2) {
                throw new IllegalArgumentException(
                        "Invalid mapping token '" + token + "'. Use format ExcelHeader:db_column"
                );
            }
            String excelHeader = pair[0].trim();
            String dbColumn = pair[1].trim();
            if (excelHeader.isEmpty() || dbColumn.isEmpty()) {
                throw new IllegalArgumentException("Invalid mapping token '" + token + "'. Header and db column are required.");
            }
            mappings.add(new ColumnMapping(excelHeader, dbColumn));
        }

        if (mappings.isEmpty()) {
            throw new IllegalArgumentException("At least one column mapping is required.");
        }

        return List.copyOf(mappings);
    }

    private static List<String> parseDedupeColumns(String rawDedupeColumns, List<ColumnMapping> mappings) {
        if (rawDedupeColumns == null || rawDedupeColumns.trim().isEmpty()) {
            return defaultDedupeColumns(mappings);
        }

        Set<String> validColumns = new LinkedHashSet<>();
        for (ColumnMapping mapping : mappings) {
            validColumns.add(mapping.dbColumn());
        }

        List<String> dedupeColumns = new ArrayList<>();
        for (String token : rawDedupeColumns.split(",")) {
            String column = token.trim();
            if (column.isEmpty()) {
                continue;
            }
            if (!validColumns.contains(column)) {
                throw new IllegalArgumentException(
                        "Invalid dedupe column '" + column + "'. Use mapped DB columns only: " + validColumns
                );
            }
            if (!dedupeColumns.contains(column)) {
                dedupeColumns.add(column);
            }
        }

        if (dedupeColumns.isEmpty()) {
            return defaultDedupeColumns(mappings);
        }

        return List.copyOf(dedupeColumns);
    }

    private static List<String> defaultDedupeColumns(List<ColumnMapping> mappings) {
        int dedupeColumnCount = Math.min(Math.max(mappings.size(), 1), 4);
        List<String> defaults = new ArrayList<>(dedupeColumnCount);
        for (int i = 0; i < dedupeColumnCount; i++) {
            defaults.add(mappings.get(i).dbColumn());
        }
        return List.copyOf(defaults);
    }

    public AppConfig {
        Objects.requireNonNull(dbUrl, "dbUrl must not be null");
        Objects.requireNonNull(dbUser, "dbUser must not be null");
        Objects.requireNonNull(dbPassword, "dbPassword must not be null");
        Objects.requireNonNull(dbDriver, "dbDriver must not be null");
        Objects.requireNonNull(tableName, "tableName must not be null");
        Objects.requireNonNull(sheetName, "sheetName must not be null");
        Objects.requireNonNull(columnMappings, "columnMappings must not be null");
        Objects.requireNonNull(dedupeColumns, "dedupeColumns must not be null");
    }
}

