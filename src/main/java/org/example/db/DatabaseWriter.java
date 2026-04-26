package org.example.db;

import org.example.config.AppConfig;
import org.example.model.ColumnMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class DatabaseWriter {

    private static final Logger log = LoggerFactory.getLogger(DatabaseWriter.class);
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    public int insertRows(List<Map<String, String>> rows, AppConfig config) throws SQLException, ClassNotFoundException {
        if (!config.dbDriver().isBlank()) {
            Class.forName(config.dbDriver());
        }

        validateIdentifier(config.tableName());
        for (ColumnMapping mapping : config.columnMappings()) {
            validateIdentifier(mapping.dbColumn());
        }
        for (String dedupeColumn : config.dedupeColumns()) {
            validateIdentifier(dedupeColumn);
        }

        try (Connection connection = DriverManager.getConnection(config.dbUrl(), config.dbUser(), config.dbPassword())) {
            connection.setAutoCommit(false);
            try {
                log.info("Writing rows to DB: table={}, incomingRows={}", config.tableName(), rows.size());
                if (config.autoCreateTable()) {
                    createTableIfMissing(connection, config);
                }

                int inserted = insertBatch(connection, rows, config);
                connection.commit();
                log.info("DB write committed: table={}, insertedRows={}, skippedRows={}",
                        config.tableName(), inserted, Math.max(rows.size() - inserted, 0));
                return inserted;
            } catch (Exception ex) {
                connection.rollback();
                log.error("DB write rolled back for table={}: {}", config.tableName(), ex.getMessage(), ex);
                throw ex;
            }
        }
    }

    private void createTableIfMissing(Connection connection, AppConfig config) throws SQLException {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ")
                .append(config.tableName())
                .append(" (");

        for (int i = 0; i < config.columnMappings().size(); i++) {
            ColumnMapping mapping = config.columnMappings().get(i);
            sql.append(mapping.dbColumn()).append(" VARCHAR(255)");
            if (i < config.columnMappings().size() - 1) {
                sql.append(", ");
            }
        }
        sql.append(")");

        try (Statement statement = connection.createStatement()) {
            statement.execute(sql.toString());
            log.debug("Ensured table exists: {}", config.tableName());
        }
    }

    private int insertBatch(Connection connection, List<Map<String, String>> rows, AppConfig config) throws SQLException {
        if (rows.isEmpty()) {
            return 0;
        }

        StringBuilder columns = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();

        for (int i = 0; i < config.columnMappings().size(); i++) {
            ColumnMapping mapping = config.columnMappings().get(i);
            columns.append(mapping.dbColumn());
            placeholders.append("?");
            if (i < config.columnMappings().size() - 1) {
                columns.append(", ");
                placeholders.append(", ");
            }
        }

        String insertSql = "INSERT INTO " + config.tableName() + " (" + columns + ") VALUES (" + placeholders + ")";
        String existsSql = buildExistsSql(config);

        try (PreparedStatement ps = connection.prepareStatement(insertSql);
             PreparedStatement existsStatement = connection.prepareStatement(existsSql)) {
            int batchedRows = 0;
            int duplicateRows = 0;
            for (Map<String, String> row : rows) {
                if (recordExists(existsStatement, row, config)) {
                    duplicateRows++;
                    continue;
                }
                for (int i = 0; i < config.columnMappings().size(); i++) {
                    String dbColumn = config.columnMappings().get(i).dbColumn();
                    ps.setString(i + 1, row.get(dbColumn));
                }
                ps.addBatch();
                batchedRows++;
            }

            if (batchedRows == 0) {
                log.info("No new rows to insert for table={} (all duplicates={})", config.tableName(), duplicateRows);
                return 0;
            }

            int[] updates = ps.executeBatch();
            int inserted = 0;
            for (int updateCount : updates) {
                if (updateCount >= 0 || updateCount == Statement.SUCCESS_NO_INFO) {
                    inserted++;
                }
            }
            log.info("Batch executed: table={}, attemptedBatchRows={}, insertedRows={}, duplicateRows={}",
                    config.tableName(), batchedRows, inserted, duplicateRows);
            return inserted;
        }
    }

    private String buildExistsSql(AppConfig config) {
        StringBuilder whereClause = new StringBuilder();
        for (int i = 0; i < config.dedupeColumns().size(); i++) {
            if (i > 0) {
                whereClause.append(" AND ");
            }
            whereClause.append(config.dedupeColumns().get(i)).append(" = ?");
        }
        return "SELECT 1 FROM " + config.tableName() + " WHERE " + whereClause;
    }

    private boolean recordExists(PreparedStatement existsStatement, Map<String, String> row, AppConfig config) throws SQLException {
        for (int i = 0; i < config.dedupeColumns().size(); i++) {
            existsStatement.setString(i + 1, row.getOrDefault(config.dedupeColumns().get(i), ""));
        }

        try (ResultSet resultSet = existsStatement.executeQuery()) {
            return resultSet.next();
        }
    }

    private void validateIdentifier(String identifier) {
        if (!SQL_IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                    "Invalid SQL identifier: '" + identifier + "'. Use only letters, numbers, and underscores."
            );
        }
    }
}
