$ErrorActionPreference = "Stop"
$localEnv = Join-Path $PSScriptRoot "dev-env.local.ps1"
if (Test-Path $localEnv) {
    . $localEnv
}
if (-not $env:ALUMINUM_DB_PASSWORD) {
    $env:ALUMINUM_DB_PASSWORD = "aluminum_app_dev"
}
Set-Location (Join-Path $PSScriptRoot "..\backend")
& .\mvnw.cmd spring-boot:run
