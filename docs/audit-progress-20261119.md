# Rà soát ngày 19/11/2026

## Đã bổ sung trong mã, chưa triển khai lên dữ liệu thật

- Báo cáo không biến giá vốn còn thiếu thành 0 để hiển thị lãi cao sai lệch. Giá vốn thực tế và giá vốn tham khảo được phân biệt; CSV giữ trạng thái thiếu dữ liệu.
- Sổ tháng có tồn đầu, tồn cuối, giá trị vụn và lãi quản trị. Nguyên liệu cuối kỳ lấy các lớp nhập mới nhất, không dùng FIFO. Nếu không tìm được bản chốt liền trước, người dùng nhập giá trị tồn đầu để đối chiếu.
- Phụ cấp, khấu trừ và tiền công tính theo tháng; không tự chia theo ngày khi kỳ năm cắt giữa tháng.
- Ứng lương có ngày, tiền và ghi chú riêng; được ghi chi thực tế và trừ thực lĩnh. Dữ liệu ứng tổng trước đây giữ nguyên, không tự bịa ngày ứng.
- Công nợ kỳ cũ có thao tác thu/trả qua phiếu gốc. Thu tiền tại chi tiết khách hàng có ngày thực nhận riêng.
- Không cho ghi đè bản chốt năm đã lưu. Chốt tháng của Xưởng yêu cầu chốt tiền công tháng.
- Giới hạn dữ liệu giải nén backup; giữ thông báo lỗi đồng bộ nếu có tệp thất bại. Kiểm tra bản cloud có trạng thái đang xử lý.
- Desktop kiểm tra định danh ứng dụng và trạng thái database trước khi dùng backend trên cổng có sẵn.
- CI chuẩn bị MySQL riêng và chạy các bài tích hợp, bao gồm luồng tồn kho/phân phối/xưởng.

## Kiểm thử và giới hạn

Frontend đã build thành công. Backend: 71 unit test đạt, 0 lỗi. Không coi build thành công là đã kiểm thử UI/PDF hay bản cài Windows.

MySQL tích hợp chưa chạy tại máy này: đang chờ người dùng cấu hình `scripts/test-mysql-env.local.ps1` từ file example, sau đó chạy `scripts/test-mysql.ps1`. Script chỉ nhận URL MySQL local có tên database thử `aluminum_backup_test`; không dùng cấu hình database thật. Không ghi mật khẩu vào tài liệu hay Git.

Chưa xác nhận hoàn tất: sửa/đảo phiếu và cạnh tranh giao dịch trên MySQL thật riêng; khôi phục staging/R2 đầu cuối; kiểm tra trực quan UI/PDF; biên dịch và cài đặt Windows sạch. Khôi phục thay thế database đang làm việc vẫn bị vô hiệu hóa.

Không reset, xóa hoặc chạy dữ liệu mẫu vào database đang làm việc. Chưa commit/push các thay đổi này.

