# Sao luu thu cong qua API backend local
param(
    [string]$Base = "http://127.0.0.1:8080/api/v1",
    [string]$User = "admin",
    [Parameter(Mandatory = $true)][string]$Password
)
$ErrorActionPreference = "Stop"
$login = Invoke-RestMethod -Method POST -Uri "$Base/auth/login" -ContentType "application/json" -Body (@{ username = $User; password = $Password } | ConvertTo-Json)
$token = $login.data.token
$backup = Invoke-RestMethod -Method POST -Uri "$Base/backups" -Headers @{ Authorization = "Bearer $token" }
Write-Host "Sao luu thanh cong: $($backup.data.backupPath) (SHA-256: $($backup.data.checksumSha256))"
