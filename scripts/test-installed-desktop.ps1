param(
    [Parameter(Mandatory)][string]$InstallerPath,
    [Parameter(Mandatory)][string]$MysqlDirectory,
    [Parameter(Mandatory)][string]$ExpectedRevision,
    [Parameter(Mandatory)][string]$ResultDirectory
)
$ErrorActionPreference = 'Stop'
if ($env:GITHUB_ACTIONS -ne 'true' -or $env:RUNNER_OS -ne 'Windows') { throw 'Installer lifecycle testing is restricted to a disposable Windows runner' }
foreach ($port in @(8080,13308)) {
    if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) { throw "Required test port $port is already occupied" }
}
$root = Join-Path $env:RUNNER_TEMP ('aluminum-desktop-smoke-' + [guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $root -Force | Out-Null
New-Item -ItemType Directory -Path $ResultDirectory -Force | Out-Null
$installDirectory = Join-Path $root 'installed'
$mysqlData = Join-Path $root 'mysql-data'
$server = Join-Path $MysqlDirectory 'bin/mysqld.exe'
$mysql = Join-Path $MysqlDirectory 'bin/mysql.exe'
if ((Get-AuthenticodeSignature -FilePath $server).Status -ne 'Valid') { throw 'MySQL binary signature is not valid' }
$db = $null; $app = $null; $backendProcessId = $null
Add-Type @'
using System;
using System.Text;
using System.Runtime.InteropServices;
public static class DesktopSmokeWindow {
  public delegate bool Callback(IntPtr h, IntPtr l);
  [DllImport("user32.dll")] static extern bool EnumWindows(Callback cb, IntPtr data);
  [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] static extern int GetWindowText(IntPtr h, StringBuilder text, int max);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint message, IntPtr w, IntPtr l);
  [DllImport("user32.dll")] public static extern bool ShowWindow(IntPtr h, int command);
  [DllImport("user32.dll")] public static extern bool SetForegroundWindow(IntPtr h);
  public static IntPtr Find(uint id) {
    IntPtr found=IntPtr.Zero;
    EnumWindows((h,l)=> { uint pid; GetWindowThreadProcessId(h,out pid); var text=new StringBuilder(512); GetWindowText(h,text,512);
      if(pid==id && text.ToString().Contains("Quản lý hàng hóa nhôm")) { found=h; return false; } return true; },IntPtr.Zero);
    return found;
  }
}
'@
try {
    Write-Host 'Installing NSIS candidate in the disposable directory...'
    $installation = Start-Process -FilePath $InstallerPath -ArgumentList @('/S',('/D='+$installDirectory)) -WindowStyle Hidden -PassThru
    if (-not $installation.WaitForExit(180000)) { throw 'NSIS installer exceeded its time limit' }
    if ($installation.ExitCode -ne 0) { throw "NSIS install failed: $($installation.ExitCode)" }
    $executables = @(Get-ChildItem -LiteralPath $installDirectory -Filter '*.exe' | Where-Object { $_.Name -notmatch '(?i)uninstall|webview' })
    if ($executables.Count -ne 1) { throw 'Expected exactly one installed desktop executable' }
    $executable = $executables[0].FullName
    if (-not (Get-ChildItem -LiteralPath $installDirectory -Filter 'aluminum-manager.jar' -Recurse)) { throw 'Installed backend JAR is missing' }
    Write-Host 'Installed executable and backend JAR found; preparing private MySQL...'
    New-Item -ItemType Directory -Path $mysqlData | Out-Null
    & $server --no-defaults --initialize-insecure "--basedir=$MysqlDirectory" "--datadir=$mysqlData" --console *> (Join-Path $ResultDirectory 'mysql-initialize.log')
    if ($LASTEXITCODE -ne 0) { throw 'Private MySQL initialization failed' }
    $db = Start-Process $server -ArgumentList @('--no-defaults',('--basedir="'+$MysqlDirectory+'"'),('--datadir="'+$mysqlData+'"'),'--port=13308','--bind-address=127.0.0.1','--mysqlx=OFF','--console') -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $ResultDirectory 'mysql.out.log') -RedirectStandardError (Join-Path $ResultDirectory 'mysql.err.log')
    for ($i=0; $i -lt 40; $i++) {
        if ($db.HasExited) { throw 'Private MySQL stopped' }
        if (Get-NetTCPConnection -State Listen -LocalPort 13308 -ErrorAction SilentlyContinue) { break }
        Start-Sleep -Seconds 1
    }
    $secret = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).Replace('+','-').Replace('/','_').TrimEnd('=')
    "ALTER USER 'root'@'localhost' IDENTIFIED BY '$secret';" | & $mysql --no-defaults --host=127.0.0.1 --port=13308 --user=root
    if ($LASTEXITCODE -ne 0) { throw 'Private MySQL credentials setup failed' }
    $env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:13308/aluminum_desktop_smoke?createDatabaseIfNotExist=true&allowPublicKeyRetrieval=true&sslMode=DISABLED'
    $env:SPRING_DATASOURCE_USERNAME = 'root'
    $env:SPRING_DATASOURCE_PASSWORD = $secret
    $env:APPDATA = Join-Path $root 'profile/Roaming'
    $env:LOCALAPPDATA = Join-Path $root 'profile/Local'
    $env:WEBVIEW2_USER_DATA_FOLDER = Join-Path $root 'webview'
    $env:ALUMINUM_FIRST_RUN_CREDENTIAL_DIR = Join-Path $root 'credentials'
    $env:ALUMINUM_BACKUP_SCHEDULED_ENABLED = 'false'
    $env:ALUMINUM_API_PORT = '8080'
    Remove-Item Env:ALUMINUM_DEV_BOOTSTRAP_PASSWORD -ErrorAction SilentlyContinue
    $app = Start-Process -FilePath $executable -WindowStyle Hidden -PassThru
    Write-Host 'Launched installed app; waiting for its owned backend...'
    $ready = $null
    for ($i=0; $i -lt 120; $i++) {
        if ($app.HasExited) { throw 'Packaged desktop exited before becoming ready' }
        try { $ready = Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/system/ready' -TimeoutSec 2; if ($ready.data.database -eq 'UP') { break } } catch {}
        Start-Sleep -Seconds 1
    }
    if ($ready.data.application -ne 'aluminum-manager' -or $ready.data.schemaVersion -ne '44' -or
        -not $ready.data.revision.StartsWith($ExpectedRevision.Substring(0,12))) { throw 'Installed backend identity/schema/revision did not match the artifact' }
    $backendProcessId = (Get-NetTCPConnection -State Listen -LocalPort 8080).OwningProcess | Select-Object -First 1
    $window = [DesktopSmokeWindow]::Find($app.Id)
    if ($window -eq [IntPtr]::Zero) { throw 'Packaged native window was not created' }
    Write-Host 'Backend identity/schema verified; inspecting the packaged window...'
    [DesktopSmokeWindow]::ShowWindow($window,5) | Out-Null
    [DesktopSmokeWindow]::SetForegroundWindow($window) | Out-Null
    Start-Sleep -Seconds 2
    & powershell.exe -NoProfile -File (Join-Path $PSScriptRoot 'check-desktop-ui.ps1') -WindowHandle $window.ToInt64() -ScreenshotPath (Join-Path $ResultDirectory 'packaged-login.png')
    if ($LASTEXITCODE -ne 0) { throw 'Packaged WebView did not render the login screen' }
    $credentialFile = Get-ChildItem $env:ALUMINUM_FIRST_RUN_CREDENTIAL_DIR -Filter '*.txt' | Select-Object -First 1
    $line = Get-Content -LiteralPath $credentialFile.FullName | Where-Object { $_.StartsWith('admin: ') }
    $login = Invoke-RestMethod 'http://127.0.0.1:8080/api/v1/auth/login' -Method Post -ContentType 'application/json' -Body (@{username='admin';password=$line.Substring(7)} | ConvertTo-Json)
    if ($login.data.role -ne 'ADMIN' -or -not $login.data.mustChangePassword) { throw 'Packaged first-run login did not enforce the password change' }
    Write-Host 'First-run authentication verified; closing the desktop window...'
    [DesktopSmokeWindow]::PostMessage($window,0x0010,[IntPtr]::Zero,[IntPtr]::Zero) | Out-Null
    if (-not $app.WaitForExit(30000)) { throw 'Packaged app did not close' }
    for ($i=0; $i -lt 30; $i++) {
        if (-not (Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue)) { break }
        Start-Sleep -Seconds 1
    }
    if (Get-NetTCPConnection -State Listen -LocalPort 8080 -ErrorAction SilentlyContinue) { throw 'Owned backend remained alive after closing the desktop' }
    $uninstaller = Get-ChildItem -LiteralPath $installDirectory -Filter '*uninstall*.exe' | Select-Object -First 1
    if (-not $uninstaller) { throw 'Installed uninstaller is missing' }
    Write-Host 'Owned backend stopped; uninstalling candidate...'
    $uninstall = Start-Process -FilePath $uninstaller.FullName -ArgumentList '/S' -WindowStyle Hidden -PassThru
    if (-not $uninstall.WaitForExit(120000)) { throw 'NSIS uninstaller exceeded its time limit' }
    if ($uninstall.ExitCode -ne 0) { throw 'NSIS uninstall failed' }
    for ($i=0; $i -lt 30 -and (Test-Path -LiteralPath $executable); $i++) { Start-Sleep -Seconds 1 }
    if (Test-Path -LiteralPath $executable) { throw 'Uninstall left the application executable behind' }
    @{ installed=$true; backendReady=$true; schema=$ready.data.schemaVersion; revision=$ready.data.revision;
       nativeWindow=$true; loginRendered=$true; firstRunLogin=$true; backendStoppedWithApp=$true; uninstalled=$true } |
        ConvertTo-Json | Set-Content (Join-Path $ResultDirectory 'summary.json')
    Write-Host 'PASS: installer, packaged backend, WebView login, first-run authentication, shutdown and uninstall.'
} finally {
    if ($app -and -not $app.HasExited) { Stop-Process -Id $app.Id -Force -ErrorAction SilentlyContinue }
    if ($backendProcessId) { Stop-Process -Id $backendProcessId -Force -ErrorAction SilentlyContinue }
    if ($db -and -not $db.HasExited) { Stop-Process -Id $db.Id -Force -ErrorAction SilentlyContinue }
    $backendLog = Join-Path $root 'profile/Roaming/AluminumManager/backend.log'
    if (Test-Path -LiteralPath $backendLog) { Copy-Item -LiteralPath $backendLog -Destination (Join-Path $ResultDirectory 'backend.log') }
    Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
}
exit 0
