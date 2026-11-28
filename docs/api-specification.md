# API hiện tại

Prefix `/api/v1`. Mọi phản hồi có `success`. Lỗi có `code` và `message`.

`POST /attendance/{id}/overtime` và `PUT /overtime/{id}` nhận `overtimeHours`, `approved`, `notes` và `hourlyRate` tùy chọn (đồng theo đơn vị đã chọn, không âm). Bỏ trống `hourlyRate` dùng mức cấu hình hiệu lực theo ngày công; response trả `hourlyRateSnapshot` và `amount` đã lưu.

Hai API tăng ca nhận thêm `payUnit: HOUR | SESSION` (thiếu thì mặc định HOUR). Với SESSION, gửi `overtimeHours: 1` để qua kiểm tra đầu vào; hệ thống lưu 0 giờ và tính đúng một mức tiền buổi. `hourlyRate` là đơn giá theo đơn vị đã chọn. API thêm nhân viên và mức công nhận `initialOvertimeSessionRate` hoặc `overtimeSessionRate`; response mức công trả `overtimeSessionRate`. Dữ liệu tăng ca trả `payUnit`.

| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| POST | `/auth/login` | Công khai |
| POST | `/auth/logout` | Thu hồi phiên trong bộ nhớ |
| GET | `/auth/me` | Tài khoản hiện tại |
| PUT | `/auth/password` | Xác nhận mật khẩu hiện tại hoặc đổi mật khẩu, trả phiên mới; chấp nhận mật khẩu mới trùng mật khẩu hiện tại |
| GET | `/system/ready` | Công khai, trả 503 trong lúc khởi tạo và trả thông tin sẵn sàng khi database dùng được |
| POST | `/system/desktop-backup/{startup\|shutdown}` | Desktop nội bộ: loopback và mã ngẫu nhiên riêng của backend, tạo bản backup khi mở/đóng app |
| GET | `/settings` | Cấu hình cửa hàng và lịch sao lưu |
| PUT | `/settings` | Cập nhật cấu hình |
| GET | `/bank-accounts` | Danh sách tài khoản QR nhận tiền (nhiều TK; có ảnh thẻ) |
| GET | `/bank-accounts/default` | Tài khoản QR mặc định |
| POST | `/bank-accounts` | Thêm tài khoản QR |
| PUT | `/bank-accounts/{id}` | Sửa tài khoản / ảnh QR |
| POST | `/bank-accounts/{id}/default` | Đặt mặc định |
| DELETE | `/bank-accounts/{id}` | Xóa tài khoản QR |
| GET | `/dashboard/summary?from&to` | Doanh thu, tiền thu, lãi gộp, chi phí, tồn, tiền công, kg nhập (`purchaseKg`) và kg giao |
| POST | `/dashboard/distribution-close?from&to&annual=true&year=2026` | Chốt năm Phân phối theo khoảng ngày tự chọn; `year` là tên kỳ. Kỳ liền sau phải bắt đầu ngày tiếp theo. |
| GET | `/dashboard/year-end?year` | Bản số dư chốt năm theo sổ đang chọn; `data: null` nếu chưa chốt. Gồm `periodFrom`, `periodTo`, `inventoryKg`, `inventoryValue`, `customerDebt`, `supplierDebt`, `profit`, `closedAt` |
| GET | `/dashboard/year-periods` | Danh sách kỳ đã lưu của sổ hiện tại theo thứ tự ngày bắt đầu. Trả cùng cấu trúc với `/dashboard/year-period`. `year` là mã tra cứu nội bộ. |
| POST | `/dashboard/year-reopen?year` | Mở lại bản chốt năm của sổ đang đăng nhập để sửa kiểm kê. Từ chối nếu có bản chốt phụ thuộc hoặc kho Phân phối đã biến động sau ngày chốt. |
| GET | `/reports/annual-book?year&shopCode` | Dữ liệu sổ cuối năm: khách, phiếu bán/nhập, thanh toán, thu chi, công thợ, tồn kho. `transfersOut`/`transferItems` chứa phiếu xuất nội bộ Phân phối trong kỳ; `purchases` của Xưởng gồm phiếu nhập nội bộ trước kỳ để đối chiếu. Xưởng/Phân phối chỉ xem sổ của mình; Admin bị từ chối. |
| GET | `/stock-movements?productId` | Biến động tồn của sản phẩm trong sổ hiện tại; mỗi dòng trả `internal` để nhận biết phiếu chuyển nội bộ. |
| GET | `/dashboard/charts?from&to` | Theo ngày, múi giờ Việt Nam, `to` tính đến hết ngày |

## Landing page công khai (khách xem, để lại thông tin)

Các endpoint `/public/**` không cần đăng nhập và chỉ trả thông tin giới thiệu hoặc nhận form. `/auth/login` và `/system/ready` cũng công khai.

| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/public/shop-info` | Tên cửa hàng, địa chỉ, điện thoại, danh mục đang bán |
| POST | `/public/leads` | Khách gửi form liên hệ |
| GET | `/leads?status&q` | **Cần đăng nhập.** Danh sách khách để lại thông tin |
| GET | `/leads/summary` | **Cần đăng nhập.** Số khách mới / đã gọi / đã xử lý / hôm nay |
| PUT | `/leads/{id}` | **Cần đăng nhập.** Cập nhật trạng thái và ghi chú nội bộ |

Quy tắc chống spam đã bật:

- Trường ẩn `website` (honeypot): bot điền vào thì bản ghi bị loại nhưng vẫn trả "thành công" để bot không biết.
- Tối đa 5 lần gửi cho mỗi IP mỗi ngày.
- Cùng số điện thoại trong 1 giờ thì cập nhật đơn cũ, không tạo rác.
- Số điện thoại phải khớp `^(0|\+84)[0-9]{8,10}$`.

Chạy thử: `powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-landing.ps1`

Với truy vấn đọc Thu chi và Báo cáo, ngày kết thúc được hiểu là trước 00:00 ngày kế tiếp. Có thể truy vấn kỳ đang mở kéo dài quá 400 ngày.

## Giá nhập theo ngày và chi phí chuẩn

Giá nhập tham khảo được quản lý theo loại nhôm (`category_purchase_prices`) và khoảng hiệu lực, tách khỏi giá thực tế trên phiếu nhập.

| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/reports/sales-by-product`, `/reports/inventory` | Trả từng cặp sản phẩm và loại nhôm. Bộ lọc loại dùng loại trên dòng phiếu bán hoặc dòng tồn kho. |
| GET | `/categories` | Loại nhôm + giá nhập/bán (kg, tạ, tấn); trường giá m² cũ chỉ giữ để đọc dữ liệu lịch sử |
| POST / PUT | `/categories` / `/categories/{id}` | Tạo/sửa loại kèm giá. Khi sửa, bỏ `referencePurchasePrice` để giữ lịch sử giá nhập hiện có. |
| GET/POST | `/categories/{id}/purchase-prices` | Lịch sử giá nhập loại (`pricePerBase` = đ/kg). Dòng lưu từ form loại nhôm có thêm `sellingPriceKg`, `sellingPriceTa`, `sellingPriceTon`. Mốc ID mới nhất áp dụng nếu trùng ngày. |
| POST | `/categories/{id}/purchase-prices/{priceId}/apply` | Áp dụng lại một bộ giá đủ dữ liệu của chính loại nhôm đó kể từ hôm nay. Trả lịch sử cập nhật. |
| POST | `/catalog/sync-to-other-shop` | Phân phối đẩy tên/ảnh SP và loại/giá nhập tham khảo sang Xưởng; giữ giá bán, mô tả, trạng thái SP/loại đã có và không đụng tồn |
| GET/POST | `/customers` | Sổ khách theo shop; Admin `?scope=all` xem gộp |
| GET/PUT | `/customers/{id}` | Chi tiết / sửa khách; GET trả `totalSales`, `receivedAmount`, `outstandingDebt`, `deliveredKg` |
| GET | `/customers/{id}/documents` | Hóa đơn bán của khách (`paymentStatus`, `OPEN` = còn nợ) |
| POST | `/stock-documents/payments/batch` | Thu hết nợ các phiếu bán ngoài được chọn của một khách. Body gồm `customerId`, `paymentDate`, `paymentMethod`, `collectedBy`, `receiptId` (UUID) và `invoices[{documentId, amount}]` (tối đa 500). Dùng kỳ và tháng của ngày thu. Trả danh sách phiếu cùng khoản thu để in biên nhận. |
| GET/POST | `/customers/{id}/reminders` | Lịch nhắc nợ |
| GET | `/customers/due-reminders` | Nhắc đến hạn chưa xong |
| PUT | `/stock-documents/{id}/customer` | Gắn / gỡ `customerId` trên phiếu bán |
| GET | `/stock-documents/delivered-by-category` | Kg đã giao theo loại từ phiếu bán đã xác nhận; lọc `from`, `to`, `categoryId`, `customerIds` (danh sách ID phân cách dấu phẩy), `scope=all` cho Admin |
| GET | `/dashboard/month-status` | Nhận `month=YYYY-MM`, trả `NOT_OPEN` / `OPEN` / `CLOSED` cho cửa hàng đang chọn |
| POST | `/dashboard/month-open` | Nhận `year`, `month`; mở tháng đầu hoặc tháng kế tiếp sau khi tháng trước đã khóa. Tháng còn mở thuộc kỳ đã chốt không chặn kỳ mới. |
| POST | `/dashboard/month-close` | Nhận `year`, `month`; khóa tháng đã mở sau khi hết tháng (Xưởng cần chốt kỳ công trước) |
| POST | `/dashboard/month-reopen` | Nhận `year`, `month=YYYY-MM`; mở lại tháng đã khóa của sổ đăng nhập nếu không có bản chốt liên quan |
| GET | `/products` | SP: mã, tên, ảnh, trạng thái, tổng tồn gộp |
| POST / PUT | `/products` / `/products/{id}` | Chỉ mã/tên/ảnh/trạng thái; đơn vị kg/tạ/tấn tự tạo |
| POST/PUT | stock documents items | Bắt buộc `categoryId`; phiếu bán xưởng nhận kg/tạ/tấn, lưu `quantityBase` là kg nhôm thành phẩm |
| GET | `/reports/price-variance?from&to&categoryId` | Chênh lệch giá nhập thực tế so với giá nhập chuẩn |

Quy tắc bắt buộc:

- Dòng phiếu phân phối lưu giá vốn thực tế và giá nhập chuẩn theo ngày. Phiếu bán Xưởng tự gắn dòng nhập hợp lệ gần nhất của cùng loại tính đến thời điểm bán làm giá tham chiếu.
- Mốc giá đã dùng trên phiếu thì không sửa; đổi giá sẽ mở mốc mới. Nhờ vậy sửa giá hôm nay không làm lệch lãi của phiếu đã xác nhận.
- Không ghi mốc giá có ngày hiệu lực trước mốc sớm nhất. Ngày nằm trước mọi mốc thì dòng phiếu không có giá chuẩn, và báo cáo trả `standardCostComplete = false`.
- Phân phối vẫn dùng giá vốn bình quân gia quyền (`InventoryMath`). Xưởng dùng báo cáo kiểm kê năm; phiếu bán xưởng mới không có giá vốn từng phiếu.

## Quyết toán xưởng

| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/factory/years/{year}/report` | Khối lượng, hao hụt, doanh thu, chi phí nhôm và lãi năm. Chỉ cộng phiếu nhập và bán thành phẩm có ngày thuộc kỳ, kể cả kỳ đầu tiên. Lãi và hao hụt `null` nếu chưa kiểm kê. Kỳ đã chốt trả lãi từ bản chốt nếu có. |
| GET/PUT | `/factory/years/{year}/count` | Xem/lưu tổng kiểm kê cùng `periodFrom`, `periodTo`; đầu kỳ lấy số cuối kỳ trước khi có |
| GET/PUT | `/factory/years/{year}/raw-counts` | Xem/lưu kg nguyên liệu theo loại và các lớp phiếu nhập gần nhất. Mỗi dòng có `openingKg` từ kiểm kê trước và `purchasedKg`, `deliveredKg` chỉ trong kỳ. Báo lỗi nếu lịch sử nhập không đủ để định giá. |
| GET | `/factory/purchase-costs` | Các dòng nhập xưởng đã xác nhận và chưa đảo, dùng làm lựa chọn giá tham chiếu |
| GET | `/factory/years/{year}/sale-costs?categoryId&costPerKg` | Lọc phiếu bán theo loại và giá tham chiếu, cộng kg và tiền |
| PUT | `/factory/sale-items/{itemId}/purchase-reference` | Gắn dòng nhập cùng loại, trước ngày bán cho dòng bán xưởng chưa thuộc năm đã chốt; body `{ "purchaseItemId": ... }` |
| POST | `/factory/years/{year}/close` | Chốt kỳ đã kết thúc, khóa phiếu có ngày thuộc kỳ |
| GET | `/factory/years/{year}/scrap-sales` | Phiếu phế liệu của năm |
| POST | `/factory/scrap-sales` | Bán phế liệu, ghi kg và tiền; không trừ tồn thường xuyên |
| POST | `/factory/scrap-sales/{id}/payments` | Ghi thu một phần hoặc đủ; tạo phiếu thu trong sổ thu chi |
| POST | `/factory/scrap-sales/{id}/void` | Hủy phiếu chưa thu tiền |
| GET | `/factory/customers/{id}/years/{year}` | Kg thành phẩm và từng dòng phiếu đã giao của khách |
| GET | `/factory/years/{year}/settlement?month=1..12&monthYear=2027` | Công nợ tháng giao với kỳ đã chọn; bỏ `month` để lấy cả kỳ. `monthYear` dùng cho kỳ đi qua năm dương lịch. `supplier` gồm `openingDebt`, `purchases`, `payments`, `closingDebt`. |

Mục lục [feature.md](feature.md) dẫn tới tài liệu từng module và màn hình. Quyết toán công nợ được tính theo dữ liệu tại lúc xem; dữ liệu nhập lùi ngày có thể thay đổi kết quả.

# Kỳ xem theo tên năm

`GET /api/v1/dashboard/year-period?year=2025` trả `{ year, periodName, periodFrom, periodTo, closed, started, endSaved }` theo cửa hàng hiện tại. Kỳ chưa bắt đầu có `started=false`; kỳ đang làm việc chưa lưu ngày kết thúc dùng ngày hiện tại làm mốc cuối tạm. Mốc đã chốt là cố định. Tổng quan, báo cáo và thu chi dùng hai mốc này để lọc dữ liệu.

`POST /api/v1/dashboard/year-period/start?year=2025&from=2025-01-29&name=Ky%202025-2026` lưu ngày bắt đầu và tên kỳ cho sổ hiện tại. `POST /api/v1/dashboard/year-period?year=2025&from=2025-01-29&to=2026-02-16` lưu ngày kết thúc dự kiến sau khi đã bắt đầu. Chốt năm là bước riêng, yêu cầu cả hai mốc đã lưu. Kỳ đã chốt không sửa được; khoảng tối đa 400 ngày và kỳ sau phải liền kề kỳ trước đã chốt.
## Sao lưu mã hóa và Cloudflare R2 (Admin)

| Method | API | Hành vi |
|---|---|---|
| GET/PUT | `/backups/settings` | Lịch backup, thêm `frequencyHours` (1, 2, 4, 8, 24); mặc định 4 giờ |
| POST | `/backups` | Tạo `.ndbak` nén GZIP và mã hóa AES-256-GCM, dọn bản trên máy vượt giới hạn 50 sau khi xác minh, rồi bắt đầu đồng bộ nền. Nhật ký bản đã dọn có trạng thái `PRUNED`. |
| POST | `/backups/{id}/verify` | Kiểm tra SHA-256 và giải mã bản local |
| POST | `/backups/{id}/prepare` | Dựng database khôi phục thử, không thay dữ liệu hiện hành |
| GET/PUT | `/backups/cloud` | Xem/lưu R2 endpoint, bucket, Access Key ID; Secret Access Key chỉ nhận khi ghi |
| POST | `/backups/cloud/test` | Kiểm tra kết nối R2 |
| GET | `/backups/cloud/status`, `/backups/cloud/objects` | Trạng thái đồng bộ và danh sách object trên R2 |
| POST | `/backups/cloud/sync` | Thử tải các bản `.ndbak` còn chờ lên R2 |
| POST | `/backups/cloud/download?key=...` | Tải archive từ R2 về máy, kiểm tra checksum |
| POST | `/backups/cloud/verify`, `/backups/cloud/prepare` | Body `{key,recoveryKey}`; kiểm tra hoặc dựng database thử từ cloud |
| GET | `/backups/recovery-key` | Xuất khóa phục hồi để cất ngoài máy |
| POST | `/backups/recovery-key/import` | Body `{value}`; nhập khóa trên máy mới, từ chối ghi đè khóa khác |
| POST | `/backups/{id}/restore` | Tiếp tục trả 503; chưa cho ghi đè database hoạt động |

GET /api/v1/dashboard/monthly-book?year=...&month=YYYY-MM đọc số liệu hoặc bản chốt. POST cùng đường dẫn với year, month, counts lưu bản chốt bất biến theo shop/tháng. counts gồm categoryId, rawKg, scrapKg.

categoryFlows của Xưởng gồm categoryId, openingKg, purchasedKg và deliveredKg. Hai số phát sinh lọc theo giao của tháng và kỳ. Bản chốt cũ có thể thiếu trường này.

## Bổ sung 19/11/2026

POST /api/v1/payroll/periods/{id}/details/{detailId}/advances: ghi ứng lương có date, amount, note, idempotencyKey. Chỉ kỳ lương và tháng đang mở; ngày ứng phải thuộc tháng lương và kỳ làm việc. Khoản ứng ghi chi PAYROLL_ADVANCE, trừ tiền còn lĩnh.

## Tồn đầu kỳ theo loại

GET /api/v1/dashboard/opening-inventory?year=2026&month=2026-03 (month tùy chọn). Chỉ role vận hành, theo shop hiện tại. Trả from, sourceTo, source MONTH/YEAR, available, rows(categoryId, category, rawKg, scrapKg), totalRawKg, totalScrapKg, message. Không có nguồn chốt liền trước thì available=false; không tự chia tổng tồn. scrapKg/totalScrapKg của nguồn YEAR là null vì kiểm kê vụn năm chưa tách loại. Endpoint chỉ đọc, không tạo phiếu hoặc biến động kho.

POST /dashboard/monthly-book: rawKg phải có; scrapKg có thể null/không gửi và được tạm ghi0. openingValue không bắt buộc, backend tự lấy nguồn đã lưu; frontend không gửi tiền. Thiếu giá trị đầu kỳ vẫn chốt kg được, profit=null. Định giá vụn và kiểm kê khi chốt năm vẫn thực hiện riêng.

## Bổ sung 20/11/2026

GET /api/v1/system/ready giữ application/status/database/schemaVersion/version/revision và trả thêm databaseName từ `SELECT DATABASE()`. Desktop dùng tên này để xác nhận chuyển DB; API không trả thông tin xác thực.

POST /api/v1/backups/{id}/prepare và /api/v1/backups/cloud/prepare trả databaseName, sqlStatements, imagesVerified, imageDirectory, profilePath. Hồ sơ sinh sau xác minh staging, không có tài khoản/mật khẩu MySQL. Quyền Admin giữ nguyên; restore trực tiếp vẫn503. Lệnh Tauri `activate_recovery`, `rollback_recovery`, `recovery_status` phục vụ chuyển/quay lại trong desktop, không mở thành HTTP API. Quy trình: recovery-runbook.md.


## Chuyển sổ giấy

- Stock document request nhận paperSource và items[].enteredCostPerKg. Chi tiết phiếu trả các trường này.
- PUT /api/v1/stock-documents/{id}/payments/{paymentId}/date nhận date dạng YYYY-MM-DD, trả phiếu đã cập nhật.
- POST /api/v1/stock-documents/{id}/payments nhận `confirmLockedCarryoverMonth` để ghi khoản thanh toán phiếu nợ chuyển kỳ trong tháng đã khóa. Màn Công nợ chỉ gửi `workingMonth` theo ngày thanh toán. Khi thiếu `workingYear`, backend dùng kỳ theo ngày thanh toán. Kỳ thanh toán phải còn mở và phiếu gốc phải lập trước ngày đầu kỳ với xác nhận qua khóa tháng.
- GET /api/v1/reports/carryover-debt trả `remainingDebt` tại cuối kỳ, `currentDebt` theo toàn bộ các khoản đã ghi và `payments` gồm lịch sử trước, trong và sau kỳ.
- API ghi và sửa ngày thanh toán từ chối ngày trước ngày lập phiếu. API sửa phiếu từ chối dời ngày lập qua ngày đã thanh toán.
- Finance transaction request và response có monthlySummary. transactionDate dùng mốc phân kỳ khi monthlySummary là true.
- GET /api/v1/reports/monthly-margins?from=YYYY-MM-DD&to=YYYY-MM-DD trả month, grossProfit, operatingCost, payrollCost, profitAfterExpenses. Lãi null khi thiếu giá vốn. Bản in năm trả monthlySummary trên các dòng thu chi.

POST /api/v1/dashboard/monthly-book/reconcile nhận year, month và items gồm productId, categoryId, countedQuantityKg, scrapKg tùy chọn. Phân phối dùng được với tháng đang mở hoặc đã khóa trong kỳ năm chưa chốt, miễn kho chưa có biến động sau ngày cuối tháng. Trả bản chốt tháng sau khi cập nhật kho trong cùng giao dịch. POST /api/v1/stock-balances/count cũng cho phép kiểm kê trong cả tháng đang mở và tháng đã khóa khi kỳ năm chưa chốt và kho chưa có biến động sau ngày kiểm kê.


DELETE /api/v1/dashboard/year-period?year=2025&from=2026-02-22 trả trạng thái kỳ sau khi xóa thiết lập. Chỉ Xưởng và Phân phối trong sổ tương ứng. Kỳ đã sử dụng hoặc ngày bắt đầu đã đổi trả lỗi kiểm tra.


Lưu ngày kết thúc dự kiến từ chối khi mốc mới loại tháng đã mở hoặc chứng từ, thu chi trong kỳ.


POST /api/v1/system/desktop-backup/dev-handoff-check và /dev-handoff chỉ gọi từ desktop qua loopback với X-Desktop-Backup-Token. Lệnh check trả imageDirectory. Lệnh bàn giao sao lưu thành công trước khi yêu cầu JVM dừng.


GET /api/v1/stock-documents?filter=internal-history và GET /api/v1/stock-documents/{id}?internalHistory=true cho Xưởng tra phiếu nhập nội bộ ngoài bộ lọc kỳ. Quyền ghi và số liệu kỳ giữ nguyên.


PUT /api/v1/finance/transactions/{id} dùng các trường của yêu cầu lập phiếu chi. Chỉ sửa khoản EXPENSE, MANUAL, CONFIRMED của sổ hiện tại, ngoài PAYROLL. Ngày gốc và ngày mới phải thuộc tháng mở chưa chốt sổ.

POST /api/v1/holidays cho phép tạo lịch ngày lễ tương lai trong kỳ đang làm việc khi không ghi mức công, thưởng hay người đã nhận. PUT ghi thưởng giữ kiểm tra mở tháng và kỳ công.

GET /api/v1/stock-documents lấy giao giữa from/to và kỳ đã chọn cho danh sách thường. GET /api/v1/dashboard/monthly-book trả stockAtClose và reconciliationBlock cho bản xem trước Phân phối. GET /api/v1/reports/inventory trả inventoryValue cuối khoảng ngày cho Phân phối, null nếu không đủ lớp giá; CSV thể hiện cùng mốc.
