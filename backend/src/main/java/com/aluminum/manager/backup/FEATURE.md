# Sao lưu và phục hồi

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`BackupController`/`BackupService` quản lý sao lưu và lịch; mọi phương thức dưới `/api/v1/backups` chỉ dành cho Admin. Tài khoản chưa đăng nhập nhận 401, Xưởng/Phân phối nhận 403.

Khôi phục qua API hiện tạm trả 503 và không gọi `BackupService.restoreBackup`: cách cũ ghi trực tiếp vào database đang chạy trước khi kiểm thử trên database riêng. Không bật lại trước khi có bước dựng database thử, kiểm tra schema/dữ liệu/ảnh, chuyển đổi có thể quay lại và test khôi phục độc lập. Các file backup hiện có được giữ nguyên.

Bản sao lưu có trạng thái nhận thưởng lễ theo từng nhân viên. Phục hồi bản trước V24 sẽ bỏ các trạng thái thưởng không có trong bản sao lưu.

## Màn hình liên quan

Cấu hình Cloudflare R2 qua `GET/PUT /api/v1/backups/cloud` và thử kết nối qua `POST /api/v1/backups/cloud/test` (Admin). Secret Access Key chỉ nhận khi lưu, mã hóa DPAPI theo tài khoản Windows vào `%APPDATA%/AluminumManager/r2-config.properties` với ACL chủ sở hữu; API chỉ trả endpoint, bucket, Access Key ID và cờ đã cấu hình. Endpoint chỉ chấp nhận S3 API của Cloudflare R2.

Bản backup mới là `.ndbak`: SQL V2 và ảnh được nén GZIP, mã hóa AES-256-GCM bằng khóa 256 bit riêng của máy, ghi qua file tạm rồi đổi tên. Khóa lưu DPAPI ở `%APPDATA%/AluminumManager/backup-recovery-key.bin`; Admin phải xuất khóa và cất ngoài PC. Máy mới có thể nhập lại khóa, không ghi đè khóa đã tồn tại. Backup cũ `.sql` không tự tải lên cloud.

Sau khi backup thành công, tác vụ nền thử tải `.ndbak` cùng manifest lên R2; lịch thử lại mỗi 5 phút. Mỗi lần đồng bộ kiểm tra checksum local, giải mã thử, và HEAD object sau upload. Danh sách object R2 độc lập với `backup_logs`. Admin có thể đồng bộ, tải xuống, kiểm tra giải mã, và dựng database thử bằng Flyway. Database thử có tên `aluminum_restore_*`; ảnh được viết vào thư mục staging riêng. Không ghi đè database đang làm việc. Chuyển database thật và rollback tự động chưa được hỗ trợ; API restore trực tiếp tiếp tục trả 503.

Dựng database thử cần Admin nhập tạm tài khoản MySQL có quyền CREATE DATABASE. Mật khẩu chỉ đi trong yêu cầu cục bộ, không lưu trong cấu hình hoặc log ứng dụng. Tài khoản vận hành `aluminum_app` chỉ có quyền database hiện tại nên không thể dùng cho bước này trên máy phát triển hiện tại.

Lịch backup mặc định 4 giờ; chọn 1/2/4/8/24 giờ ở trang Admin. Không xóa bản backup local cũ theo retention lúc này, để tránh xóa bản cuối trước khi cloud được xác minh. Bộ tạo SQL V2 hiện vẫn dùng bộ nhớ theo kích thước dữ liệu và ảnh; cần tối ưu streaming trước khi dùng với dữ liệu rất lớn.

[backup](../../../../../../../../frontend/src/features/backup/FEATURE.md)

## Cập nhật rà soát 19/11/2026

Xem `docs/audit-progress-20261119.md` để biết thay đổi và giới hạn kiểm thử. Sổ tháng định giá tồn theo lớp nhập mới nhất, không FIFO; tiền công/phụ cấp/khấu trừ theo tháng. Ứng lương mới có lịch sử ngày, tiền và ghi chú, ghi chi thực tế và trừ thực lĩnh; số ứng tổng cũ không được tự gán ngày. Báo cáo thiếu giá vốn không hiển thị lợi nhuận giả bằng cách coi giá vốn là 0. Các bài MySQL phải dùng cấu hình thử riêng, không thay đổi database thật.
