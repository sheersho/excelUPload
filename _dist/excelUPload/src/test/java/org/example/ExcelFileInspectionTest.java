package org.example;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.nio.file.Files;
import java.nio.file.Path;

class ExcelFileInspectionTest {

    void printsWorkbookDetails() throws Exception {
        Path excelDir = Path.of("C:\\Users\\YV738GP\\IdeaProjects\\excelUPload\\excelFiles");
        DataFormatter formatter = new DataFormatter();

        try (var files = Files.list(excelDir)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
            try (Workbook workbook = WorkbookFactory.create(file.toFile())) {
                System.out.println("FILE=" + file.getFileName());
                for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                    Sheet sheet = workbook.getSheetAt(sheetIndex);
                    System.out.println("SHEET=" + sheet.getSheetName() + ";ROWCOUNT=" + (sheet.getLastRowNum() + 1));
                    for (int rowIndex = 0; rowIndex <= Math.min(sheet.getLastRowNum(), 4); rowIndex++) {
                        Row row = sheet.getRow(rowIndex);
                        if (row == null) {
                            System.out.println("ROW" + rowIndex + "=<empty>");
                            continue;
                        }
                        int lastCell = Math.max(row.getLastCellNum(), 0);
                        StringBuilder values = new StringBuilder();
                        for (int cellIndex = 0; cellIndex < lastCell; cellIndex++) {
                            if (cellIndex > 0) {
                                values.append(" | ");
                            }
                            values.append(formatter.formatCellValue(row.getCell(cellIndex)));
                        }
                        System.out.println("ROW" + rowIndex + "=" + values);
                    }
                }
            }
            }
        }
    }
}


