# Đăng nhập và phiên

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`AuthController` cung cấp login, logout, me và PUT password; phiên trả JWT, role và shop. `AdminBootstrap` chỉ tạo tài khoản còn thiếu trong ba tài khoản `admin`, `phanphoi`, `xuong`, yêu cầu đổi mật khẩu. Bản cài mới sinh mật khẩu ngẫu nhiên vào tệp chỉ chủ máy đọc; môi trường dev cấp `1234567` bằng `scripts/dev-backend.ps1`. Tài khoản đã có giữ nguyên mật khẩu, role, shop và trạng thái.

`PUT /auth/password` kiểm tra mật khẩu hiện tại. Người dùng được nhập lại đúng mật khẩu đó để hoàn tất bước xác nhận bắt buộc; hash giữ nguyên, cờ `must_change_password` được xóa và token cũ bị thu hồi. Nếu nhập mật khẩu khác, backend tạo hash mới như trước.

## Màn hình liên quan

[auth](../../../../../../../../frontend/src/features/auth/FEATURE.md)

Danh tính lịch sử từ backup có username __historical_ID, historical_username giữ tên nguồn và bị khóa inactive bởi V44. Backup mới giữ ba tài khoản vận hành và hash mật khẩu trong archive mã hóa; bootstrap chỉ tạo tài khoản còn thiếu, nên sau phục hồi bản mới mật khẩu cũ tiếp tục dùng được. Bản backup cũ vẫn cần mật khẩu lần đầu cho tài khoản thiếu.
