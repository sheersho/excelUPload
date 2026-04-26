package org.example;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.config.AppConfig;
import org.example.model.ColumnMapping;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExcelToSqlServiceTest {

    @Test
    void importsMappedColumnsIntoDatabase() throws Exception {
        Path tempDir = Files.createTempDirectory("excel-upload-test");
        Path excelPath = tempDir.resolve("employees.xlsx");
        String dbUrl = "jdbc:h2:" + tempDir.resolve("testdb");

        createExcel(excelPath,
                List.of(
                        new String[]{"Alice", "31", "Finance"},
                        new String[]{"Bob", "27", "Engineering"}
                ));

        AppConfig config = new AppConfig(
                dbUrl,
                "sa",
                "",
                "org.h2.Driver",
                "employees",
                "Sheet1",
                0,
                1,
                true,
                List.of(
                        new ColumnMapping("Name", "name"),
                        new ColumnMapping("Age", "age"),
                        new ColumnMapping("Department", "department")
                ),
                List.of("name", "age", "department")
        );

        ExcelToSqlService service = new ExcelToSqlService();
        ImportResult result = service.importFile(excelPath, config);

        assertEquals(1, result.filesProcessed());
        assertEquals(2, result.rowsRead());
        assertEquals(2, result.rowsInserted());
        assertEquals(0, result.rowsSkipped());

        try (Connection connection = DriverManager.getConnection(dbUrl, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM employees")) {
            rs.next();
            assertEquals(2, rs.getInt(1));
        }
    }

    @Test
    void importsAllExcelFilesFromFolderAndSkipsDuplicates() throws Exception {
        Path tempDir = Files.createTempDirectory("excel-upload-folder-test");
        Path excelDir = tempDir.resolve("excelFiles");
        Files.createDirectories(excelDir);

        createExcel(excelDir.resolve("employees-a.xlsx"),
                List.of(
                        new String[]{"Alice", "31", "Finance"},
                        new String[]{"Bob", "27", "Engineering"}
                ));
        createExcel(excelDir.resolve("employees-b.xlsx"),
                List.of(
                        new String[]{"Alice", "31", "Finance"},
                        new String[]{"Charlie", "35", "HR"}
                ));

        String dbUrl = "jdbc:h2:" + tempDir.resolve("folderdb");
        AppConfig config = new AppConfig(
                dbUrl,
                "sa",
                "",
                "org.h2.Driver",
                "employees",
                "Sheet1",
                0,
                1,
                true,
                List.of(
                        new ColumnMapping("Name", "name"),
                        new ColumnMapping("Age", "age"),
                        new ColumnMapping("Department", "department")
                ),
                List.of("name", "age", "department")
        );

        ExcelToSqlService service = new ExcelToSqlService();
        ImportResult result = service.importPath(excelDir, config);

        assertEquals(2, result.filesProcessed());
        assertEquals(4, result.rowsRead());
        assertEquals(3, result.rowsInserted());
        assertEquals(1, result.rowsSkipped());

        try (Connection connection = DriverManager.getConnection(dbUrl, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM employees")) {
            rs.next();
            assertEquals(3, rs.getInt(1));
        }
    }

    @Test
    void skipsRowsAlreadyPresentInDatabaseOnSecondRun() throws Exception {
        Path tempDir = Files.createTempDirectory("excel-upload-repeat-test");
        Path excelDir = tempDir.resolve("excelFiles");
        Files.createDirectories(excelDir);

        createExcel(excelDir.resolve("employees-a.xlsx"),
                List.of(
                        new String[]{"Alice", "31", "Finance"},
                        new String[]{"Bob", "27", "Engineering"}
                ));

        String dbUrl = "jdbc:h2:" + tempDir.resolve("repeatdb");
        AppConfig config = new AppConfig(
                dbUrl,
                "sa",
                "",
                "org.h2.Driver",
                "employees",
                "Sheet1",
                0,
                1,
                true,
                List.of(
                        new ColumnMapping("Name", "name"),
                        new ColumnMapping("Age", "age"),
                        new ColumnMapping("Department", "department")
                ),
                List.of("name", "age", "department")
        );

        ExcelToSqlService service = new ExcelToSqlService();
        ImportResult firstRun = service.importPath(excelDir, config);
        ImportResult secondRun = service.importPath(excelDir, config);

        assertEquals(2, firstRun.rowsInserted());
        assertEquals(0, firstRun.rowsSkipped());
        assertEquals(0, secondRun.rowsInserted());
        assertEquals(2, secondRun.rowsSkipped());

        try (Connection connection = DriverManager.getConnection(dbUrl, "sa", "");
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM employees")) {
            rs.next();
            assertEquals(2, rs.getInt(1));
        }
    }

    private void createExcel(Path excelPath, List<String[]> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            XSSFSheet sheet = workbook.createSheet("Sheet1");

            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("Name");
            header.createCell(1).setCellValue("Age");
            header.createCell(2).setCellValue("Department");

            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Row row = sheet.createRow(rowIndex + 1);
                String[] values = rows.get(rowIndex);
                for (int cellIndex = 0; cellIndex < values.length; cellIndex++) {
                    row.createCell(cellIndex).setCellValue(values[cellIndex]);
                }
            }

            Files.createDirectories(excelPath.getParent());
            try (OutputStream outputStream = Files.newOutputStream(excelPath)) {
                workbook.write(outputStream);
            }
        }
    }
}

