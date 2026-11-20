# Chức năng backup

20/11: kết quả dựng DB thử hiện thành khối thông tin cố định trong trang, có thể copy tên DB, thư mục ảnh và verified-recovery.json. Không chỉ dùng toast tự mất. Modal dựng local có loading và khóa thao tác trong lúc chờ để tránh bấm tạo lặp. DB này chưa tự trở thành DB vận hành; launcher và quy trình đối chiếu/quay lại nằm ở docs/recovery-runbook.md.

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

## Cập nhật rà soát 19/11/2026

Xem `docs/audit-progress-20261119.md` để biết thay đổi và giới hạn kiểm thử. Sổ tháng định giá tồn theo lớp nhập mới nhất, không FIFO; tiền công/phụ cấp/khấu trừ theo tháng. Ứng lương mới có lịch sử ngày, tiền và ghi chú, ghi chi thực tế và trừ thực lĩnh; số ứng tổng cũ không được tự gán ngày. Báo cáo thiếu giá vốn không hiển thị lợi nhuận giả bằng cách coi giá vốn là 0. Các bài MySQL phải dùng cấu hình thử riêng, không thay đổi database thật.
