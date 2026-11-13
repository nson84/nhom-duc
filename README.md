# Quản lý hàng hóa nhôm

Ứng dụng desktop một máy cho cửa hàng nhôm: React + Vite + Tauri, Spring Boot và MySQL.

Ứng dụng có hai sổ Xưởng sản xuất và Phân phối nhôm khối; các vai trò Admin, Xưởng và Phân phối có quyền khác nhau. Các màn hình sản phẩm, phiếu nhập/bán, khách mua, thu chi, báo cáo, quyết toán xưởng và nhân sự xưởng đã được nối với backend. Tra cứu chức năng theo màn hình và module tại [docs/feature.md](docs/feature.md); quy tắc đọc/cập nhật tài liệu nằm trong [AGENTS.md](AGENTS.md).

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

Script tạo database `aluminum_app` và tài khoản ứng dụng `aluminum_app`. Mật khẩu trong script chỉ dùng lúc phát triển. Tài khoản này không phải `root`.

### Ghi đè tài khoản DB khi phát triển local

Nếu máy dev chỉ có tài khoản `root`, copy `scripts\dev-env.local.example.ps1` thành `scripts\dev-env.local.ps1` rồi điền user và mật khẩu. `dev-backend.ps1` tự nạp file này. File đã nằm trong `.gitignore` và không bao giờ được commit. `root` chỉ được chấp nhận như một ghi đè local của lập trình viên. Bản cài đặt đóng gói phải dùng tài khoản MySQL riêng, không phải `root`.

## Chạy backend và giao diện

Hai cửa sổ PowerShell:

```powershell
.\scripts\dev-backend.ps1
.\scripts\dev-frontend.ps1
```

Mở `http://127.0.0.1:5173`.

Ảnh sản phẩm lưu trong thư mục `image` ở gốc dự án khi chạy bằng `dev-backend.ps1`. Sao lưu thư mục này cùng database để giữ ảnh khi chuyển máy. Có thể đặt `ALUMINUM_IMAGE_DIR` để dùng thư mục khác.

Backend mặc định lắng nghe `127.0.0.1:8080`. Flyway tự chạy migration lúc khởi động. `AdminBootstrap` tạo ba tài khoản `admin`, `phanphoi`, `xuong` nếu chưa tồn tại, với mật khẩu ban đầu `1234567` và yêu cầu đổi mật khẩu. Tài khoản đã có giữ nguyên mật khẩu và trạng thái khi backend khởi động lại. Form đăng nhập tự điền `1234567` khi chọn tài khoản; nếu tài khoản đã đổi mật khẩu, hãy nhập mật khẩu hiện tại. Các biến cấu hình khác được khai báo trong `application.yml`:

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

## Quyết toán nhôm xưởng theo năm

### Nạp dữ liệu mẫu để thử kỳ 2025

Để xóa dữ liệu nghiệp vụ và tự nhập lại phiếu từ năm 2025, sao lưu MySQL trước rồi chạy `scripts/reset-business-data.sql`. Lệnh này xóa phiếu kho, thu chi, khách hàng, nhân viên, chấm công, kiểm kê và bản chốt; giữ danh mục nhôm, tài khoản và cấu hình. Giá nhập tham chiếu được đặt lại thành một mốc đầu kỳ 01/01/2025 từ giá hiện có của từng loại. Trường **Ngày phiếu** trên phiếu nhập/bán cho chọn ngày giờ quá khứ; phiếu Xưởng chỉ bị khóa nếu ngày đó thuộc kỳ đã chốt.

Khi MySQL đã chạy và mỗi sổ chỉ có ba loại nhôm mẫu, chạy từ thư mục gốc:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\seed-demo-data.ps1 -Year 2025 -DryRun
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\seed-demo-data.ps1 -Year 2025
```

Lệnh đầu tạo thử rồi hoàn tác; lệnh sau ghi dữ liệu trong một giao dịch. Script tự dừng nếu đã có phiếu, khách hàng, nhân viên, thu chi, kiểm kê hoặc bản chốt năm để tránh trộn hay nhân đôi dữ liệu. Nó giữ các loại nhôm và giá đã có. Dữ liệu mẫu gồm 30 phiếu nhập Phân phối (1.000 tấn), 100 phiếu bán Phân phối, 12 lần chuyển nội bộ (500 tấn) và 80 phiếu bán Xưởng cho 30 khách mỗi bên; có thanh toán đủ, nhiều lần, nợ một phần, chưa trả và thu/trả nợ trong đầu năm 2026. Tồn Phân phối cuối 2025 là 1 tấn; Xưởng có kiểm kê nháp 200 kg nguyên liệu, 100 kg phế liệu đã bán và 100 kg hao hụt ước tính. Mỗi ngày có khoản nước cho thợ, cùng các khoản chi khác, 5 nhân viên, chấm công và tăng ca. Chưa tạo bản chốt để có thể thử luồng chốt năm.

### In sổ cuối năm

Mở **Sổ cuối năm**, chọn năm và bấm **Tạo bản xem trước**. Admin có thể chọn **Cả hai sổ** để in chung một quyển; tài khoản vận hành chỉ in sổ của mình. Bản in gồm từng khách và phiếu bán/thu tiền, nợ chi tiết, sổ thu, sổ chi, phiếu nhập/nợ nhà cung cấp, công thợ Xưởng và tồn kho. Bấm **In / Lưu thành PDF**, chọn máy in PDF trong hộp thoại, rồi in/photocopy bản PDF. Số liệu chưa chốt có nhãn tạm tính.

Khi chốt năm Xưởng, ứng dụng lưu bản số dư nhôm, nợ khách, nợ nhà cung cấp và lãi tại thời điểm chốt. Phân phối chọn tên năm, đặt ngày bắt đầu và ngày chốt trên Tổng quan rồi bấm **Chốt năm** để có bản số dư tương tự. Sau đó chỉ cần chọn tên năm trên Tổng quan, Báo cáo hoặc Thu chi: ứng dụng hiện ngày bắt đầu/kết thúc đã lưu và lọc đúng kỳ. Kỳ kế tiếp bắt đầu sau ngày chốt; Sổ cuối năm cũng lấy mốc này. Bản chốt dùng để đối chiếu; tồn kho và công nợ tiếp tục theo phiếu gốc sang năm sau. Thu hoặc trả nợ cũ chỉ thay đổi dòng tiền và công nợ, không tính lãi lần hai.

Xưởng ghi phiếu nhập nhôm, phiếu bán thành phẩm đã giao và từng lần bán phế liệu. Phiếu bán xưởng mới không trừ tồn theo ngày. Vào **Quyết toán xưởng** để kiểm kê nhôm nguyên liệu, phế liệu chưa bán, hàng đang làm và thành phẩm chưa bán vào cuối năm. Báo cáo tính hao hụt ước tính và lãi quản trị theo giá trị tồn đầu/cuối năm; trang này cũng có công nợ khách theo tháng/năm. Phân phối tiếp tục quản lý tồn liên tục.

Nguyên liệu cuối năm được cân theo **từng loại nhôm**. Giá trị của mỗi loại lấy từ phiếu nhập xưởng đã xác nhận gần nhất, thiếu số kg thì lấy tiếp phiếu trước; chi tiết phân bổ được lưu khi chốt. Trên phiếu bán xưởng có thể chọn một phiếu nhập làm **giá nhôm tham chiếu** để lọc các phiếu bán theo giá/kg; giá này chỉ dùng để đối chiếu, không trừ kho hay cộng thêm giá vốn vào lãi năm. Admin mở **Tổng hợp hai bên** để xem doanh thu, tiền thu, nợ và lãi quản trị của phân phối cùng xưởng, đã loại giao dịch chuyển nội bộ khỏi doanh thu. Phân phối do chủ quản lý, không có nhân viên/chấm công/lương. Cả hai sổ chọn một trong năm khoản chi tại **Thu chi → Lập phiếu chi**: tiền điện, ăn mỗi ngày, nước và bánh kẹo giữa giờ, vật tư máy móc, sửa chữa hoặc thay thế bảo dưỡng. Chi trả phiếu nhập được ghi trên phiếu nhập.

## Giá nhập thay đổi theo ngày
Chuông nhắc kiểm tra giá bán lưu trong trình duyệt. Khi mở ứng dụng, các nhắc thuộc loại nhôm không còn trong database được gỡ tự động; nếu reset dữ liệu khi app đang mở, tải lại trang để đồng bộ.
Giá nhập không còn là một ô bị ghi đè. Mỗi loại nhôm có một lịch sử giá theo khoảng hiệu lực: vào **Sản phẩm → Lịch sử giá nhập** để đặt giá mới kèm ngày hiệu lực (mặc định hôm nay, có thể đặt trước cho ngày mai).

Khi lập phiếu nhập với đơn giá khác giá hiện tại, popup hiện giá cũ/mới và ngày bắt đầu áp dụng trước khi lưu. Có thể chỉ lưu phiếu hoặc đồng thời cập nhật giá chuẩn; lịch sử giữ cả mốc cũ nếu đổi nhiều lần trong cùng ngày.

Hai lớp số được giữ tách biệt:

- **Phân phối: giá vốn thực tế** — bình quân gia quyền từ các lần nhập thật. Mỗi phiếu bán lưu giá vốn tại lúc xác nhận.
- **Giá vốn chuẩn phân phối** — lấy theo giá nhập tham khảo có hiệu lực đúng **ngày phiếu**, lưu snapshot trên dòng phiếu. Phiếu bán xưởng có trường giá nhập tham chiếu riêng, không dùng làm giá vốn hằng ngày.

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
