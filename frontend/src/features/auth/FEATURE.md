# Chức năng auth

Phiên đăng nhập mới được lưu trong trình duyệt và không hết hạn theo thời gian. Đăng xuất xóa phiên ở các tab cùng địa chỉ.

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

### `/dang-nhap` — Đăng nhập

| | |
|--|--|
| File | `features/auth/LoginPage.tsx` |
| Ai dùng | Tất cả |
| API | `POST /api/v1/auth/login` |

**Chức năng:** chọn tài khoản (`admin` / `phanphoi` / `xuong`) + mật khẩu → JWT; sync `shopCode` vào store; nếu `mustChangePassword` → `/doi-mat-khau`. Admin được đưa tới Sao lưu, chỉ có Sao lưu và Cài đặt.

### `/doi-mat-khau` — Đổi mật khẩu bắt buộc

| | |
|--|--|
| File | `features/auth/ChangePasswordPage.tsx` |
| Ai dùng | User đang bị bắt đổi MK |
| API | `PUT /api/v1/auth/password` |

**Chức năng:** xác nhận lại mật khẩu hiện tại hoặc đặt mật khẩu mới trong bước bắt buộc; sau thành công vào app.

## Bổ sung theo code hiện tại

Đổi mật khẩu dùng `PUT /api/v1/auth/password`; đăng nhập dùng `POST /api/v1/auth/login`.

Form chỉ tự điền `1234567` khi Vite chạy ở chế độ dev. Bản production để trống mật khẩu; người cài mới đọc tệp mật khẩu lần đầu trong `%APPDATA%\AluminumManager`, đăng nhập và đổi mật khẩu. Backend không đặt lại mật khẩu khi khởi động; xem [tài liệu auth backend](../../../../backend/src/main/java/com/aluminum/manager/auth/FEATURE.md).

Giao diện đăng nhập dùng ảnh nền phủ tối, điểm nhấn champagne, nhãn tài khoản/mật khẩu, chuyển động mở trang và ánh sáng nền nhẹ. Hover/focus và trạng thái đang gửi được hiển thị; lỗi có role alert. Tắt animation khi prefers-reduced-motion. Màn nhỏ cuộn tự nhiên, không cố định chiều cao form. Không thay API hay quy tắc tài khoản.

Sau đăng nhập thành công, ghi nhớ tài khoản gần nhất trong localStorage. Trên desktop Windows, mật khẩu đã đăng nhập thành công được lưu riêng theo tài khoản trong Windows Credential Manager để tự điền khi chọn tài khoản ở lần mở sau; đổi mật khẩu thành công cập nhật mục đã lưu. Bản web chỉ giữ mật khẩu trong RAM của tab. Không lưu mật khẩu rõ vào localStorage/sessionStorage hoặc backup/R2; backend lưu hash trong bản backup mã hóa. Fallback 1234567 vẫn chỉ ở dev. Hồi quy: tests/login-memory.test.mjs.

Ghi nhớ tài khoản cũng lấy từ phiên đang tồn tại khi khởi động, cập nhật phiên và trước khi clear/logout; áp dụng cả phiên được tạo trước bản sửa giao diện. Không cần đăng nhập lại để chuyển phiên cũ sang cơ chế nhớ tài khoản.
