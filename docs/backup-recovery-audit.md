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
- [ ] GZIP, AES-256-GCM, recovery key ngoài máy, manifest version + checksum, file tạm rồi finalize atomically, khóa tác vụ chạy trùng và thử khôi phục định kỳ.
- [ ] Cloudflare R2 qua S3 API: credential chỉ ở backend/Windows, test connection, upload queue/retry/idempotency/verification và liệt kê tải backup độc lập với `backup_logs`.
- [ ] Lịch 4 giờ mặc định, chọn 1/2/4/8/24 giờ, bắt kịp lịch bỏ lỡ và tách trạng thái local/cloud.

## P2

- [ ] Retention 7 ngày gần nhất + 4 tuần + 6 tháng, không xóa bản hợp lệ cuối cùng hoặc bản cloud còn Bucket Lock.
- [ ] UI Admin: cloud/local status, bản chờ upload, lịch kế tiếp, cấu hình, kiểm tra và lịch sử lỗi.
- [ ] Windows installer: thử NSIS trên máy sạch có Java/MySQL, đường dẫn dữ liệu, offline và vòng đời backend.
- [ ] CI dùng MySQL test và mock S3; diễn tập PC B với dữ liệu và ảnh của cả hai sổ. Chưa thể nghiệm thu recovery khi chưa chạy được diễn tập này.

## Kết quả kiểm thử tại thời điểm rà soát

- `backend/mvnw.cmd test`: 55 test, 0 thất bại.
- `frontend/npm run build`: đạt; cảnh báo bundle JS lớn.
- Flyway lúc backend khởi động: schema version 39, không còn migration chờ; 39 file SQL có phiên bản 1–39 duy nhất.
- HTTP không đăng nhập gọi `/api/v1/backups/status`: 401.
- `git diff --check`: không có lỗi nội dung sau khi sửa dòng trống cuối tài liệu.

Các test trên chưa xác minh backup có thể khôi phục. Không thực hiện restore trên database đang sử dụng.
