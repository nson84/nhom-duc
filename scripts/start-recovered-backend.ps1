[CmdletBinding(SupportsShouldProcess)]
param(
    [Parameter(Mandatory)][string]$ProfilePath,
    [string]$JarPath = (Join-Path $PSScriptRoot '../backend/target/aluminum-manager-0.0.1-SNAPSHOT.jar'),
    [string]$DatabaseUsername,
    [Security.SecureString]$DatabasePassword,
    [ValidateRange(1024,65535)][int]$ApiPort = 8080,
    [switch]$EnableScheduledBackup
)
$ErrorActionPreference = 'Stop'
$profileFile = (Resolve-Path -LiteralPath $ProfilePath).Path
$profileDirectory = Split-Path $profileFile -Parent
$profile = Get-Content -LiteralPath $profileFile -Raw | ConvertFrom-Json
if ($profile.verified -ne $true -or $profile.databaseName -notmatch '^aluminum_restore_[a-f0-9]{12}$' -or
    (Split-Path $profileDirectory -Leaf) -ne $profile.databaseName) { throw 'Not a verified recovery profile' }
if ($profile.jdbcUrl -notmatch '^jdbc:mysql://127\.0\.0\.1:[0-9]+/(aluminum_restore_[a-f0-9]{12})(\?.*)?$' -or
    $Matches[1] -ne $profile.databaseName) { throw 'Recovery URL must select the verified local database' }
$imageDirectory = (Resolve-Path -LiteralPath $profile.imageDirectory).Path
if ($imageDirectory -ne (Join-Path $profileDirectory 'image')) { throw 'Recovery images must belong to the verified profile' }
$jar = (Resolve-Path -LiteralPath $JarPath).Path
if (-not $PSCmdlet.ShouldProcess($profile.databaseName, "Run backend on port $ApiPort; keep the old database intact")) { return }
$probe = [Net.Sockets.TcpClient]::new()
try {
    try { $probe.Connect('127.0.0.1', $ApiPort) } catch [Net.Sockets.SocketException] {}
    if ($probe.Connected) { throw "Port $ApiPort is occupied. Close the existing application first; no process was stopped." }
} finally { $probe.Dispose() }
if (-not $DatabaseUsername) { $DatabaseUsername = Read-Host 'MySQL username with access to the restored database' }
if (-not $DatabasePassword) { $DatabasePassword = Read-Host 'MySQL password (not saved)' -AsSecureString }
$variables = @('SPRING_DATASOURCE_URL','SPRING_DATASOURCE_USERNAME','SPRING_DATASOURCE_PASSWORD','APPDATA',
    'ALUMINUM_IMAGE_DIR','ALUMINUM_API_PORT','ALUMINUM_FIRST_RUN_CREDENTIAL_DIR','ALUMINUM_BACKUP_SCHEDULED_ENABLED')
$previous = @{}
foreach ($name in $variables) { $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$secretPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($DatabasePassword)
try {
    $env:SPRING_DATASOURCE_URL = $profile.jdbcUrl
    $env:SPRING_DATASOURCE_USERNAME = $DatabaseUsername
    $env:SPRING_DATASOURCE_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($secretPointer)
    $env:APPDATA = Join-Path $profileDirectory 'runtime'
    $env:ALUMINUM_IMAGE_DIR = $imageDirectory
    $env:ALUMINUM_API_PORT = [string]$ApiPort
    $env:ALUMINUM_FIRST_RUN_CREDENTIAL_DIR = Join-Path $profileDirectory 'runtime/credentials'
    $env:ALUMINUM_BACKUP_SCHEDULED_ENABLED = $EnableScheduledBackup.IsPresent.ToString().ToLowerInvariant()
    Write-Host "Starting $($profile.databaseName); old database and login accounts are unchanged."
    Write-Host "New installation login credentials: $env:ALUMINUM_FIRST_RUN_CREDENTIAL_DIR"
    Write-Host "Scheduled backup enabled: $env:ALUMINUM_BACKUP_SCHEDULED_ENABLED"
    & java -jar $jar
    if ($LASTEXITCODE -ne 0) { throw "Recovered backend stopped with exit code $LASTEXITCODE" }
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($secretPointer)
    foreach ($name in $variables) { [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process') }
}
