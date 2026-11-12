# Sao lưu và phục hồi

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`BackupController`/`BackupService` quản lý sao lưu, lịch, trạng thái và phục hồi; quyền Admin.

Bản sao lưu có trạng thái nhận thưởng lễ theo từng nhân viên. Phục hồi bản trước V24 sẽ bỏ các trạng thái thưởng không có trong bản sao lưu.

## Màn hình liên quan

[backup](../../../../../../../../frontend/src/features/backup/FEATURE.md)
