# Start Excel to SQL REST API Microservice
# Usage: .\run-api.ps1

Write-Host "Starting Excel to SQL REST API Service..." -ForegroundColor Green
Write-Host ""
Write-Host "Server will start on: http://localhost:8080" -ForegroundColor Cyan
Write-Host "API Endpoint: POST /api/import" -ForegroundColor Cyan
Write-Host ""
Write-Host "Example curl command:" -ForegroundColor Yellow
Write-Host 'curl -X POST "http://localhost:8080/api/import?path=C:\path\to\excelFiles"' -ForegroundColor White
Write-Host ""
Write-Host "Press CTRL+C to stop the server." -ForegroundColor Yellow
Write-Host ""

java -cp "target\excelUPload-1.0-SNAPSHOT.jar" org.example.ExcelToSqlApplication

Read-Host "Press Enter to exit"

