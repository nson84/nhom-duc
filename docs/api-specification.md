# API giai đoạn 1

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
| GET | `/dashboard/summary?from&to` | Doanh thu, tiền thu, lãi gộp, chi phí, tồn, tiền công |
| GET | `/dashboard/charts?from&to` | Theo ngày, múi giờ Việt Nam, `to` tính đến hết ngày |

## Landing page công khai (khách xem, để lại thông tin)

Đây là các endpoint **duy nhất** không cần đăng nhập. Chỉ trả thông tin giới thiệu và nhận form; không trả giá nhập, tồn kho, lãi, lương hay cấu hình sao lưu.

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

Khoảng ngày kết thúc được hiểu là trước 00:00 ngày kế tiếp. Khoảng tối đa 366 ngày.

## Giá nhập theo ngày và chi phí chuẩn

Giá nhập thay đổi theo ngày nên không ghi đè một ô duy nhất. `products.reference_purchase_price` chỉ là ảnh phản chiếu giá đang hiệu lực hôm nay.

| Method | Đường dẫn | Ghi chú |
| --- | --- | --- |
| GET | `/products?asOf=YYYY-MM-DD` | Danh sách sản phẩm kèm giá nhập có hiệu lực tại `asOf` (bỏ trống = hôm nay) |
| GET | `/products/{id}?asOf=YYYY-MM-DD` | Một sản phẩm kèm `purchasePriceEffectiveFrom` |
| GET | `/products/{id}/purchase-prices` | Lịch sử giá nhập, mỗi mốc có `effectiveFrom`, `effectiveTo`, `open`, `future` |
| POST | `/products/{id}/purchase-prices` | Ghi mốc giá mới: `{ pricePerBase, effectiveFrom?, note? }` |
| GET | `/reports/price-variance?from&to&categoryId` | Chênh lệch giá nhập thực tế so với giá nhập chuẩn |

Quy tắc bắt buộc:

- Mỗi dòng phiếu lưu `standard_cost_per_base_snapshot` và `standard_cost_total_snapshot` theo giá nhập chuẩn có hiệu lực đúng **ngày phiếu**, không theo giá hôm nay và không đọc lại giá hiện tại khi báo cáo.
- Mốc giá đã dùng trên phiếu thì không sửa; đổi giá sẽ mở mốc mới. Nhờ vậy sửa giá hôm nay không làm lệch lãi của phiếu đã xác nhận.
- Không ghi mốc giá có ngày hiệu lực trước mốc sớm nhất. Ngày nằm trước mọi mốc thì dòng phiếu không có giá chuẩn, và báo cáo trả `standardCostComplete = false`.
- Giá vốn thực tế vẫn là bình quân gia quyền (`InventoryMath`). Giá vốn chuẩn là lớp số thứ hai; `priceVariance = cogs − standardCogs`, dương nghĩa là nhập đắt hơn chuẩn.

Các API sản phẩm, phiếu kho, nhân sự, thu chi, báo cáo và sao lưu giữ đúng danh sách trong đặc tả và sẽ được nối ở giai đoạn tương ứng.
