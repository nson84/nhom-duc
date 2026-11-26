# Sao lưu và khôi phục khi mất máy

Ứng dụng dùng trên một máy. Muốn phục hồi sau khi mất máy cần **cả hai**: tệp `.ndbak` trên Cloudflare R2 và khóa phục hồi đã cất ngoài máy. Mật khẩu đăng nhập, mật khẩu MySQL và khóa R2 không thay thế được khóa phục hồi.

## Chuẩn bị khi máy cũ còn hoạt động

1. Đăng nhập Admin → **Sao lưu** → **Khóa phục hồi**. Cất chuỗi khóa ngoài máy và kiểm tra có thể đọc lại. Không lưu cùng thư mục backup hoặc trong ảnh chụp màn hình công khai.
2. Cấu hình Cloudflare R2 bằng S3 API endpoint, bucket, Access Key ID và Secret Access Key. Tạo token **Object Read & Write** giới hạn đúng bucket. Bấm **Kiểm tra kết nối**.
3. Bấm **Sao lưu ngay**, **Đồng bộ R2 ngay**, kiểm tra **Bản chờ đồng bộ = 0** và bản mới xuất hiện trong danh sách R2. Bấm **Kiểm tra** cho bản đó; nếu có thể, bấm **Dựng DB thử** và đối chiếu dữ liệu trên database riêng.
4. Hoàn thành và lưu các phiếu nháp còn nằm trên thiết bị. Bản nháp chỉ trong trình duyệt không có trong `.ndbak`.

Khi mở ứng dụng desktop và backend khởi động, ứng dụng tạo một bản sao lưu sau khoảng 30 giây nếu bản gần nhất đã cách ít nhất 5 phút. Sau đó lịch mặc định chạy mỗi 4 giờ **khi backend đang chạy**. Khi đóng desktop bình thường, ứng dụng tạo thêm một bản `shutdown` rồi thử đồng bộ R2 trước khi thoát; nếu vượt 90 giây, desktop buộc dừng backend và cần kiểm tra nhật ký. Khi mất mạng, backend thử đồng bộ lại mỗi 5 phút trong lúc chạy. Mở lại ứng dụng trong vòng 5 phút không tạo bản khởi động trùng, nhưng đóng bình thường vẫn tạo bản cuối. Kiểm tra định kỳ bản mới nhất trên R2; số bản chờ bằng 0 riêng nó không chứng minh bản mới nhất đã phục hồi được.

## Nếu mất máy: lấy bản sao từ R2

1. Trên máy mới, cài Java 17, MySQL 8 và **bộ cài ứng dụng phiên bản bằng hoặc mới hơn bản đã sao lưu**. Cần quyền tạo database riêng để dựng thử. Mở ứng dụng và đăng nhập Admin mới để chuẩn bị phục hồi. Sau khi chuyển sang backup tạo bằng phiên bản mới, dùng mật khẩu đăng nhập cũ; backup cũ có thể cần mật khẩu lần đầu.
2. Đăng nhập tài khoản Cloudflare của bạn → **R2 object storage** → chọn bucket → thư mục `backups/`. Chọn bản `.ndbak` gần nhất cần khôi phục. Cloudflare cho tải object qua menu **… → Download** để giữ thêm một bản ngoài máy; tệp tải xuống vẫn mã hóa. Ứng dụng hiện chưa nhận trực tiếp tệp `.ndbak` tải thủ công, nên các bước tiếp theo dùng kết nối R2 trong màn Sao lưu.
3. Nếu mất Access Key ID/Secret Access Key của máy cũ, tạo **R2 API token mới** có quyền đọc bucket đó; lưu secret khi Cloudflare hiển thị vì sau đó không xem lại được. Trong ứng dụng: **Sao lưu → Cấu hình Cloudflare R2**, nhập endpoint dạng `https://<account-id>.r2.cloudflarestorage.com`, tên bucket và cặp khóa mới. Bấm **Kiểm tra kết nối**.
4. Danh sách **Bản trên R2** phải hiện các object `backups/*.ndbak`. Chọn bản cần dùng → **Dựng DB thử**. Nhập khóa phục hồi cũ đã cất riêng (có thể nhập ở **Nhập khóa máy cũ** trước) và tài khoản MySQL có quyền `CREATE DATABASE`. Ứng dụng tải object về `%APPDATA%\AluminumManager\cloud-downloads`, đối chiếu SHA-256, giải mã, chạy Flyway, nhập dữ liệu và kiểm tra ảnh/khóa ngoại/số dòng trên database `aluminum_restore_*` riêng. Không thay database đang chạy.
5. Ghi lại `verified-recovery.json` được hiển thị. Đối chiếu cả hai sổ: phiếu nhập/bán, từng lần thanh toán, công nợ, tồn từng loại, nhân sự/lương, bản chốt và ảnh. Chỉ khi đối chiếu đạt mới chuyển sang [chạy bản phục hồi](recovery-runbook.md).

Cloudflare xác nhận có thể tải object từ R2 Dashboard hoặc S3 API: [Download objects](https://developers.cloudflare.com/r2/objects/download-objects/). Cách tạo token và endpoint: [R2 CLI guide](https://developers.cloudflare.com/r2/get-started/cli/).

## Giới hạn

- Trong ứng dụng desktop mới, sau khi dựng và đối chiếu DB thử, bấm **Dùng dữ liệu này**. Desktop tự chuyển backend, kiểm tra đúng DB rồi cho đăng nhập lại; nút **Quay lại database trước** nằm trên trang Sao lưu. `start-recovered-backend.ps1` vẫn là công cụ kỹ thuật cũ, không cần chạy trong luồng thông thường.
- Restore trực tiếp vào DB đang chạy vẫn khóa. Không xóa DB cũ hoặc object R2 cuối cùng.
- Nếu mất cả khóa phục hồi, không thể giải mã bản `.ndbak`. Nếu chưa từng đồng bộ thành công lên R2, dữ liệu chỉ trên máy cũ không thể lấy từ R2.
- Kiểm thử tự động trên một máy không thay thế diễn tập trên một máy Windows sạch. Chưa có bằng chứng cho một lần khôi phục sau khi mất máy vật lý thật.
