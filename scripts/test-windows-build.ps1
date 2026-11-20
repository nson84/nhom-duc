$ErrorActionPreference = 'Stop'
# Execute the packaging script against fake native tools; never build or install anything.
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('aluminum-build-test-' + [guid]::NewGuid().ToString('N'))
$originalLocation = Get-Location
try {
    New-Item -ItemType Directory -Force -Path "$fixture/scripts", "$fixture/backend/target", "$fixture/frontend" | Out-Null
    Copy-Item "$PSScriptRoot/build-windows.ps1" "$fixture/scripts/build-windows.ps1"
    Set-Content "$fixture/backend/target/test.jar" 'fixture'
    function global:npm { & $env:ComSpec /c 'exit 0' }
    function global:cargo { & $env:ComSpec /c 'exit 0' }
    function global:npx { & $env:ComSpec /c 'exit 0' }
    foreach ($scenario in @('backend', 'frontend', 'installer')) {
        $backendExit = if ($scenario -eq 'backend') { 7 } else { 0 }
        Set-Content "$fixture/backend/mvnw.cmd" "@exit /b $backendExit"
        if ($scenario -eq 'frontend') { function global:npm { & $env:ComSpec /c 'exit 8' } }
        else { function global:npm { & $env:ComSpec /c 'exit 0' } }
        if ($scenario -eq 'installer') { function global:npx { & $env:ComSpec /c 'exit 9' } }
        $failed = $false
        try { & "$fixture/scripts/build-windows.ps1" }
        catch { $failed = $_.Exception.Message -match 'exit code' }
        if (-not $failed) { throw "Packaging must reject native $scenario failure" }
    }
    Write-Host 'PASS: Maven, frontend and installer failures stop packaging.'
} finally {
    Set-Location $originalLocation
    Remove-Item Function:/npm, Function:/cargo, Function:/npx -ErrorAction SilentlyContinue
    # Fixture lives in the OS temp directory with a fixed prefix; no project files are removed.
    if ([IO.Path]::GetFullPath($fixture).StartsWith([IO.Path]::GetFullPath([IO.Path]::GetTempPath())) -and
        (Split-Path $fixture -Leaf).StartsWith('aluminum-build-test-')) {
        Remove-Item -LiteralPath $fixture -Recurse -Force
    }
}
