param([ValidateRange(2000, 2100)][int]$Year = 2025, [switch]$DryRun)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$localEnv = Join-Path $PSScriptRoot 'dev-env.local.ps1'
if (Test-Path $localEnv) { . $localEnv }
if (-not $env:ALUMINUM_DB_PASSWORD) { $env:ALUMINUM_DB_PASSWORD = 'aluminum_app_dev' }
if (-not $env:ALUMINUM_DB_USERNAME) { $env:ALUMINUM_DB_USERNAME = 'aluminum_app' }
if (-not $env:ALUMINUM_DB_NAME) { $env:ALUMINUM_DB_NAME = 'aluminum_app' }

$connector = Get-ChildItem 'C:\Users\ADMIN\.m2\repository\com\mysql\mysql-connector-j' -Recurse -Filter 'mysql-connector-j-*.jar' |
    Where-Object { $_.Name -notmatch 'sources|javadoc' } |
    Sort-Object FullName -Descending |
    Select-Object -First 1
if (-not $connector) { throw 'MySQL Connector/J is missing from Maven cache. Build backend first.' }

$outputDir = Join-Path $projectRoot 'backend\target\demo-seed'
New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
& javac -encoding UTF-8 -cp $connector.FullName -d $outputDir (Join-Path $PSScriptRoot 'DemoDataSeeder.java')
if ($LASTEXITCODE -ne 0) { throw 'Demo seeder compilation failed' }
if ($DryRun) { & java -cp "$outputDir;$($connector.FullName)" DemoDataSeeder $Year --dry-run }
else { & java -cp "$outputDir;$($connector.FullName)" DemoDataSeeder $Year }
if ($LASTEXITCODE -ne 0) { throw 'Demo seeding failed; transaction was rolled back' }
