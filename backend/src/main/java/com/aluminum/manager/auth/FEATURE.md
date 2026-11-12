# Đăng nhập và phiên

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`AuthController` cung cấp login, logout, me và PUT password; phiên trả JWT, role và shop. Hiện `AdminBootstrap` đặt lại mật khẩu mặc định `123456`, role, shop và trạng thái hoạt động của ba tài khoản `admin`, `phanphoi`, `xuong` mỗi lần backend khởi động; không bắt buộc đổi mật khẩu. Các biến admin trong `application.yml` không được lớp bootstrap hiện tại sử dụng. Đây là hạn chế cần xử lý trước khi vận hành thực tế.

## Màn hình liên quan

[auth](../../../../../../../../frontend/src/features/auth/FEATURE.md)
