$ErrorActionPreference = 'Stop'
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('aluminum-recovery-test-' + [guid]::NewGuid().ToString('N'))
$database = 'aluminum_restore_012345abcdef'
$profileDirectory = Join-Path $fixture $database
try {
    New-Item -ItemType Directory -Force -Path "$profileDirectory/image" | Out-Null
    $profilePath = Join-Path $profileDirectory 'verified-recovery.json'
    $jar = Join-Path $fixture 'fixture.jar'
    Set-Content $jar 'fixture'
    $profile = @{ verified=$true; databaseName=$database; jdbcUrl="jdbc:mysql://127.0.0.1:13307/$database"; imageDirectory=(Join-Path $profileDirectory 'image') }
    $profile | ConvertTo-Json | Set-Content $profilePath
    $priorUrl = $env:SPRING_DATASOURCE_URL
    $global:recoveryTestCalls = 0
    function global:java {
        if ($env:SPRING_DATASOURCE_URL -notlike '*aluminum_restore_012345abcdef' -or
            $env:SPRING_DATASOURCE_PASSWORD -ne 'fixture-password' -or
            $env:ALUMINUM_BACKUP_SCHEDULED_ENABLED -ne 'false' -or
            $env:APPDATA -notlike '*aluminum_restore_012345abcdef*') { throw 'Recovery environment is not isolated' }
        $global:recoveryTestCalls++
        & $env:ComSpec /c 'exit 0'
    }
    $secret = ConvertTo-SecureString 'fixture-password' -AsPlainText -Force
    & "$PSScriptRoot/start-recovered-backend.ps1" -ProfilePath $profilePath -JarPath $jar -DatabaseUsername 'test' -DatabasePassword $secret -ApiPort 18083 -Confirm:$false
    if ($global:recoveryTestCalls -ne 1 -or [string]$env:SPRING_DATASOURCE_URL -ne [string]$priorUrl) { throw 'Recovery must launch only its own profile and restore caller environment' }
    $profile.jdbcUrl = 'jdbc:mysql://127.0.0.1:13307/aluminum_app'
    $profile | ConvertTo-Json | Set-Content $profilePath
    $rejected = $false
    try { & "$PSScriptRoot/start-recovered-backend.ps1" -ProfilePath $profilePath -JarPath $jar -WhatIf }
    catch { $rejected = $_.Exception.Message -match 'verified local database' }
    if (-not $rejected) { throw 'Recovery must reject a production database URL' }
    Write-Host 'PASS: recovery isolates data, images, credentials and scheduled jobs; production URL rejected.'
} finally {
    Remove-Item Function:/java -ErrorAction SilentlyContinue
    Remove-Variable recoveryTestCalls -Scope Global -ErrorAction SilentlyContinue
    if ([IO.Path]::GetFullPath($fixture).StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath())) -and
        (Split-Path $fixture -Leaf).StartsWith('aluminum-recovery-test-')) { Remove-Item -LiteralPath $fixture -Recurse -Force }
}
