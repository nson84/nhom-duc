# Chức năng backup

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

### `/sao-luu` — Sao lưu & phục hồi

| | |
|--|--|
| File | `features/backup/BackupsPage.tsx` |
| Ai dùng | **Admin only** |
| API | `/api/v1/backups` |

**Chức năng**
- Trạng thái lịch (tần suất, lần kế tiếp, thư mục), cảnh báo lỗi lần gần nhất
- Sao lưu ngay; danh sách bản sao lưu. Nút khôi phục tạm khóa vì backend chưa có quy trình xác minh trên database riêng; API trả 503, không ghi đè dữ liệu làm việc.
- Cấu hình tần suất 1/2/4/8/24 giờ, thư mục và số ngày giữ (chưa tự dọn bản cũ)
- Cấu hình bucket/endpoint/khóa R2, kiểm tra kết nối, đồng bộ ngay, xem/tải/kiểm tra bản trên R2. Secret Access Key không được hiển thị lại.
- Xuất khóa phục hồi để cất ngoài máy, nhập khóa trên máy mới; kiểm tra local/cloud và dựng database khôi phục thử. Database làm việc không bị thay thế; nút restore trực tiếp vẫn khóa.
- Khi dựng DB thử, nhập tài khoản MySQL có quyền CREATE DATABASE. Mật khẩu chỉ dùng cho lần yêu cầu đó.

## Bổ sung theo code hiện tại

Thao tác sao lưu và phục hồi dành cho Admin.
