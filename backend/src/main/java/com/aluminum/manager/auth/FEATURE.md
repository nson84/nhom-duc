# Đăng nhập và phiên

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`AuthController` cung cấp login, logout, me và PUT password; phiên trả JWT, role và shop. `AdminBootstrap` chỉ tạo tài khoản còn thiếu trong ba tài khoản `admin`, `phanphoi`, `xuong`, yêu cầu đổi mật khẩu. Bản cài mới sinh mật khẩu ngẫu nhiên vào tệp chỉ chủ máy đọc; môi trường dev cấp `1234567` bằng `scripts/dev-backend.ps1`. Tài khoản đã có giữ nguyên mật khẩu, role, shop và trạng thái.

## Màn hình liên quan

[auth](../../../../../../../../frontend/src/features/auth/FEATURE.md)
