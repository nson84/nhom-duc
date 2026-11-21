# Sao lưu và phục hồi

21/11: launcher `scripts/start-recovered-backend.ps1` được đóng gói cạnh `aluminum-manager.jar` trong tài nguyên Tauri; tự tìm JAR cùng thư mục khi chạy từ bộ cài. Luồng tải R2, xác minh, dựng DB riêng và giữ DB cũ không đổi. Xem `docs/backup-recovery-guide.md` cho các bước trên máy mới.
`R2LatestBackupReadIT` là kiểm thử tùy chọn chỉ đọc object `.ndbak` mới nhất trên R2 thật, so kích thước/checksum rồi giải mã bằng khóa máy đang dùng; không xuất nội dung và không ghi R2.

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

Sau khi mở desktop khoảng 30 giây, desktop yêu cầu một bản `startup` kể cả khi backend đã chạy sẵn. Khi đóng bình thường, desktop yêu cầu bản `shutdown`: nếu sở hữu backend thì dùng stdin rồi dừng Java; nếu dùng backend có sẵn thì dùng API loopback và giữ backend đó chạy. Sau bản `shutdown` thành công, backend đợi thử đồng bộ R2 trước khi trả lời/thoát; lỗi mạng vẫn giữ bản local và được thử lại khi backend chạy lần sau. API yêu cầu mã ngẫu nhiên trong tệp chỉ chủ Windows đọc được, đổi mỗi lần backend khởi động; không nhận truy cập từ mạng ngoài. Backend cũ không có API này cần được khởi động lại bằng bản mới. Tick đầu riêng của backend (trễ khoảng 30 giây) vẫn tạo backup nếu bản gần nhất đã cách ít nhất 5 phút; các tick sau theo lịch 1/2/4/8/24 giờ. Bản thành công được thử đồng bộ lên R2; lỗi mạng được thử lại mỗi 5 phút khi backend còn chạy. Lịch tắt ở chế độ diễn tập phục hồi tắt backup tự động. Không xóa bản local cũ theo retention lúc này. Bộ tạo SQL V2 hiện vẫn dùng bộ nhớ theo kích thước dữ liệu và ảnh; cần tối ưu streaming trước khi dùng với dữ liệu rất lớn.

[backup](../../../../../../../../frontend/src/features/backup/FEATURE.md)

## Cập nhật rà soát 19/11/2026

Xem `docs/audit-progress-20261119.md` để biết thay đổi và giới hạn kiểm thử. Sổ tháng định giá tồn theo lớp nhập mới nhất, không FIFO; tiền công/phụ cấp/khấu trừ theo tháng. Ứng lương mới có lịch sử ngày, tiền và ghi chú, ghi chi thực tế và trừ thực lĩnh; số ứng tổng cũ không được tự gán ngày. Báo cáo thiếu giá vốn không hiển thị lợi nhuận giả bằng cách coi giá vốn là 0. Các bài MySQL phải dùng cấu hình thử riêng, không thay đổi database thật.

Tệp khóa/cấu hình dùng ACL chỉ chủ sở hữu trên Windows và quyền POSIX 0600 trên Linux; filesystem không hỗ trợ cả hai bị từ chối. Có test kiểm tra quyền thực tế trên từng nền tảng.

Rà soát 19/11: quyền POSIX tệp0600/thư mục0700; staging kiểm tra khóa ngoại thực tế sau import, vì SET FOREIGN_KEY_CHECKS=1 không kiểm tra lại dòng cũ. SQL dump giới hạn 32 Mi ký tự và kiểm tra dung lượng ảnh trước đọc; vẫn dùng RAM, chưa streaming đầy đủ. Test giới hạn bộ đệm và staging SQL/ảnh/FK. Restore production vẫn khóa.

Audit tại HEAD b290e3c: bộ tạo dump giữ cột DEFAULT_GENERATED (đặc biệt created_at/updated_at), chỉ bỏ cột tính toán STORED/VIRTUAL GENERATED. Staging đối chiếu mọi đường dẫn product-image trong bảng products với ảnh đã xác minh và dựng ở thư mục staging; thiếu ảnh phải trả lỗi. BackupBusinessMysqlIT bảo vệ dữ liệu hai sổ, quan hệ dòng hàng/thanh toán/nhân sự, ảnh và thời điểm lịch sử bằng database thử riêng.

Tại audit b290, phục hồi có tác giả vẫn NOT_VERIFIED. Giới hạn này được xử lý cho backup mới bởi V44 bên dưới; backup cũ thiếu tác giả vẫn không thể tự bổ sung.

Audit b361221: mở rộng test public createManualBackup/verifiedArchive, SHA256, PNG,26 bảng bao gồm cặp nội bộ, phế liệu, raw layers và snapshot tháng/năm. Liệt kê thêm FK local của attendance_day_locks và customer_leads; không tự sửa liên kết hoặc khôi phục tài khoản. Test máy mới có tài khoản khác cùngID vẫn bị từ chối thay vì gán nhầm tác giả. Workload gần32Mi ký tự khôi phục đủ, vượt giới hạn báo FAILED và giữ bản cũ, không gọi upload file lỗi. Dump vẫn dùng RAM; số đo và giới hạn tại docs/audit-next-main.md.

20/11: V44 giữ các tác giả được tham chiếu bằng bản ghi không đăng nhập được (__historical_ID, tên gốc ở historical_username, mật khẩu sentinel, inactive bắt buộc bởi CHECK). Không xuất hash/phiên/quyền cũ; giữ ID FK và ngày lịch sử, cả audit_logs. Database mới có account trùng ID không bị thay đổi vì luôn dựng DB riêng. Test closed/reopened payroll, khóa ngày công, lead và nhật ký; kiểm tra không kích hoạt lại được actor.

Sau xác minh staging, trả thêm profilePath và ghi hồ sơ JSON chỉ chủ máy đọc, không chứa thông tin xác thực MySQL. Launcher start-recovered-backend.ps1 từ chối URL DB thật, cổng đã bận và ảnh ngoài hồ sơ. Dùng APPDATA/credentials riêng, phục hồi env khi dừng. aluminum.backup.scheduled-enabled=false tắt cả tick local lẫn retry R2 cho diễn tập; mặc định ứng dụng bình thường vẫn true. Không bật restore trực tiếp. Hướng dẫn chuyển và quay lại: docs/recovery-runbook.md.

BackupSqlPolicy giới hạn câu lệnh staging vào SET FOREIGN_KEY_CHECKS, DELETE và INSERT chỉ có literal đúng định dạng dump. Không cho USE, DDL, bảng có schema, hàm/biểu thức hoặc nhiều câu lệnh. Kiểm tra trước khi tạo DB; import dùng MySQL backslash escaping theo writer kể cả khi server bật NO_BACKSLASH_ESCAPES. Không thay định dạng V2 hoặc thực thi SQL tự do từ archive.

R2LiveRecoveryIT là test opt-in riêng với ALUMINUM_TEST_R2_CONFIG_FILE trỏ cấu hình DPAPI có sẵn và MySQL thử loopback. Không chạy trong CI thường. Chỉ thử fixture AES riêng dưới verification/UUID, kiểm tra PUT/HEAD/GET, so byte, giải mã/dựng DB rồi xóa chính object mới tạo. Cấu hình R2 không đổi; không dùng dữ liệu/khóa giải mã backup nghiệp vụ. Đã chạy đạt trong lượt20/11.
