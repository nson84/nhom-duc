# Dong goi ung dung desktop Windows (Backend JAR + Frontend dist + Tauri bundle neu co Rust)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

Write-Host "1. Dong goi Backend Spring Boot..."
Set-Location (Join-Path $root "backend")
& .\mvnw.cmd -q clean package -DskipTests
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

if (Get-Command cargo -ErrorAction SilentlyContinue) {
    Write-Host "3. Dong goi bo cai Windows bang Tauri..."
    npx tauri build
} else {
    Write-Host "3. May chua cai Rust/Cargo trong PATH nen bo qua buoc 'tauri build'. Backend JAR va Frontend dist da san sang."
}
