$ErrorActionPreference = "Stop"
$localEnv = Join-Path $PSScriptRoot "dev-env.local.ps1"
if (Test-Path $localEnv) {
    . $localEnv
}
if (-not $env:ALUMINUM_DB_PASSWORD) {
    $env:ALUMINUM_DB_PASSWORD = "aluminum_app_dev"
}
if (-not $env:ALUMINUM_IMAGE_DIR) {
    $env:ALUMINUM_IMAGE_DIR = Join-Path (Split-Path -Parent $PSScriptRoot) "image"
}
if (-not $env:ALUMINUM_DEV_BOOTSTRAP_PASSWORD) {
    $env:ALUMINUM_DEV_BOOTSTRAP_PASSWORD = "1234567"
}
# Handoff uses the desktop's default database configuration for rollback.
$env:ALUMINUM_DEV_HANDOFF = if (
    (-not $env:ALUMINUM_DB_NAME -or $env:ALUMINUM_DB_NAME -eq 'aluminum_app') -and
    (-not $env:ALUMINUM_DB_USERNAME -or $env:ALUMINUM_DB_USERNAME -eq 'aluminum_app') -and
    $env:ALUMINUM_DB_PASSWORD -ceq 'aluminum_app_dev' -and
    -not $env:SPRING_DATASOURCE_URL -and -not $env:SPRING_DATASOURCE_USERNAME -and
    -not $env:SPRING_DATASOURCE_PASSWORD -and -not $env:SPRING_APPLICATION_JSON -and
    -not $env:ALUMINUM_JWT_SECRET -and -not $env:ALUMINUM_JWT_SECRET_FILE -and
    (-not $env:ALUMINUM_BIND_ADDRESS -or $env:ALUMINUM_BIND_ADDRESS -eq '127.0.0.1')
) { 'true' } else { 'false' }
Set-Location (Join-Path $PSScriptRoot "..\backend")
& .\mvnw.cmd spring-boot:run
