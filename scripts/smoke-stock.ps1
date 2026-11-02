# Smoke test luong san pham -> nhap -> ban -> thu tien -> dao phieu. Chay khi backend dang chay.
# Tai khoan admin phai da duoc dang nhap duoc voi mat khau dua vao tham so.
param(
    [string]$Base = "http://127.0.0.1:8080/api/v1",
    [string]$User = "admin",
    [string]$Password = "123456",
    [string]$TempPassword = "123456"
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
$token = $login.data.token
$change = Call PUT "/auth/password" @{ currentPassword = $Password; newPassword = $TempPassword } $token
$login = Call POST "/auth/login" @{ username = $User; password = $TempPassword }
$token = $login.data.token

$suffix = Get-Random -Maximum 99999
$cat = Call POST "/categories" @{ name = "Test $suffix" } $token
$product = Call POST "/products" @{
    productCode = "T$suffix"; name = "Thanh test"; categoryId = $cat.data.id; baseUnitName = "kg"
    referencePurchasePrice = 0; defaultSellingPrice = 0
} $token
$prodId = $product.data.id
$kg = $product.data.units[0].id
$cay = (Call POST "/products/$prodId/units" @{ unitName = "cay"; factorToBase = 3.5; defaultSelling = $true } $token).data.units | Where-Object { $_.unitName -eq "cay" }

function Doc($type, $unit, $qty, $price) {
    return @{ documentType = $type; documentDate = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss"); counterpartyName = "Khach"
        items = @(@{ productId = $prodId; unitId = $unit; quantityDocument = $qty; unitPrice = $price }) }
}

"--- Nhap 100 kg gia 50000, roi 100 kg gia 60000 => TB 55000"
$p1 = Call POST "/stock-documents" (Doc "PURCHASE" $kg 100 50000) $token
$null = Call POST "/stock-documents/$($p1.data.id)/confirm" $null $token
$p2 = Call POST "/stock-documents" (Doc "PURCHASE" $kg 100 60000) $token
$null = Call POST "/stock-documents/$($p2.data.id)/confirm" $null $token
$bal = (Call GET "/stock-balances?q=T$suffix" $null $token).data[0]
"Ton: $($bal.quantityBase) kg, gia von: $($bal.averageCostPerBase)"

"--- Xac nhan lan hai phai bi chan"
(Call POST "/stock-documents/$($p2.data.id)/confirm" $null $token).code

"--- Ban 10 cay (35 kg)"
$s1 = Call POST "/stock-documents" (Doc "SALE" $cay.id 10 200000) $token
$s1c = Call POST "/stock-documents/$($s1.data.id)/confirm" $null $token
"Ma phieu: $($s1c.data.documentCode), gia von dong: $($s1c.data.items[0].costTotal)"
$bal = (Call GET "/stock-balances?q=T$suffix" $null $token).data[0]
"Ton con: $($bal.quantityBase)"

"--- Ban vuot ton phai bi chan"
$s2 = Call POST "/stock-documents" (Doc "SALE" $kg 1000 1) $token
(Call POST "/stock-documents/$($s2.data.id)/confirm" $null $token).code

"--- Thu 1.000.000 roi thu vuot"
$pay = Call POST "/stock-documents/$($s1.data.id)/payments" @{ amount = 1000000; paymentMethod = "CASH" } $token
"Trang thai: $($pay.data.paymentStatus), da thu: $($pay.data.paidAmount)"
(Call POST "/stock-documents/$($s1.data.id)/payments" @{ amount = 5000000; paymentMethod = "CASH" } $token).message

"--- Dao phieu da thu tien phai bi chan"
(Call POST "/stock-documents/$($s1.data.id)/reverse" @{ reason = "test" } $token).code

"--- Ban 1 cay chua thu tien roi dao"
$s3 = Call POST "/stock-documents" (Doc "SALE" $cay.id 1 200000) $token
$null = Call POST "/stock-documents/$($s3.data.id)/confirm" $null $token
$before = (Call GET "/stock-balances?q=T$suffix" $null $token).data[0].quantityBase
$rev = Call POST "/stock-documents/$($s3.data.id)/reverse" @{ reason = "Nhap nham" } $token
$after = (Call GET "/stock-balances?q=T$suffix" $null $token).data[0].quantityBase
"Ton truoc dao: $before, sau dao: $after, phieu dao: $($rev.data.documentCode)"
"Dao lan hai: " + (Call POST "/stock-documents/$($s3.data.id)/reverse" @{ reason = "lan 2" } $token).code
