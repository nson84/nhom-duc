$ErrorActionPreference = "Stop"
$testConfig = Join-Path $PSScriptRoot "test-mysql-env.local.ps1"
if (Test-Path -LiteralPath $testConfig) { . $testConfig }
if (-not $env:ALUMINUM_TEST_DB_URL -or
    $env:ALUMINUM_TEST_DB_URL -notmatch '^jdbc:mysql://127\.0\.0\.1:[0-9]+/aluminum_backup_test(?:\?|$)') {
    throw "Set ALUMINUM_TEST_DB_URL to a separate local MySQL using the name aluminum_backup_test."
}
if (-not $env:ALUMINUM_TEST_DB_USERNAME -or -not $env:ALUMINUM_TEST_DB_PASSWORD) {
    throw "Enter the dedicated test MySQL credentials in scripts/test-mysql-env.local.ps1."
}
Push-Location (Join-Path $PSScriptRoot "..\backend")
try {
    & .\mvnw.cmd "-Dtest=*MysqlIT" test
    if ($LASTEXITCODE -ne 0) { throw "MySQL integration tests failed." }
} finally { Pop-Location }
