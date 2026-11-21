# Phục hồi không ghi đè database cũ

Dùng ứng dụng V44 trở lên. Cất riêng tệp .ndbak và khóa phục hồi; mật khẩu đăng nhập không phải khóa giải mã backup. Máy đích cần Java17, MySQL8 và tài khoản được phép tạo database thử. Không gửi khóa/mật khẩu lên Git hoặc chat.

## Dựng và kiểm tra

1. Admin → Sao lưu → chọn bản local/R2 → Dựng DB thử. Máy mới nhập khóa phục hồi cũ khi cần; nhập tạm tài khoản MySQL có quyền CREATE DATABASE.
2. Chờ kết quả cố định: tên aluminum_restore_*, thư mục ảnh và verified-recovery.json. Hồ sơ chỉ sinh sau kiểm tra số dòng, FK và ảnh; không lưu mật khẩu MySQL.
3. Đối chiếu phiếu, kg từng loại, từng lần thu/chi, công nợ, lương/ứng và bản chốt. Backup mới giữ tác giả inactive, không mang mật khẩu/quyền cũ. Backup cũ thiếu tác giả có thể bị từ chối; tạo bản mới từ nguồn còn chạy được, không NULL hoặc bỏ FK.

## Dùng dữ liệu đã dựng trong ứng dụng desktop

1. Trong khối kết quả dựng thử, bấm **Dùng dữ liệu này**. Ứng dụng sẽ sao lưu và dừng backend do nó khởi động, mở DB đã dựng và kiểm tra tên DB từ `/system/ready`. Nếu backend cổng 8080 được khởi động bên ngoài, nút từ chối chuyển để tránh đóng nhầm tiến trình.
2. Nếu chuyển thành công, giao diện hiện mật khẩu đăng nhập lần đầu của các tài khoản mới. Cất mật khẩu, bấm xác nhận, đăng nhập lại rồi đổi mật khẩu. Tài khoản đăng nhập máy cũ không nằm trong backup.
3. Đối chiếu dữ liệu trong ứng dụng. Backend của DB phục hồi có thư mục ảnh và khóa JWT riêng. Nó dùng lại khóa giải mã và sao chép cấu hình R2 đã nhập trên máy này vào runtime mới; bản sao local tự động tiếp tục chạy. Mở **Khóa phục hồi** để kiểm tra và cất khóa ngoài máy, rồi bấm **Kiểm tra kết nối** và xem bản mới có đồng bộ R2 hay không.

Tauri giữ lựa chọn DB, mật khẩu MySQL và khóa phục hồi trong Windows Credential Manager để lần mở sau tự kết nối. Hồ sơ xác minh không chứa các mã này. DB cũ vẫn tồn tại. Khi backend mới không mở được, desktop thử mở lại backend cũ và báo lỗi. Nếu việc mở lại cũng thất bại, không tự xóa DB nào; xem `backend.log`.

## Quay lại

Vào **Sao lưu → Quay lại database trước**. Ứng dụng sao lưu trạng thái hiện tại, chuyển về DB trước, kiểm tra tên DB rồi yêu cầu đăng nhập lại. Dữ liệu đã ghi trên DB phục hồi không tự nhập gộp vào DB cũ. Không xóa DB nào trước khi đối chiếu.

Launcher `scripts/start-recovered-backend.ps1` vẫn có trong mã nguồn cho kiểm tra kỹ thuật, nhưng luồng desktop không yêu cầu chạy lệnh thủ công.

Restore trực tiếp DB đang chạy tiếp tục khóa. Diễn tập cùng máy không thay thế kiểm thử R2, bộ cài và máy vật lý khác.
