# Cấu hình khởi tạo

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`AdminBootstrap` chỉ tạo tài khoản còn thiếu, yêu cầu đổi mật khẩu lần đầu và giữ nguyên tài khoản đã có. Bản cài mới tạo mật khẩu ngẫu nhiên riêng cho từng tài khoản trong tệp `first-run-credentials-*.txt` dưới `%APPDATA%\AluminumManager`, chỉ chủ máy được đọc. Nếu không ghi được tệp an toàn, bootstrap dừng trước khi tạo tài khoản. Khi chạy qua `scripts/dev-backend.ps1`, mật khẩu thử nghiệm `1234567` được cấp qua biến môi trường chỉ cho môi trường dev. `ClockConfig` cấu hình thời gian.

## Màn hình liên quan

[auth](../../../../../../../../frontend/src/features/auth/FEATURE.md), [system](../../../../../../../../frontend/src/features/system/FEATURE.md)
