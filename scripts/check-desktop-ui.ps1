param(
    [Parameter(Mandatory)][long]$WindowHandle,
    [Parameter(Mandatory)][string]$ScreenshotPath
)
$ErrorActionPreference = 'Stop'
if ($env:GITHUB_ACTIONS -ne 'true' -or $env:RUNNER_OS -ne 'Windows') { throw 'UI inspection is restricted to disposable Windows CI' }
# Run with Windows PowerShell 5.1 so the built-in .NET Framework UI Automation assemblies are available.
Add-Type -AssemblyName UIAutomationClient,UIAutomationTypes,System.Drawing,System.Windows.Forms
$window = [System.Windows.Automation.AutomationElement]::FromHandle([IntPtr]::new($WindowHandle))
if (-not $window) { throw 'Native application window is unavailable' }
# ASCII source also works under Windows PowerShell's legacy source encoding.
$loginLabel = ([string][char]0x0111) + [char]0x0103 + 'ng nh' + [char]0x1EAD + 'p'
$password = $false; $login = $false
$deadline = [DateTime]::UtcNow.AddSeconds(60)
do {
    $elements = $window.FindAll([System.Windows.Automation.TreeScope]::Descendants,[System.Windows.Automation.Condition]::TrueCondition)
    foreach ($element in $elements) {
        try {
            $current = $element.Current
            if ($current.IsPassword -and $current.ControlType -eq [System.Windows.Automation.ControlType]::Edit) { $password = $true }
            if ($current.ControlType -eq [System.Windows.Automation.ControlType]::Button -and $current.Name.ToLowerInvariant().Contains($loginLabel)) { $login = $true }
        } catch [System.Windows.Automation.ElementNotAvailableException] {}
    }
    if ($password -and $login) { break }
    Start-Sleep -Seconds 1
} while ([DateTime]::UtcNow -lt $deadline)
$bounds = $window.Current.BoundingRectangle
$screen = [System.Windows.Forms.SystemInformation]::VirtualScreen
$left = [Math]::Max([int]$bounds.Left,$screen.Left)
$top = [Math]::Max([int]$bounds.Top,$screen.Top)
$right = [Math]::Min([int]$bounds.Right,$screen.Right)
$bottom = [Math]::Min([int]$bounds.Bottom,$screen.Bottom)
if ($right -le $left -or $bottom -le $top) { throw 'Native window has no visible screenshot bounds' }
$bitmap = [System.Drawing.Bitmap]::new($right-$left,$bottom-$top)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
try {
    $graphics.CopyFromScreen($left,$top,0,0,[System.Drawing.Size]::new($bitmap.Width,$bitmap.Height))
    $bitmap.Save($ScreenshotPath,[System.Drawing.Imaging.ImageFormat]::Png)
} finally { $graphics.Dispose(); $bitmap.Dispose() }
if (-not $password -or -not $login) { throw 'Packaged UI did not expose the password input and Vietnamese login button' }
Write-Host 'PASS: Windows UI Automation found the real password input and login button; screenshot saved.'
exit 0
