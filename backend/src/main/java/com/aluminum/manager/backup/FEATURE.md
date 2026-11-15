# Sao lưu và phục hồi

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`BackupController`/`BackupService` quản lý sao lưu và lịch; mọi phương thức dưới `/api/v1/backups` chỉ dành cho Admin. Tài khoản chưa đăng nhập nhận 401, Xưởng/Phân phối nhận 403.

Khôi phục qua API hiện tạm trả 503 và không gọi `BackupService.restoreBackup`: cách cũ ghi trực tiếp vào database đang chạy trước khi kiểm thử trên database riêng. Không bật lại trước khi có bước dựng database thử, kiểm tra schema/dữ liệu/ảnh, chuyển đổi có thể quay lại và test khôi phục độc lập. Các file backup hiện có được giữ nguyên.

Bản sao lưu có trạng thái nhận thưởng lễ theo từng nhân viên. Phục hồi bản trước V24 sẽ bỏ các trạng thái thưởng không có trong bản sao lưu.

## Màn hình liên quan

[backup](../../../../../../../../frontend/src/features/backup/FEATURE.md)
