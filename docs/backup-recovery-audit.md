# Rà soát Backup & Cloud Recovery (15/11/2026)

HEAD khi bắt đầu: `bb1cbc50da03b109f9b0fee5f826a8258cb07581`. Nhánh làm việc: `feat/backup-recovery-hardening-20261115`. Các thay đổi nghiệp vụ đã có trong working tree được giữ nguyên. Không dùng database làm việc để thử restore.

## Đã có

- API và trang Admin sao lưu thủ công, lịch một lần/ngày, nhật ký, SHA-256, retention theo số ngày.
- File SQL V2 chứa dữ liệu nghiệp vụ hai sổ và ảnh sản phẩm; backup chạy trong giao dịch `REPEATABLE_READ` với consistent snapshot cho bảng InnoDB.
- Cấu hình backend localhost, JWT secret tự sinh trên máy nếu chưa cấp, installer Tauri khai báo JAR trong resources.

## P0

- [x] Chặn Xưởng/Phân phối ở mọi phương thức `/api/v1/backups/**`; test GET/POST/PUT/PATCH/DELETE. Chưa xác thực được kiểm tra 401 trên backend local.
- [x] Tạm khóa API restore trực tiếp (503) và nút trong UI: cách cũ chạy `DELETE`/`INSERT` trên database đang dùng, rollback DML không bảo đảm cứu được mọi tình huống. Giữ nguyên file backup.
- [ ] Dựng database tạm từ schema đúng phiên bản, nhập V2 vào đó, kiểm tra bảng/quan hệ/ảnh/số liệu, backup hiện trạng, maintenance mode, chuyển database và có rollback sang database cũ. Không thử trên database làm việc.
- [x] Bản production không dùng mật khẩu cố định; bootstrap sinh mật khẩu riêng, ghi tệp chỉ chủ máy đọc và không đổi tài khoản đã có. Chưa kiểm chứng trên NSIS/máy Windows sạch.
- [ ] Integration test phân quyền HTTP với database test riêng và kiểm thử restore nhiều lần.

## P1

- [ ] Đánh giá/thay SQL dump JDBC dùng `StringBuilder` và `readAllBytes`: hiện giữ toàn bộ dữ liệu và ảnh trong RAM. Kiểm tra Unicode, `BLOB`, thời gian, trigger, FK, concurrent writes và V2 compatibility bằng dữ liệu thử.
- [x] GZIP, AES-256-GCM, recovery key xuất/nhập ngoài máy, manifest version + checksum trên R2, file tạm rồi finalize, khóa tác vụ chạy trùng. Test round-trip và file bị sửa.
- [x] Cloudflare R2 qua S3 API: credential chỉ ở backend/Windows, test connection, upload/retry/idempotency/verification bằng mock S3 và liệt kê tải backup độc lập với `backup_logs`. Chưa thử token R2 thật sau khi xoay khóa.
- [x] Lịch 4 giờ mặc định, chọn 1/2/4/8/24 giờ, bắt kịp lịch bỏ lỡ và tách trạng thái local/cloud.

## P2

- [ ] Retention 7 ngày gần nhất + 4 tuần + 6 tháng, không xóa bản hợp lệ cuối cùng hoặc bản cloud còn Bucket Lock.
- [x] UI Admin: cloud/local status, bản chờ upload, lịch kế tiếp, cấu hình, kiểm tra và thông báo lỗi tổng quát.
- [ ] Windows installer: đã khai báo bundle backend JAR và chuyển thư mục ảnh về AppData; JAR đóng gói chứa S3/JNA. Chưa tạo NSIS vì máy hiện thiếu Cargo; Java và MySQL vẫn cần cài riêng. Cần thử trên máy Windows sạch.
- [ ] CI dùng MySQL test và mock S3; diễn tập PC B với dữ liệu và ảnh của cả hai sổ. Chưa thể nghiệm thu recovery khi chưa chạy được diễn tập này.

## Kết quả kiểm thử tại thời điểm rà soát

- `backend/mvnw.cmd test`: 55 test, 0 thất bại.
- `frontend/npm run build`: đạt; cảnh báo bundle JS lớn.
- Flyway lúc backend khởi động: schema version 39, không còn migration chờ; 39 file SQL có phiên bản 1–39 duy nhất.
- HTTP không đăng nhập gọi `/api/v1/backups/status`: 401.
- `git diff --check`: không có lỗi nội dung sau khi sửa dòng trống cuối tài liệu.

Các test trên chưa xác minh backup có thể khôi phục. Không thực hiện restore trên database đang sử dụng.

## Cập nhật sau khi triển khai R2

Maven test: 62 test, 0 thất bại. Frontend build đạt. Staging restore tạo database mới `aluminum_restore_*` qua Flyway, nhập SQL, kiểm tra số dòng và ảnh trong thư mục staging; chưa chạy với MySQL test riêng hoặc bucket R2 thật do chưa có token mới trên ứng dụng. Không bật thao tác chuyển database hoạt động hay xóa bản backup tự động. Các khóa xuất hiện trong ảnh chat trước đây phải xoay trước khi nhập vào app.

Ngày 15/11: full suite tăng lên 63 test, 0 lỗi; frontend build đạt. Backend khởi động trên MySQL thật, Flyway schema 39 không có migration chờ. Backup thủ công #5 tạo `.ndbak` 6.547 byte và API verify trả `VERIFIED`. Integration test dựng database mới bằng tài khoản ứng dụng thất bại đúng dự kiến vì `aluminum_app` không có quyền CREATE DATABASE; UI/API đã bổ sung chỗ nhập tạm tài khoản MySQL có quyền đó. Chưa thử lại bằng tài khoản có quyền. Chưa có cấu hình R2 mới trong ứng dụng nên chưa thử upload/download R2 thật. Không xóa/sửa dữ liệu nghiệp vụ.

Sau khi Admin nhập token R2 mới: API kết nối trả `CONNECTED`; đồng bộ được 2 archive `.ndbak` (bản scheduled và bản thủ công #5). Tải lại #5 từ R2 và giải mã trả `VERIFIED`, kể cả khi cung cấp recovery key trong yêu cầu như máy mới. Đồng bộ lần hai upload 0, hàng chờ 0, không có lỗi đồng bộ. Chưa dựng database thử bằng tài khoản có quyền CREATE DATABASE và chưa kiểm thử chuyển database hoạt động/rollback trên PC B.

## Cập nhật 19/11/2026

MySQL thử riêng cổng 13307 đã chạy 14 bài tích hợp (12 nghiệp vụ và 2 staging), 0 lỗi; 74 unit test, frontend npm ci/build đạt. Đã bổ sung kiểm tra FK sau import, ảnh staging, quyền thư mục0700 và giới hạn bộ đệm SQL. Kết quả này chưa chứng minh chuyển database hoạt động/rollback hoặc archive đầy đủ trên máy khác. Xem audit-followup-20261119.md.
