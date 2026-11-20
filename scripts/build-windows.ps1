# Dong goi ung dung desktop Windows (Backend JAR + Frontend dist + Tauri bundle neu co Rust)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

Write-Host "1. Dong goi Backend Spring Boot..."
Set-Location (Join-Path $root "backend")
$revision = 'unversioned'
if (Test-Path (Join-Path $root '.git')) {
    $revision = (& git -C $root rev-parse --short=12 HEAD).Trim()
    if ($LASTEXITCODE -ne 0) { throw "Cannot identify source revision (exit code $LASTEXITCODE)" }
    if (& git -C $root status --porcelain) { $revision += '-dirty' }
}
$env:VITE_BUILD_REVISION = $revision
& .\mvnw.cmd -q clean package -DskipTests "-Daluminum.build.revision=$revision"
if ($LASTEXITCODE -ne 0) { throw "Backend build failed (exit code $LASTEXITCODE)" }
$jar = Get-ChildItem target\*.jar | Where-Object { $_.Name -notlike "*.original" } | Select-Object -First 1
if (-not $jar) {
    throw "Khong tim thay file JAR sau khi build backend"
}

$resourcesDir = Join-Path $root "frontend\src-tauri\resources"
New-Item -ItemType Directory -Force -Path $resourcesDir | Out-Null
Copy-Item $jar.FullName (Join-Path $resourcesDir "aluminum-manager.jar") -Force
Write-Host "   -> Da copy JAR vao $resourcesDir\aluminum-manager.jar"

Write-Host "2. Build Frontend React + Vite..."
Set-Location (Join-Path $root "frontend")
npm run build
if ($LASTEXITCODE -ne 0) { throw "Frontend build failed (exit code $LASTEXITCODE)" }

if (Get-Command cargo -ErrorAction SilentlyContinue) {
    Write-Host "3. Dong goi bo cai Windows bang Tauri..."
    npx tauri build
    if ($LASTEXITCODE -ne 0) { throw "Windows installer build failed (exit code $LASTEXITCODE)" }
} else {
    throw "Cannot build Windows installer: Rust/Cargo is missing from PATH"
}
