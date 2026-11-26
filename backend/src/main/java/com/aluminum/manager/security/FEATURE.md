# Phân quyền và xác thực

JWT mới mặc định không hết hạn theo thời gian. Phiên đã thu hồi được lưu trong cơ sở dữ liệu để tiếp tục bị chặn sau khi khởi động lại.

Bản backup mới mã hóa hash của ba tài khoản vận hành để phục hồi giữ mật khẩu cũ; `revoked_login_sessions` không được xuất cùng archive, nên phiên đăng nhập cũ không được chuyển sang database mới.

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`SecurityConfig`, `JwtAuthenticationFilter`, `RoleAccessFilter`: Admin chỉ dùng API xác thực, Sao lưu, Cài đặt và tài khoản QR nhận tiền; Phân phối không truy cập nhân sự/lương; Xưởng có quyền vận hành nhân sự. Đối chiếu router và menu khi thay đổi.

Mọi phương thức ở chính xác `/api/v1/backups` và các đường dẫn con chỉ cho Admin; các vai trò Xưởng và Phân phối nhận 403. Truy cập chưa đăng nhập nhận 401 từ `SecurityConfig`.

Hai lệnh desktop `POST /api/v1/system/desktop-backup/{startup|shutdown}` không dùng JWT nhưng chỉ nhận loopback và mã ngẫu nhiên lưu trong tệp chỉ chủ Windows đọc được; mã đổi khi backend khởi động.

## Màn hình liên quan

[auth](../../../../../../../../frontend/src/features/auth/FEATURE.md), [shop](../../../../../../../../frontend/src/features/shop/FEATURE.md)


Hai endpoint desktop-backup/dev-handoff-check và desktop-backup/dev-handoff kiểm tra địa chỉ loopback, token local và cờ cho phép bàn giao do launcher dev đặt. Tiến trình ngoài không bật cờ bị từ chối.
