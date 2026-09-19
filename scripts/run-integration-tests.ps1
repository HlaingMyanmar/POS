# Run MySQL/Flyway integration tests (*IT).
# Uses local MySQL only when IT_USE_LOCAL_MYSQL=true; otherwise Testcontainers.

$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)

$useLocal = $env:IT_USE_LOCAL_MYSQL -eq "true"
Write-Host "Integration backend: $(if ($useLocal) { 'local MySQL ser_db_it' } else { 'Testcontainers (Docker required)' })"
Write-Host "Running failsafe integration tests (profile integration-test)..."

$mavenArgs = @("-Pintegration-test", "verify", "-Dsurefire.skip=true", "-DskipITs=false")
if ($useLocal) {
    $mavenArgs += "-Dit.useLocalMysql=true"
}
& .\mvnw.cmd @mavenArgs
exit $LASTEXITCODE
