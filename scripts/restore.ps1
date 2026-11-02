# Phuc hoi ban sao luu qua API backend local
param(
    [string]$Base = "http://127.0.0.1:8080/api/v1",
    [string]$User = "admin",
    [Parameter(Mandatory = $true)][string]$Password,
    [Parameter(Mandatory = $true)][long]$BackupId,
    [string]$Reason = "Phuc hoi tu script"
)
$ErrorActionPreference = "Stop"
$login = Invoke-RestMethod -Method POST -Uri "$Base/auth/login" -ContentType "application/json" -Body (@{ username = $User; password = $Password } | ConvertTo-Json)
$token = $login.data.token
$body = [Text.Encoding]::UTF8.GetBytes((@{ confirmText = "KHOI PHUC"; reason = $Reason } | ConvertTo-Json))
$res = Invoke-RestMethod -Method POST -Uri "$Base/backups/$BackupId/restore" -Headers @{ Authorization = "Bearer $token" } -ContentType "application/json; charset=utf-8" -Body $body
Write-Host "Da phuc hoi tu ban sao luu #$($res.data.id): $($res.data.backupPath)"
