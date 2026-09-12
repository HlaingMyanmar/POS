# Run MySQL/Flyway integration tests (*IT).
# Prefers local MySQL (ser_db_it) when Docker is not installed.

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

if (-not $env:IT_USE_LOCAL_MYSQL) {
    $env:IT_USE_LOCAL_MYSQL = "true"
}

Write-Host "IT_USE_LOCAL_MYSQL=$($env:IT_USE_LOCAL_MYSQL)"
Write-Host "Running failsafe integration tests (profile integration-test)..."

& .\mvnw.cmd -Pintegration-test verify "-Dsurefire.skip=true" "-DskipITs=false" "-Dit.useLocalMysql=true"
exit $LASTEXITCODE
