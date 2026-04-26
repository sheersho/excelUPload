@echo off
REM Start Excel to SQL REST API Microservice
REM Usage: run-api.bat

setlocal enabledelayedexpansion

echo Starting Excel to SQL REST API Service...
echo.
echo Server will start on: http://localhost:8080
echo API Endpoint: POST /api/import
echo.
echo Example curl command:
echo curl -X POST "http://localhost:8080/api/import?path=C:\path\to\excelFiles"
echo.
echo Press CTRL+C to stop the server.
echo.

java -cp target\excelUPload-1.0-SNAPSHOT.jar org.example.ExcelToSqlApplication

pause

