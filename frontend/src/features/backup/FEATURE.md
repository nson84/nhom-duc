# Chức năng backup

22/11: sau khi dựng DB thử, ứng dụng desktop hiện nút **Dùng dữ liệu này**. Nút yêu cầu Tauri tự dừng backend đang sở hữu, mở DB đã xác minh, kiểm tra tên DB từ `/system/ready`, lưu lựa chọn và hiện mật khẩu đăng nhập lần đầu. Nếu không mở được, thử quay lại DB trước. Nút **Quay lại database trước** có trên trang Sao lưu sau khi chuyển. Trình duyệt ngoài desktop chỉ xem kết quả dựng thử.

21/11: màn Sao lưu có hướng dẫn mất máy ngay trên trang: tạo R2 token mới, tìm object `backups/*.ndbak`, dùng Dựng DB thử, đối chiếu rồi chạy launcher bằng hồ sơ đã xác minh. Tệp tải trực tiếp từ Cloudflare Dashboard chưa được nhập từ đường dẫn local vào ứng dụng. Bản nháp trình duyệt không nằm trong backup. Bộ cài mới mang launcher trong `resources` cùng JAR.

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

### `/sao-luu` — Sao lưu & phục hồi

| | |
|--|--|
| File | `features/backup/BackupsPage.tsx` |
| Ai dùng | **Admin only** |
| API | `/api/v1/backups` |

**Chức năng**
- Cảnh báo khôi phục trực tiếp giải thích rõ nút ghi đè database đang tạm khóa; sao lưu vẫn hoạt động và có thể dựng bản R2 trên database riêng để kiểm tra.
- Hai nút dựng thử được ghi rõ nguồn: **Dựng DB từ R2** cho trường hợp mất máy và **Dựng DB từ máy này** cho tệp local còn trên máy.
- Khi mới mở màn Sao lưu, trạng thái R2 hiển thị đang kiểm tra cho đến khi API trả cấu hình; không báo nhầm là chưa cấu hình trong lúc tải.
- Trạng thái lịch (tần suất, lần kế tiếp, thư mục), cảnh báo lỗi lần gần nhất
- Sao lưu ngay; danh sách bản sao lưu. Nút khôi phục tạm khóa vì backend chưa có quy trình xác minh trên database riêng; API trả 503, không ghi đè dữ liệu làm việc.
- Cấu hình tần suất 1/2/4/8/24 giờ, thư mục và số ngày giữ (chưa tự dọn bản cũ)
- Cấu hình bucket/endpoint/khóa R2, kiểm tra kết nối, đồng bộ ngay, xem/tải/kiểm tra bản trên R2. Secret Access Key không được hiển thị lại.
- Xuất khóa phục hồi để cất ngoài máy, nhập khóa trên máy mới; kiểm tra local/cloud và dựng database khôi phục thử. Database làm việc không bị thay thế; nút restore trực tiếp vẫn khóa.
- Khi dựng DB thử, nhập tài khoản MySQL có quyền CREATE DATABASE. Mật khẩu được giữ trong bộ nhớ giao diện tới khi chọn chuyển DB; khi chuyển, Tauri lưu trong Windows Credential Manager để lần mở ứng dụng sau dùng lại. Hồ sơ JSON không chứa mật khẩu.

## Bổ sung theo code hiện tại

Thao tác sao lưu và phục hồi dành cho Admin.

## Cập nhật rà soát 19/11/2026

Xem `docs/audit-progress-20261119.md` để biết thay đổi và giới hạn kiểm thử. Sổ tháng định giá tồn theo lớp nhập mới nhất, không FIFO; tiền công/phụ cấp/khấu trừ theo tháng. Ứng lương mới có lịch sử ngày, tiền và ghi chú, ghi chi thực tế và trừ thực lĩnh; số ứng tổng cũ không được tự gán ngày. Báo cáo thiếu giá vốn không hiển thị lợi nhuận giả bằng cách coi giá vốn là 0. Các bài MySQL phải dùng cấu hình thử riêng, không thay đổi database thật.
