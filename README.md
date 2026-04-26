# Excel to SQL Importer

This project reads one or more Excel files and inserts selected columns into an SQL table.

Supports two modes:
- **CLI**: Command-line batch importer for one-off imports
- **REST API**: Lightweight HTTP microservice for frontend integration

## What it does

- Reads rows from an Excel sheet
- Can process a single file or every Excel file inside a folder
- Maps Excel headers to DB column names (configured in properties)
- Keeps only unique records based on 3-4 configured columns before insert
- Inserts rows into a database using JDBC batch inserts
- Can auto-create the table with `VARCHAR(255)` columns

## Configuration

Default config file: `src/main/resources/application.properties`

Key properties:

- `db.url`, `db.username`, `db.password`, `db.driver`, `db.table`
- `excel.sheetName`, `excel.headerRowIndex`, `excel.startDataRowIndex`
- `mapping.columns` format: `ExcelHeader:db_column,OtherHeader:other_column`
- `dedupe.columns` format: `db_column1,db_column2,db_column3`
- `sql.autoCreateTable=true|false`

Example:

- `mapping.columns=Customer:customer_name,Mobile:mobile_no,City:city,PinCode:pin_code`
- `dedupe.columns=customer_name,mobile_no,city,pin_code`

With that setup, rows that repeat the same values across those 3-4 columns are skipped and only one record is inserted.

## Run

### CLI Mode (Batch)

Build and run tests:

```powershell
mvn clean test
```

Create runnable jar:

```powershell
mvn clean package
```

Run importer:

```powershell
java -jar target\excelUPload-1.0-SNAPSHOT.jar "C:\path\to\input.xlsx"
```

Or process an entire folder:

```powershell
java -jar target\excelUPload-1.0-SNAPSHOT.jar "C:\path\to\excelFiles"
```

### REST API Mode (Microservice)

Start the REST API server on port 8080:

```powershell
java -cp "target\excelUPload-1.0-SNAPSHOT.jar" org.example.ExcelToSqlApplication
```

#### API Endpoints

**POST** `/api/import`

Import data using either:

1. `path` query parameter (server-side file/folder path)
2. `file` multipart upload (Postman/curl upload from user machine)

**Path-based parameters:**
- `path` (required): Full path to Excel file or folder on the machine where API is running
- `configPath` (optional): Full path to custom config file

**Path-based curl example:**

```bash
curl -X POST "http://localhost:8080/api/import?path=C:/Users/YV738GP/IdeaProjects/excelUPload/excelFiles"
```

**Upload-based parameter:**
- `file` (required): `.xlsx` or `.xls` file in multipart form-data
- `configPath` (optional): Full path to custom config file

**Upload-based curl example (Windows PowerShell):**

```bash
curl.exe -X POST "https://excelupload.onrender.com/api/import" -F "file=@C:/Users/YV738GP/IdeaProjects/excelUPload/excelFiles/Book1.xlsx"
```

**Upload-based curl example (Linux/macOS):**

```bash
curl -X POST "https://excelupload.onrender.com/api/import" -F "file=@/absolute/path/to/Book1.xlsx"
```

**Postman setup:**
- Method: `POST`
- URL: `https://excelupload.onrender.com/api/import`
- Body -> `form-data` -> key `file` (type `File`) -> select Excel file

**Response (JSON):**

```powershell
java -jar target\excelUPload-1.0-SNAPSHOT.jar "C:\path\to\input.xlsx"
```

Example success response:

```json
{
  "success": true,
  "filesProcessed": 2,
  "rowsRead": 150,
  "rowsInserted": 145,
  "rowsSkipped": 5,
  "message": "Import completed. Processed 2 file(s), read 150 rows, inserted 145, skipped 5."
}
```

Example error response:

```json
{
  "success": false,
  "error": "No Excel files were found in directory: C:\\invalid\\path",
  "type": "IllegalArgumentException"
}
```

### Frontend JavaScript Example

```javascript
async function importExcelFiles(folderPath) {
  const response = await fetch('http://localhost:8080/api/import', {
	method: 'POST',
	headers: {
	  'Content-Type': 'application/x-www-form-urlencoded'
	},
	body: `path=${encodeURIComponent(folderPath)}`
  });

  const result = await response.json();
  if (result.success) {
	console.log(`✓ Imported: ${result.rowsInserted} records from ${result.filesProcessed} file(s)`);
  } else {
	console.error(`✗ Import failed: ${result.error}`);
  }
  return result;
}
```

## Input Excel expectations

- Header row exists (default row index `0`)
- Data rows start at configured index (default `1`)
- Header names used in `mapping.columns` must exist in the sheet

## Notes for other databases

The default setup uses H2 for easy local testing. For MySQL/PostgreSQL/SQL Server:

1. Add the corresponding JDBC driver dependency in `pom.xml`
2. Update `db.url` and `db.driver` in properties
3. Keep `mapping.columns` and `db.table` as needed

