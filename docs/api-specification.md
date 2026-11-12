# API hiện tại

Prefix `/api/v1`. Mọi phản hồi có `success`. Lỗi có `code` và `message`.

| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| POST | `/auth/login` | Công khai |
| POST | `/auth/logout` | Thu hồi phiên trong bộ nhớ |
| GET | `/auth/me` | Tài khoản hiện tại |
| PUT | `/auth/password` | Đổi mật khẩu, trả phiên mới |
| GET | `/system/ready` | Công khai, dùng cho màn hình khởi động |
| GET | `/settings` | Cấu hình cửa hàng và lịch sao lưu |
| PUT | `/settings` | Cập nhật cấu hình |
| GET | `/bank-accounts` | Danh sách tài khoản QR nhận tiền (nhiều TK; có ảnh thẻ) |
| GET | `/bank-accounts/default` | Tài khoản QR mặc định |
| POST | `/bank-accounts` | Thêm tài khoản QR |
| PUT | `/bank-accounts/{id}` | Sửa tài khoản / ảnh QR |
| POST | `/bank-accounts/{id}/default` | Đặt mặc định |
| DELETE | `/bank-accounts/{id}` | Xóa tài khoản QR |
| GET | `/dashboard/summary?from&to` | Doanh thu, tiền thu, lãi gộp, chi phí, tồn, tiền công |
| POST | `/dashboard/distribution-close?from&to&annual=true&year=2026` | Chốt năm Phân phối theo khoảng ngày tự chọn; `year` là tên kỳ. Kỳ liền sau phải bắt đầu ngày tiếp theo. |
| GET | `/dashboard/year-end?year` | Bản số dư chốt năm theo sổ đang chọn; `data: null` nếu chưa chốt. Gồm `periodFrom`, `periodTo`, `inventoryKg`, `inventoryValue`, `customerDebt`, `supplierDebt`, `profit`, `closedAt` |
| GET | `/reports/annual-book?year&shopCode` | Dữ liệu cho sổ cuối năm: khách, phiếu bán/nhập, thanh toán, thu chi, công thợ, tồn kho. Admin xem hai sổ; role vận hành chỉ xem `shopCode` của mình. |
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

Khoảng ngày kết thúc được hiểu là trước 00:00 ngày kế tiếp. Khoảng tối đa 400 ngày để hỗ trợ kỳ theo Tết âm lịch.

## Giá nhập theo ngày và chi phí chuẩn

Giá nhập tham khảo được quản lý theo loại nhôm (`category_purchase_prices`) và khoảng hiệu lực, tách khỏi giá thực tế trên phiếu nhập.

| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/categories` | Loại nhôm + giá nhập/bán (kg, tạ, tấn); trường giá m² cũ chỉ giữ để đọc dữ liệu lịch sử |
| POST / PUT | `/categories` / `/categories/{id}` | Tạo/sửa loại kèm giá; lịch sử giá nhập theo loại |
| GET/POST | `/categories/{id}/purchase-prices` | Lịch sử giá nhập loại (`pricePerBase` = đ/kg); đổi giá trong cùng ngày giữ mốc cũ, mốc ID mới nhất áp dụng cho ngày đó |
| POST | `/catalog/sync-to-other-shop` | Đẩy SP + loại sang shop kia (khớp mã/tên; giá copy lần đầu; không đụng tồn) |
| GET/POST | `/customers` | Sổ khách theo shop; Admin `?scope=all` xem gộp |
| GET/PUT | `/customers/{id}` | Chi tiết / sửa khách |
| GET | `/customers/{id}/documents` | Hóa đơn bán của khách (`paymentStatus`, `OPEN` = còn nợ) |
| GET/POST | `/customers/{id}/reminders` | Lịch nhắc nợ |
| GET | `/customers/due-reminders` | Nhắc đến hạn chưa xong |
| PUT | `/stock-documents/{id}/customer` | Gắn / gỡ `customerId` trên phiếu bán |
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
| GET | `/factory/years/{year}/report` | Khối lượng, hao hụt, doanh thu, chi phí nhôm và lãi năm; lãi/hao hụt `null` nếu chưa kiểm kê |
| GET/PUT | `/factory/years/{year}/count` | Xem/lưu tổng kiểm kê cùng `periodFrom`, `periodTo`; đầu kỳ lấy số cuối kỳ trước khi có |
| GET/PUT | `/factory/years/{year}/raw-counts` | Xem/lưu kg nguyên liệu theo loại và các lớp phiếu nhập gần nhất; báo lỗi nếu lịch sử nhập không đủ |
| GET | `/factory/purchase-costs` | Các dòng nhập xưởng đã xác nhận và chưa đảo, dùng làm lựa chọn giá tham chiếu |
| GET | `/factory/years/{year}/sale-costs?categoryId&costPerKg` | Lọc phiếu bán theo loại và giá tham chiếu, cộng kg và tiền |
| PUT | `/factory/sale-items/{itemId}/purchase-reference` | Gắn dòng nhập cùng loại, trước ngày bán cho dòng bán xưởng chưa thuộc năm đã chốt; body `{ "purchaseItemId": ... }` |
| POST | `/factory/years/{year}/close` | Chốt kỳ đã kết thúc, khóa phiếu có ngày thuộc kỳ |
| GET | `/factory/years/{year}/scrap-sales` | Phiếu phế liệu của năm |
| POST | `/factory/scrap-sales` | Bán phế liệu, ghi kg và tiền; không trừ tồn thường xuyên |
| POST | `/factory/scrap-sales/{id}/payments` | Ghi thu một phần hoặc đủ; tạo phiếu thu trong sổ thu chi |
| POST | `/factory/scrap-sales/{id}/void` | Hủy phiếu chưa thu tiền |
| GET | `/factory/customers/{id}/years/{year}` | Kg thành phẩm và từng dòng phiếu đã giao của khách |
| GET | `/factory/years/{year}/settlement?month=1..12&monthYear=2027` | Công nợ tháng giao với kỳ đã chọn; bỏ `month` để lấy cả kỳ. `monthYear` dùng cho kỳ đi qua năm dương lịch. |
| GET | `/admin/combined/years/{year}` | Admin xem tổng hai bên; loại giao dịch nội bộ khỏi doanh thu ngoài |

Mục lục [feature.md](feature.md) dẫn tới tài liệu từng module và màn hình. Quyết toán công nợ được tính theo dữ liệu tại lúc xem; dữ liệu nhập lùi ngày có thể thay đổi kết quả.

# Kỳ xem theo tên năm

`GET /api/v1/dashboard/year-period?year=2025` trả `{ year, periodFrom, periodTo, closed }` theo cửa hàng hiện tại. Mốc đã chốt là cố định; kỳ chưa chốt lấy kiểm kê nháp Xưởng hoặc bắt đầu ngay sau kỳ trước đã chốt. Tổng quan, báo cáo và thu chi dùng hai mốc này để lọc dữ liệu.
