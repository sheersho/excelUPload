package org.example.config;

import org.example.model.ColumnMapping;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^}:]+)(?::([^}]*))?}");

    public static AppConfig load(Path externalConfigPath) throws IOException {
        Properties properties = new Properties();

        // Load default application.properties
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in == null) {
                throw new IllegalStateException("Default application.properties not found in resources.");
            }
            properties.load(in);
        }

        // Load profile-specific properties (e.g., application-prod.properties)
        String activeProfile = System.getenv("SPRING_PROFILES_ACTIVE");
        if (activeProfile != null && !activeProfile.trim().isEmpty()) {
            String profileFile = "application-" + activeProfile.trim() + ".properties";
            try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream(profileFile)) {
                if (in != null) {
                    Properties profileProperties = new Properties();
                    profileProperties.load(in);
                    properties.putAll(profileProperties);
                }
            }
        }

        // Load external config file if provided (highest priority)
        if (externalConfigPath != null) {
            try (InputStream in = Files.newInputStream(externalConfigPath)) {
                Properties overrides = new Properties();
                overrides.load(in);
                properties.putAll(overrides);
            }
        }

        resolveEnvPlaceholders(properties);

        String dbUrl = required(properties, "db.url");
        String dbDriver = properties.getProperty("db.driver", "").trim();

        // Handle PostgreSQL URLs from Render (format: postgresql://user:pass@host/db)
        // Convert to proper JDBC format: jdbc:postgresql://host:5432/db
        if (dbUrl.startsWith("postgresql://")) {
            dbUrl = normalizePostgresUrl(dbUrl, properties);
            if (dbDriver.isEmpty()) {
                dbDriver = "org.postgresql.Driver";
            }
        }

        // Read username/password after URL normalization (which may have extracted them)
        String dbUser = properties.getProperty("db.username", "");
        String dbPassword = properties.getProperty("db.password", "");
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

    private static String normalizePostgresUrl(String postgresUrl, Properties properties) {
        // Input: postgresql://username:password@host:port/database
        // Output: jdbc:postgresql://host:port/database
        try {
            URI uri = URI.create(postgresUrl);
            String host = uri.getHost();
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("Host is missing");
            }

            int port = uri.getPort() == -1 ? 5432 : uri.getPort();
            String databasePath = (uri.getPath() == null || uri.getPath().isBlank()) ? "" : uri.getPath();

            String userInfo = uri.getUserInfo();
            if (userInfo != null && !userInfo.isBlank()) {
                String[] creds = userInfo.split(":", 2);
                String username = creds.length > 0 ? creds[0] : "";
                String password = creds.length > 1 ? creds[1] : "";

                if (!username.isEmpty() && properties.getProperty("db.username", "").isEmpty()) {
                    properties.setProperty("db.username", username);
                }
                if (!password.isEmpty() && properties.getProperty("db.password", "").isEmpty()) {
                    properties.setProperty("db.password", password);
                }
            }

            return "jdbc:postgresql://" + host + ":" + port + databasePath;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid PostgreSQL URL format: " + postgresUrl, e);
        }
    }

    private static void resolveEnvPlaceholders(Properties properties) {
        for (String key : properties.stringPropertyNames()) {
            String rawValue = properties.getProperty(key);
            if (rawValue == null || rawValue.isBlank()) {
                continue;
            }
            properties.setProperty(key, resolveValue(rawValue));
        }
    }

    private static String resolveValue(String rawValue) {
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(rawValue);
        StringBuilder resolved = new StringBuilder();

        while (matcher.find()) {
            String envName = matcher.group(1);
            String defaultValue = matcher.group(2);
            String envValue = System.getenv(envName);

            if (envValue == null || envValue.isEmpty()) {
                if (defaultValue != null) {
                    envValue = defaultValue;
                } else {
                    throw new IllegalArgumentException("Missing required environment variable: " + envName);
                }
            }

            matcher.appendReplacement(resolved, Matcher.quoteReplacement(envValue));
        }

        matcher.appendTail(resolved);
        return resolved.toString();
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

