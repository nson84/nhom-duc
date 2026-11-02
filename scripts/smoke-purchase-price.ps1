# Smoke test gia nhap thay doi theo ngay -> chi phi chuan -> lai thuc te vs lai chuan.
# Chay khi backend dang chay (scripts\dev-backend.ps1).
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
if (-not $token) { throw "Dang nhap that bai: $($login.message)" }

$suf = Get-Random -Maximum 99999
$cat = Call POST "/categories" @{ name = "Test gia $suf" } $token
$product = Call POST "/products" @{
    productCode = "PG$suf"; name = "Nhom test gia"; categoryId = $cat.data.id; baseUnitName = "kg"
    referencePurchasePrice = 75000; defaultSellingPrice = 95000
} $token
$prodId = $product.data.id
$kg = $product.data.units | Where-Object { $_.unitName -eq "kg" }
$today = Get-Date
$d0 = $today.ToString("yyyy-MM-dd")
$d1 = $today.AddDays(1).ToString("yyyy-MM-dd")
$from = $today.AddDays(-5).ToString("yyyy-MM-dd")
$to = $today.AddDays(10).ToString("yyyy-MM-dd")

"=== 1. Tao san pham voi gia nhap 75.000/kg, hieu luc tu $($product.data.purchasePriceEffectiveFrom) ==="
"Gia dang ap dung: $($product.data.referencePurchasePrice)"

"=== 2. Nhap 1.000 kg HOM NAY, gia thuc te = gia chuan 75.000 ==="
$buy1 = Call POST "/stock-documents" @{
    documentType = "PURCHASE"; documentDate = "${d0}T09:00:00"; counterpartyName = "NCC A"
    items = @(@{ productId = $prodId; unitId = $kg.id; quantityDocument = 1000; unitPrice = 75000; lineDiscount = 0 })
} $token
$buy1C = Call POST "/stock-documents/$($buy1.data.id)/confirm" $null $token
"  phieu $($buy1C.data.documentCode): gia von $($buy1C.data.items[0].costPerBase), gia chuan $($buy1C.data.items[0].standardCostPerBase) (chenh lech 0)"

"=== 3. Admin nang gia nhap len 82.000/kg tu NGAY MAI ==="
$null = Call POST "/products/$prodId/purchase-prices" @{ pricePerBase = 82000; effectiveFrom = $d1; note = "NCC tang gia tu mai" } $token
$prodNow = Call GET "/products/$prodId" $null $token
"  Gia hom nay van: $($prodNow.data.referencePurchasePrice) - tu $($prodNow.data.purchasePriceEffectiveFrom)"
$prodTomorrow = Call GET "/products/$prodId`?asOf=$d1" $null $token
"  Gia ngay mai: $($prodTomorrow.data.referencePurchasePrice)"

"=== 4. Nhap 1.000 kg NGAY MAI voi gia thuc te 78.000 -> gia chuan phai la 82.000 ==="
$buy2 = Call POST "/stock-documents" @{
    documentType = "PURCHASE"; documentDate = "${d1}T09:00:00"; counterpartyName = "NCC A"
    items = @(@{ productId = $prodId; unitId = $kg.id; quantityDocument = 1000; unitPrice = 78000; lineDiscount = 0 })
} $token
$buy2C = Call POST "/stock-documents/$($buy2.data.id)/confirm" $null $token
"  phieu $($buy2C.data.documentCode): gia von $($buy2C.data.items[0].costPerBase), gia chuan $($buy2C.data.items[0].standardCostPerBase)"

"=== 5. Sua gia ngay hom nay (da co phieu dung) -> mo moc moi, khong ghi de lich su ==="
$hist = Call POST "/products/$prodId/purchase-prices" @{ pricePerBase = 80000; note = "Chot lai gia hom nay" } $token
$hist.data | ForEach-Object { "  $($_.effectiveFrom) -> $(if($_.effectiveTo){$_.effectiveTo}else{'dang ap dung'}) : $($_.pricePerBase)" }
$buy1Reload = Call GET "/stock-documents/$($buy1.data.id)" $null $token
"  Phieu $($buy1Reload.data.documentCode) van giu gia chuan: $($buy1Reload.data.items[0].standardCostPerBase) (khong bi doi thanh 80.000)"

"=== 6. Chan ghi gia hieu luc truoc moc gan nhat ==="
$bad = Call POST "/products/$prodId/purchase-prices" @{ pricePerBase = 70000; effectiveFrom = $from } $token
"  success=$($bad.success) - $($bad.message)"

"=== 7. Ban 500 kg HOM NAY, gia ban 95.000 ==="
$sale = Call POST "/stock-documents" @{
    documentType = "SALE"; documentDate = "${d0}T14:00:00"; counterpartyName = "Khach le"
    items = @(@{ productId = $prodId; unitId = $kg.id; quantityDocument = 500; unitPrice = 95000; lineDiscount = 0 })
} $token
$saleC = Call POST "/stock-documents/$($sale.data.id)/confirm" $null $token
$item = $saleC.data.items[0]
"  Doanh thu: $($saleC.data.totalAmount)"
"  Gia von thuc te: $($item.costTotal) - Gia von chuan: $($item.standardCostTotal)"
$laiThuc = $saleC.data.totalAmount - $item.costTotal
$laiChuan = $saleC.data.totalAmount - $item.standardCostTotal
"  Lai thuc te: $laiThuc - Lai chuan: $laiChuan"

"=== 8. Bao cao ==="
$profit = Call GET "/reports/profit?from=$from&to=$to" $null $token
"  Gia von thuc te: $($profit.data.cogs) - Gia von chuan: $($profit.data.standardCogs) - Chenh lech: $($profit.data.priceVariance)"
"  Day du du lieu gia chuan: $($profit.data.standardCostComplete)"
$var = Call GET "/reports/price-variance?from=$from&to=$to" $null $token
$var.data | Where-Object { $_.productCode -eq "PG$suf" } | ForEach-Object {
    "  Moc $($_.effectiveFrom): chuan $($_.standardPricePerBase) / thuc te BQ $($_.actualAverageCostPerBase) tren $($_.actualQuantityBase) kg -> chenh lech $($_.varianceValue)"
}
