# Sao lưu và khôi phục khi mất máy

1. Trên máy đang dùng, đăng nhập Admin → **Sao lưu** → **Khóa phục hồi**. Cất chuỗi khóa ở nơi riêng ngoài máy. Nếu mất khóa này, không thể giải mã archive trên R2.
2. Trong Cloudflare R2, tạo token Object Read & Write giới hạn đúng bucket. Nhập S3 API endpoint, bucket, Access Key ID và Secret Access Key vào ứng dụng. Bấm **Kiểm tra kết nối**. Token đã xuất hiện trong ảnh/chat cần được thu hồi và tạo lại.
3. Bấm **Sao lưu ngay**, sau đó **Đồng bộ R2 ngay**. Xem số bản chờ bằng 0, bản cloud xuất hiện trong danh sách, bấm **Kiểm tra**. Lịch mặc định mỗi 4 giờ khi backend đang chạy; khi mất mạng, ứng dụng thử đồng bộ lại mỗi 5 phút.
4. Khi cài máy mới, kết nối MySQL và khởi động ứng dụng, đăng nhập Admin. Nhập lại khóa tại **Nhập khóa máy cũ**, cấu hình R2 bằng token hợp lệ và bấm **Kiểm tra kết nối**.
5. Chọn bản trên R2 → **Dựng DB thử**. Nhập tạm tài khoản MySQL có quyền `CREATE DATABASE` và mật khẩu của tài khoản đó; ứng dụng không lưu mật khẩu này. Ứng dụng tải archive, xác minh SHA-256, giải mã, kiểm tra ảnh, chạy Flyway và nhập SQL vào database mới tên `aluminum_restore_*`. Ảnh nằm tại thư mục được báo sau khi dựng. Đối chiếu dữ liệu Phân phối/Xưởng trước khi dùng.

Ứng dụng chưa tự đổi database hoạt động sang database thử; thao tác restore trực tiếp vẫn trả 503. Không xóa database cũ hoặc backup cuối cùng. Việc chuyển database và rollback cần được hoàn thiện, thử trên máy Windows khác trước khi xem là khôi phục hoàn chỉnh.

Bản đóng gói hiện đưa backend JAR vào tài nguyên Tauri nhưng vẫn cần Java 17 và MySQL 8 được cài trên máy. Chưa xác minh bộ cài NSIS vì môi trường phát triển hiện không có Cargo.

## Kiểm tra staging trước khi dùng

Staging kiểm tra số dòng, nội dung ảnh và khóa ngoại sau nhập. Nếu thiếu bản ghi cha, thao tác dựng phải báo lỗi; không bỏ ràng buộc để cho qua. Bộ tạo backup hiện giới hạn 32 Mi ký tự SQL để chặn tăng RAM không kiểm soát; dữ liệu lớn hơn cần phương án dump chuyên dụng. Xem audit-followup-20261119.md cho diễn tập chuyển/rollback còn chưa hỗ trợ.

## Giới hạn xác nhận tại b290e3c

Recovery end-to-end vẫn **NOT_VERIFIED**. Archive có kỳ công tham chiếu người chốt có thể bị từ chối vì tài khoản local không nằm trong backup. Không bỏ kiểm tra FK hoặc tự gán lại người chốt. Test mới đối chiếu hai sổ, dòng hàng, biến động kho, thanh toán, nhân sự, tiền công và bytes ảnh; giữ created_at/updated_at lịch sử, từ chối ảnh bị thiếu. Xem [báo cáo audit và quy trình rollback trên máy thử](audit-b290-20261119.md). Không coi nút “Kiểm tra” giải mã thành công là đã chứng minh khôi phục hoạt động trên máy mới.
