# Nhân sự, điểm danh, tăng ca và lương của xưởng

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`AttendanceController`, `AttendanceService`, `AttendanceCalculator`; API employees/attendance/overtime/payroll. Mỗi buổi sáng hoặc chiều tính 0,5 công. Điểm danh mở từ 00:01 đến hết 23:59 theo giờ cửa hàng. Tăng ca nhập trên phiếu được ghi riêng theo giờ và được duyệt khi lưu. Không dùng nhân sự cho phân phối.

Mở trang Tiền công có thể gọi tạo/lấy cùng kỳ nhiều lần. Lệnh tạo kỳ dùng khóa duy nhất `(period_year, period_month)` theo kiểu chèn hoặc lấy bản ghi đã có, nên các yêu cầu đồng thời không báo lỗi ràng buộc trùng kỳ.

## Màn hình liên quan

Thưởng lễ được lưu riêng theo ngày và nhân viên. Tiền công tháng chỉ cộng thưởng chưa nhận. Ngày lễ trong tháng đã chốt chỉ sửa được sau khi mở lại kỳ.

Lần ghi tăng ca mới dùng một đơn giá theo giờ cho mọi ngày, lấy từ trường đơn giá ngày thường trước đây. Bản ghi đã có giữ đơn giá đã lưu.

Xóa mức công chỉ áp dụng cho mốc tương lai chưa có bản ghi chấm công tham chiếu. Sau khi xóa, nối lại khoảng áp dụng liền trước nếu khoảng đó kết thúc ngay trước mốc bị xóa.

Mỗi nhân viên đã nhận có một phiếu chi gắn với ngày lễ. Bỏ tích sẽ hủy phiếu chi liên kết.

Kỳ tiền công trừ tiền ứng vào thực lĩnh và lưu số giờ tăng ca đã duyệt cùng thưởng lễ đã nhận để xem bảng kê sau khi chốt. Các khoản khấu trừ cũ được lưu riêng.

[attendance](../../../../../../../../frontend/src/features/attendance/FEATURE.md)
