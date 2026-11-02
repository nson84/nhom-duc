# Smoke test Giai doan 4, 5, 6: Nhan vien -> Cham cong -> Tien cong -> Thu chi -> Bao cao -> Sao luu & Phuc hoi
param(
    [string]$Base = "http://127.0.0.1:8080/api/v1",
    [string]$User = "admin",
    [string]$Password = "123456"
)
$ErrorActionPreference = "Stop"

function Call($method, $path, $body, $token) {
    $headers = @{}
    if ($token) { $headers.Authorization = "Bearer $token" }
    $params = @{ Method = $method; Uri = "$Base$path"; Headers = $headers; ContentType = "application/json; charset=utf-8" }
    if ($body) { $params.Body = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 8)) }
    try { return Invoke-RestMethod @params }
    catch {
        $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
        return ($reader.ReadToEnd() | ConvertFrom-Json)
    }
}

$login = Call POST "/auth/login" @{ username = $User; password = $Password }
if (-not $login.success) {
    foreach ($old in @("Admin@123", "Smoke@12345")) {
        $try = Call POST "/auth/login" @{ username = $User; password = $old }
        if ($try.success) {
            $null = Call PUT "/auth/password" @{ currentPassword = $old; newPassword = $Password } $try.data.token
            $login = Call POST "/auth/login" @{ username = $User; password = $Password }
            break
        }
    }
}
$token = $login.data.token
"Dang nhap thanh cong voi mat khau 123456: $($login.success), mustChangePassword=$($login.data.mustChangePassword)"
$suf = Get-Random -Maximum 99999

"=== 1. Nhan vien & Muc cong ==="
$emp = Call POST "/employees" @{
    employeeCode = "NV$suf"
    fullName = "Nguyen Van A"
    phone = "0901234567"
    hireDate = "2026-01-01"
    active = $true
    initialDailyRate = 400000
    initialOvertimeWeekdayRate = 60000
    initialOvertimeRestdayRate = 80000
    initialOvertimeHolidayRate = 100000
} $token
$empId = $emp.data.id
"Tao NV: $($emp.data.employeeCode), cong ngay: $($emp.data.currentPayRate.dailyRate)"

"=== 2. Cham cong & Tang ca (thang 9/2026) ==="
$period = Call POST "/payroll/periods" @{ year = 2026; month = 9 } $token
if ($period.data.status -eq "CLOSED") {
    $period = Call POST "/payroll/periods/$($period.data.id)/reopen" @{ reason = "Mo de test" } $token
}
$att1 = Call PUT "/attendance/$empId/2026-09-10" @{
    status = "PRESENT"
    clockIn = "2026-09-10T08:00:00"
    clockOut = "2026-09-10T17:00:00"
    notes = "Lam du ngay"
} $token
"Ngay 10/09: cong=$($att1.data.creditedDays), gio=$($att1.data.workedHours)"

# Tang ca chua duyet (2h) + Tang ca da duyet (3h * 60,000 = 180,000)
$null = Call POST "/attendance/$($att1.data.id)/overtime" @{
    overtimeType = "WEEKDAY"; overtimeHours = 2; approved = $false; notes = "Chua duyet"
} $token
$attAfterOt = Call POST "/attendance/$($att1.data.id)/overtime" @{
    overtimeType = "WEEKDAY"; overtimeHours = 3; approved = $true; notes = "Da duyet"
} $token
$board = Call GET "/attendance/monthly?year=2026&month=9" $null $token
$empRow = $board.data.employees | Where-Object { $_.employeeId -eq $empId }
"Bang thang 09: tong cong=$($empRow.totalCreditedDays), OT da duyet=$($empRow.approvedOvertimeHours)h, tien OT=$($empRow.approvedOvertimePay)"

"=== 3. Tinh luong, chot ky, chan sua khi da chot, mo lai ky ==="
$period = Call POST "/payroll/periods" @{ year = 2026; month = 9 } $token
$periodId = $period.data.id
$closed = Call POST "/payroll/periods/$periodId/close" $null $token
"Trang thai sau chot: $($closed.data.status)"
$blockedEdit = Call PUT "/attendance/$empId/2026-09-10" @{ status = "ABSENT" } $token
"Sua cong khi da chot -> ma loi: $($blockedEdit.code)"

# Doi muc cong thang 10 -> ky thang 9 da chot khong duoc doi
$null = Call POST "/employees/$empId/pay-rates" @{
    effectiveFrom = "2026-10-01"; dailyRate = 500000; standardHoursPerDay = 8
    overtimeWeekdayRate = 70000; overtimeRestdayRate = 90000; overtimeHolidayRate = 120000
} $token
$afterRateChange = Call GET "/payroll/periods/$periodId" $null $token
$empDetail = $afterRateChange.data.details | Where-Object { $_.employeeId -eq $empId }
"Luong co ban thang 9 sau khi doi muc cong thang 10: $($empDetail.basePay) (giu nguyen 400000)"

$reopened = Call POST "/payroll/periods/$periodId/reopen" @{ reason = "Bo sung phu cap" } $token
"Mo lai ky cong -> status: $($reopened.data.status), ly do: $($reopened.data.reopenReason)"
$null = Call POST "/payroll/periods/$periodId/close" $null $token

"=== 4. Thu chi & Chống ghi trung ==="
$dupAttempt = Call POST "/finance/transactions" @{
    transactionType = "INCOME"; category = "SALE_RECEIPT"; amount = 100000; description = "Thu trung"
} $token
"Tao SALE_RECEIPT thu cong -> bi chan voi code: $($dupAttempt.code)"

$exp = Call POST "/finance/transactions" @{
    transactionType = "EXPENSE"; category = "OPERATING_EXPENSE"; amount = 250000
    description = "Tien dien thang 10"; idempotencyKey = "exp-$suf"
} $token
"Phieu chi hoat dong: $($exp.data.transactionCode) - $($exp.data.amount)"

"=== 5. Bao cao & Xuat CSV ==="
$profit = Call GET "/reports/profit?from=2026-10-01&to=2026-10-31" $null $token
"Bao cao thang 10: Doanh thu=$($profit.data.revenue), Gia von=$($profit.data.cogs), Lai gop=$($profit.data.grossProfit), Chi phi HD=$($profit.data.operatingExpenses)"

"=== 6. Sao luu & Phuc hoi ==="
$bk = Call POST "/backups" $null $token
"Sao luu thanh cong: id=$($bk.data.id), size=$($bk.data.fileSizeBytes), sha256=$($bk.data.checksumSha256.Substring(0,16))..."

# Ngung nhan vien trong form sua (active = false), roi phuc hoi tu backup -> nhan vien tro lai active = true
$disabled = Call PUT "/employees/$empId" @{
    employeeCode = "NV$suf"; fullName = "Nguyen Van A"; active = $false
} $token
"Trang thai NV sau khi disable trong form sua: active=$($disabled.data.active)"

$restored = Call POST "/backups/$($bk.data.id)/restore" @{
    confirmText = "KHOI PHUC"; reason = "Test phuc hoi"
} $token
"Ket qua phuc hoi: success=$($restored.success), msg=$($restored.message)"
$empAfterRestore = Call GET "/employees/$empId" $null $token
"Trang thai NV sau khi phuc hoi backup: active=$($empAfterRestore.data.active)"
