# Quản lý hàng hóa nhôm

Ứng dụng desktop một máy cho cửa hàng nhôm: React + Vite + Tauri, Spring Boot và MySQL.

Phiên bản hiện tại là **giai đoạn 1**. Đã có khung ứng dụng, đăng nhập một tài khoản Admin, cấu hình cửa hàng, tổng quan và schema đầy đủ. Nghiệp vụ sản phẩm, kho, chấm công, thu chi, báo cáo và sao lưu được khóa bằng quy tắc tính toán và bảng dữ liệu, giao diện các module đó sẽ nối ở các giai đoạn sau.

## Cần có trên máy

- JDK 17
- Node.js 20
- MySQL 8, chỉ lắng nghe máy local
- Rust và Cargo khi đóng gói cửa sổ Tauri

Máy phát triển hiện có JDK 17 và Node.js. MySQL và Rust chưa có trong `PATH`.

## Tạo database

Đăng nhập MySQL bằng tài khoản quản trị, rồi chạy:

```powershell
mysql -u root -p < scripts\init-mysql.sql
```

Script tạo database `aluminum_manager` và tài khoản ứng dụng `aluminum_app`. Mật khẩu trong script chỉ dùng lúc phát triển. Tài khoản này không phải `root`.

### Ghi đè tài khoản DB khi phát triển local

Nếu máy dev chỉ có tài khoản `root`, copy `scripts\dev-env.local.example.ps1` thành `scripts\dev-env.local.ps1` rồi điền user và mật khẩu. `dev-backend.ps1` tự nạp file này. File đã nằm trong `.gitignore` và không bao giờ được commit. `root` chỉ được chấp nhận như một ghi đè local của lập trình viên. Bản cài đặt đóng gói phải dùng tài khoản MySQL riêng, không phải `root`.

## Chạy backend và giao diện

Hai cửa sổ PowerShell:

```powershell
.\scripts\dev-backend.ps1
.\scripts\dev-frontend.ps1
```

Mở `http://127.0.0.1:5173`.

Backend chỉ lắng nghe `127.0.0.1:8080`. Flyway tự chạy migration lúc khởi động. Lần đầu, nếu chưa có Admin, hệ thống tạo tài khoản `admin` / `Admin@123` và bắt buộc đổi mật khẩu. Đổi các giá trị này bằng biến môi trường trước khi đưa vào cửa hàng:

- `ALUMINUM_ADMIN_USERNAME`
- `ALUMINUM_ADMIN_INITIAL_PASSWORD`
- `ALUMINUM_JWT_SECRET` (ít nhất 32 byte)
- `ALUMINUM_DB_USERNAME`
- `ALUMINUM_DB_PASSWORD`

Mật khẩu database và khóa ký phiên không nằm trong mã React.

## Kiểm thử đã chạy được không cần MySQL

```powershell
cd backend
.\mvnw.cmd test
```

Các phép thử này khóa quy tắc bán 10 cây × 3,5 kg, giá vốn bình quân, từ chối tồn âm, mã phiếu, tháng 2 nhuận và tăng ca chưa duyệt.

## Giá nhập thay đổi theo ngày
Giá nhập không còn là một ô bị ghi đè. Mỗi mã nhôm có một lịch sử giá theo khoảng hiệu lực: vào **Sản phẩm → Lịch sử giá nhập** để đặt giá mới kèm ngày hiệu lực (mặc định hôm nay, có thể đặt trước cho ngày mai).

Hai lớp số được giữ tách biệt:

- **Giá vốn thực tế** — bình quân gia quyền từ các lần nhập thật. Mỗi phiếu bán lưu giá vốn tại lúc xác nhận.
- **Giá vốn chuẩn** — lấy theo giá nhập có hiệu lực đúng **ngày phiếu**. Mỗi dòng phiếu lưu snapshot nên Admin sửa giá về sau không làm lệch lãi đã chốt.

Báo cáo **Chênh lệch giá nhập** cho biết nhập thực tế đắt hay rẻ hơn giá chuẩn; trong tab Doanh thu & Lợi nhuận có thêm *Lãi gộp theo giá nhập chuẩn* và *Chênh lệch giá nhập (thực tế − chuẩn)*.

Chạy thử luồng này khi backend đang chạy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-purchase-price.ps1
```

## Landing page cho khách

App có thêm trang giới thiệu công khai, **không cần đăng nhập**:

- Trong app (chạy local): `http://127.0.0.1:5173/gioi-thieu`
- Khách vào trang này, đọc giới thiệu, điền form **Họ tên + Số điện thoại** (email và nội dung không bắt buộc).
- Thông tin đổ về màn hình **Khách để lại thông tin** trong app để gọi lại. Đánh dấu *Đã gọi* sau khi liên hệ để không gọi trùng.

Trang giới thiệu **không** hiển thị giá nhập, tồn kho, doanh thu, lãi, lương hay cấu hình sao lưu. Nó chỉ lấy tên cửa hàng, địa chỉ, điện thoại và tên các loại nhôm đang bán từ `/api/v1/public/shop-info`.

Chống spam đã bật: trường ẩn cho bot, tối đa 5 lần/IP/ngày, gộp trùng số trong 1 giờ.

Chạy thử luồng này khi backend đang chạy:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\smoke-landing.ps1
```

### Đưa landing page lên Internet

Trang `/gioi-thieu` nằm trong cùng gói frontend với app quản lý. Khi deploy thật, cần:

1. **Biến môi trường khi build frontend**: `VITE_API_BASE_URL=https://api.ten-mien-cua-ban.vn`
2. **Domain của landing page**: `ALUMINUM_CORS_PUBLIC_ORIGINS=https://ten-mien-cua-ban.vn` — chỉ mở cho `/api/v1/public/**`, không mở quyền vào dữ liệu cửa hàng
3. **Bắt buộc đổi** `ALUMINUM_JWT_SECRET` (ít nhất 32 byte, ngẫu nhiên) và mật khẩu admin
4. **Nginx** phục vụ `frontend/dist` và proxy `/api` sang backend, kèm HTTPS
5. Chỉ mở route `/gioi-thieu` ra ngoài. App quản lý (`/`, `/san-pham`, ...) **không** nên mở công khai — nếu cần dùng từ xa, dùng VPN hoặc giới hạn IP.

## Đóng gói desktop

Sau khi cài Rust:

```powershell
cd frontend
npm run tauri dev
```

Tauri kiểm tra cổng 8080. Nếu backend chưa chạy và có file JAR, Tauri tự khởi động Java rồi dừng tiến trình đó khi đóng cửa sổ. Nhật ký nằm ở `%APPDATA%\AluminumManager\backend.log`.
