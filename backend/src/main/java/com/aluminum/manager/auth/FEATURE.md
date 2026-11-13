# Đăng nhập và phiên

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`AuthController` cung cấp login, logout, me và PUT password; phiên trả JWT, role và shop. `AdminBootstrap` chỉ tạo tài khoản còn thiếu trong ba tài khoản `admin`, `phanphoi`, `xuong` với mật khẩu ban đầu `1234567` và yêu cầu đổi mật khẩu. Tài khoản đã có giữ nguyên mật khẩu, role, shop và trạng thái; các biến admin trong `application.yml` không được lớp bootstrap hiện tại sử dụng.

## Màn hình liên quan

[auth](../../../../../../../../frontend/src/features/auth/FEATURE.md)
