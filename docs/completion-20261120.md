# Hoàn thiện từ đánh giá ngày 20/11/2026

Phạm vi: sửa các điểm chặn đã xác định trên main b361221 và các sửa cục bộ có sẵn. Giữ nghiệp vụ Xưởng/Phân phối, không FIFO, không sửa số lương lịch sử. Không reset/xóa/seed/restore database thật. Mọi MySQL integration dùng server riêng127.0.0.1:13307, datadir .runtime/completion-mysql, DB aluminum_test_*/aluminum_restore_*.

## Đã sửa

- Backup V44: giữ ID/tên tác giả và audit_logs, không mang hash/quyền/phiên cũ. Tác giả lưu dạng inactive với username riêng và CHECK ngăn kích hoạt. Các FK chốt/mở lương, khóa công, lead, audit được giữ; máy mới có account trùngID không bị thay đổi.
- Staging: chỉ nhận SQL literal do writer sinh, chặn đổi catalog/DDL/hàm/nhiều lệnh trước khi tạoDB. Kiểm tra row count/FK/ảnh; xuất verified-recovery.json chỉ chủ máy đọc, không chứa credentials MySQL. Xử lý đúng chuỗi escape khi session có NO_BACKSLASH_ESCAPES.
- Recovery: launcher chỉ nhận DB đã xác minh, giữ DB cũ, APPDATA/ảnh/credentials tách riêng; diễn tập mặc định tắt lịch local/R2. Menu backup giữ kết quả DB/ảnh/hồ sơ thay vì chỉ toast. [Runbook](recovery-runbook.md).
- Đảo phiếu: dùng guard tháng/năm chung; shop/phiếu được khóa theo thứ tự ổn định cho hai đầu nội bộ. Ngày nghiệp vụ phiếu và biến động dùng ngày gốc; thời điểm thao tác giữ ở audit/xác nhận/hủy. Không chỉnh kỳ đã khóa.
- Build: Maven/npm/Tauri lỗi phải dừng, không dùng JAR cũ; thiếu Cargo là lỗi đóng gói. Backend build-info và menu tài khoản hiển thị phiên bản frontend/backend/schema, phân biệt unversioned/dirty.
- PDF: phiếu mới lên trước, nhãn hình thức thanh toán tiếng Việt, giữ chi tiết/tổng/khách hết nợ và escape dữ liệu nhập. Có hồi quy40 hóa đơn và hóa đơn65 dòng.
- CI: chạy hồi quy frontend và script Windows; workflow thủ công tạo NSIS sau khi Verify đạt, không tự phát hành.
- Tài liệu: sửa quy tắc cũ mâu thuẫn “phiếu xác nhận không được sửa” thành sửa phiếu thường trong24giờ, giữ điều kiện thanh toán/khóa kỳ; ghi rõ các giới hạn cũ được thay thế bằng bản sửa nào.

## Bằng chứng cục bộ

- .runtime/completion-backup-red.log: phục hồi có tác giả thất bại FK trước sửa.
- .runtime/completion-actors-and-reverse.log: actor phục hồi đạt; đảo chưa dùng shared guard được tái hiện.
- .runtime/completion-reverse-opposite-red.log: deadlock khi đảo đồng thời từ hai đầu.
- .runtime/completion-final-backend.log: toàn bộ120 ca đạt,0 lỗi/bỏ qua; migration V1–V44 và V42→V43.
- .runtime/completion-final-backup-hardening.log: chạy lại unit và các suite backup sau SQL policy; đối chiếu kết quả cuối ở log.
- Lượt hardening cuối đạt99 ca; lượt stock cuối đạt100 ca (toàn bộ94 unit và6 ca đảo/khóa liên quan). Các lượt có trùng ca, không cộng thành một lượt chạy duy nhất. Snapshot cũ của nháp vừa xác nhận nội bộ được tái hiện ở completion-stale-pair-red.log; sau sửa đọc pair hiện hành và yêu cầu tải lại, không đảo một đầu.
- .runtime/completion-recovery-e2e.json: JAR thật khởi động từ hồ sơ phục hồi; Admin chỉ truy cập backup và nhận403 với sổ nghiệp vụ; Phân phối100triệu đã trả100triệu, Xưởng100triệu đã trả50triệu/còn50triệu và có nhập nội bộ. Actor lịch sử nhận401. Tiến trình diễn tập được dừng, cổng18080/13307 trống.
- .runtime/completion-r2-live.log: phép thử R2 thật đạt1 ca,0 lỗi/bỏ qua. Dùng cấu hình DPAPI đã có để PUT/HEAD/GET một fixture mã hóa nhỏ trong verification/UUID, so byte, giải mã và dựng DB MySQL riêng rồi đối chiếu tiếng Việt. Chỉ xóa object mới tạo và xác minh config file không đổi; không tải dữ liệu nghiệp vụ hoặc sửa backup hiện hữu.
- .runtime/completion-frontend-tests.log và completion-frontend-build.log: hồi quy frontend và build.
- .runtime/completion-npm-audit.json: npm audit --omit=dev không báo vulnerability tại thời điểm chạy.
- .runtime/completion-pdf-check.log: PDF Phân phối30 trang, Xưởng32 trang, đủ40 phiếu/sổ, đúng tổng4triệu/đã trả3triệu/nợ1triệu; không trang trắng/chữ ngoài trang. PNG đã xem ở completion-pdf/final-*.

## Giới hạn nghiệm thu

Chưa chứng nhận NSIS/máy Windows sạch, vì máy này thiếu Rust/C++ toolchain. Workflow mới chưa chạy trên GitHub. R2 transport/phục hồi đã thử thật với fixture; chưa nghiệm thu chạy lịch dài hạn hoặc máy vật lý khác. Chưa áp dụng V43/V44 vào database nghiệp vụ; trước nâng cấp phải sao lưu và chạy v43-preflight.sql trên bản sao. Backup cũ thiếu tác giả vẫn có thể không phục hồi được, không tự gán tác giả giả. Dump vẫn có giới hạn32Mi ký tự và dùng RAM; không phải thiết kế cho database rất lớn. Bundle frontend khoảng2,41MB minified còn cảnh báo kích thước. Restore trực tiếp ghi đè DB hoạt động vẫn khóa.

Không dùng số ca test để tuyên bố toàn bộ sản phẩm đã được nghiệm thu trên mọi môi trường. Chỉ phát hành chính thức sau khi bộ cài và diễn tập phục hồi trên máy đích đạt.
