# Smoke test landing page: khach gui form cong khai -> Admin doc va xu ly.
# Chay khi backend dang chay (scripts\dev-backend.ps1).
param(
    [string]$Base = "http://127.0.0.1:8080/api/v1",
    [string]$User = "admin",
    [string]$Password = "123456"
)
$ErrorActionPreference = "Continue"

function Call($method, $path, $body, $token) {
    $headers = @{ "Content-Type" = "application/json" }
    if ($token) { $headers.Authorization = "Bearer $token" }
    $params = @{ Method = $method; Uri = "$Base$path"; Headers = $headers }
    if ($body) { $params.Body = [Text.Encoding]::UTF8.GetBytes(($body | ConvertTo-Json -Depth 8)) }
    try { return Invoke-RestMethod @params }
    catch {
        $reader = New-Object IO.StreamReader($_.Exception.Response.GetResponseStream())
        return ($reader.ReadToEnd() | ConvertFrom-Json)
    }
}

$suf = Get-Random -Maximum 99999
$phone = "09" + (Get-Random -Minimum 10000000 -Maximum 99999999)

"=== 1. Khach gui form KHONG can dang nhap ==="
$lead = Call POST "/public/leads" @{
    fullName = "Khach Test $suf"
    phone = $phone
    email = "khach$suf@example.com"
    requestType = "BAO_GIA"
    message = "Can 200kg nhom loai 1"
    sourcePage = "landing"
}
"Cau tra loi cho khach: success=$($lead.success), id=$($lead.data.id)"

"=== 2. Bot dien truong an (honeypot) -> khong tao ban ghi ==="
$bot = Call POST "/public/leads" @{
    fullName = "Bot Spam"; phone = "0900000000"; website = "http://spam.example"; requestType = "BAO_GIA"
}
"success=$($bot.success), id=$($bot.data.id)  (id=0 la bi loai, nhung bot van thay nhu thanh cong)"

"=== 3. Nguoi la khong dang nhap doc danh sach khach -> phai bi chan ==="
try {
    $null = Invoke-WebRequest -Uri "$Base/leads"
    "LOI: doc duoc danh sach khi chua dang nhap!"
} catch {
    "Bi chan: HTTP " + [int]$_.Exception.Response.StatusCode + " (dung, phai la 401)"
}

"=== 4. Shop info cong khai khong lo cau hinh noi bo ==="
$info = (Call GET "/public/shop-info").data
"Ten cua hang: $($info.shopName)"
"Co lo backupDirectory? " + ($null -ne $info.backupDirectory) + " (phai la False)"
"Co noi dung landing? heroTitle=$($info.content.heroTitle)"

"=== 5. So dien thoai sai dinh dang bi tu choi ==="
$bad = Call POST "/public/leads" @{ fullName = "Test"; phone = "abc"; requestType = "BAO_GIA" }
"code=$($bad.code) (phai la VALIDATION_ERROR)"

"=== 6. Admin dang nhap va xem danh sach ==="
$token = (Call POST "/auth/login" @{ username = $User; password = $Password }).data.token
if (-not $token) { throw "Khong dang nhap duoc. Doi mat khau admin hoac truyen -Password." }
$list = (Call GET "/leads?q=$phone" $null $token).data
"Tim theo so dien thoai $phone -> $($list.Count) ket qua"
$id = $list[0].id

"=== 7. Admin danh dau da goi ==="
$updated = (Call PUT "/leads/$id" @{ status = "CONTACTED"; internalNote = "Da goi tu van, hen gui bao gia" } $token).data
"status=$($updated.status), contactedAt=$($updated.contactedAt), boi=$($updated.contactedBy)"

"=== 8. Ghi chu noi bo khong lo ra ngoai ==="
$public = (Call GET "/public/shop-info").data
"public tra ve truong internalNote? " + ($null -ne $public.internalNote) + " (phai la False)"

"=== 9. Tom tat ==="
$sum = (Call GET "/leads/summary" $null $token).data
"moi=$($sum.newCount), daGoi=$($sum.contactedCount), daXuLy=$($sum.closedCount), homNay=$($sum.todayCount)"

"=== 10. Admin sua chu trang gioi thieu ==="
$content = (Call GET "/landing-content" $null $token).data
$newTitle = "Smoke title $suf"
$content.heroTitle = $newTitle
$saved = (Call PUT "/landing-content" $content $token).data
"Luu heroTitle=$($saved.heroTitle)"
$public2 = (Call GET "/public/shop-info").data
"Cong khai doc duoc? $($public2.content.heroTitle -eq $newTitle)"
# Tra lai tieu de goc neu co
$content.heroTitle = "Nhôm cửa, nhôm hộp — báo giá trong 15 phút"
$null = Call PUT "/landing-content" $content $token
"Da tra lai tieu de mac dinh"
