# Chức năng backup

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

### `/sao-luu` — Sao lưu & phục hồi

| | |
|--|--|
| File | `features/backup/BackupsPage.tsx` |
| Ai dùng | **Admin only** |
| API | `/api/v1/backups` |

**Chức năng**
- Trạng thái lịch (giờ, retention, thư mục), cảnh báo lỗi lần gần nhất
- Sao lưu ngay; danh sách bản sao lưu; phục hồi (xác nhận)
- Cấu hình lịch / thư mục / số ngày giữ

## Bổ sung theo code hiện tại

Thao tác sao lưu và phục hồi dành cho Admin.
