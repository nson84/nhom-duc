# Phục hồi không ghi đè database cũ

Dùng ứng dụng V44 trở lên. Cất riêng tệp .ndbak và khóa phục hồi; mật khẩu đăng nhập không phải khóa giải mã backup. Máy đích cần Java17, MySQL8 và tài khoản được phép tạo database thử. Không gửi khóa/mật khẩu lên Git hoặc chat.

## Dựng và kiểm tra

1. Admin → Sao lưu → chọn bản local/R2 → Dựng DB thử. Máy mới nhập khóa phục hồi cũ khi cần; nhập tạm tài khoản MySQL có quyền CREATE DATABASE.
2. Chờ kết quả cố định: tên aluminum_restore_*, thư mục ảnh và verified-recovery.json. Hồ sơ chỉ sinh sau kiểm tra số dòng, FK và ảnh; không lưu mật khẩu MySQL.
3. Đối chiếu phiếu, kg từng loại, từng lần thu/chi, công nợ, lương/ứng và bản chốt. Backup mới giữ tác giả inactive, không mang mật khẩu/quyền cũ. Backup cũ thiếu tác giả có thể bị từ chối; tạo bản mới từ nguồn còn chạy được, không NULL hoặc bỏ FK.

## Chạy bản phục hồi

Đóng ứng dụng/backend cũ trước khi dùng cổng8080. Launcher từ chối cổng đã bận, không tự giết tiến trình. Từ thư mục dự án:

```powershell
./scripts/start-recovered-backend.ps1 -ProfilePath 'C:/duong-dan/aluminum_restore_xxxxxxxxxxxx/verified-recovery.json'
```

Nhập tài khoản MySQL có quyền trên DB đã phục hồi; ưu tiên tài khoản chỉ có quyền DB đó. Launcher chỉ nhận DB aluminum_restore_* ở127.0.0.1 và ảnh đúng thư mục hồ sơ, từ chối aluminum_app/host Internet. Có thể truyền -JarPath nếu JAR nằm ở nơi khác.

Mở frontend bình thường khi backend ở8080. Diễn tập song song dùng -ApiPort18080 và frontend riêng với VITE_API_BASE_URL=http://127.0.0.1:18080. APPDATA, JWT, bootstrap và cloud config được tách khỏi cài đặt cũ. Mật khẩu mới nằm ở runtime/credentials cạnh hồ sơ, chỉ chủ máy đọc; không dùng lại mật khẩu đăng nhập cũ.

Mặc định tắt lịch backup/R2 trong diễn tập. Sau khi đối chiếu và chọn vận hành bản này, chạy lại launcher với -EnableScheduledBackup, cấu hình R2 và xuất khóa giải mã mới ra nơi riêng. Lịch chạy khi backend đang mở.

## Quay lại

Dừng backend phục hồi bằng Ctrl+C; mở ứng dụng/backend cũ bằng cấu hình cũ. Launcher phục hồi env của shell khi kết thúc, không đổi cấu hình cũ và không drop/rename/restore DB cũ. Không tự nhập gộp phiếu đã ghi trên bản phục hồi về DB cũ; chọn một nơi vận hành chính thức sau đối chiếu.

Restore trực tiếp DB đang chạy tiếp tục khóa. Diễn tập cùng máy không thay thế kiểm thử R2, bộ cài và máy vật lý khác.
