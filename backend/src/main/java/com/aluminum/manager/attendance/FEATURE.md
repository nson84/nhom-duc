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

Các lệnh sửa chấm công dùng kiểm tra ngày thuộc kỳ năm và tháng đã mở, chưa khóa. Kỳ công chỉ tạo/tính/sửa/chốt trong tháng đang mở; phải chốt kỳ công trước khi khóa tháng ở Tổng quan. Khi tháng đã khóa không cho mở lại kỳ công.

[attendance](../../../../../../../../frontend/src/features/attendance/FEATURE.md)

## Cập nhật rà soát 19/11/2026

Xem `docs/audit-progress-20261119.md` để biết thay đổi và giới hạn kiểm thử. Sổ tháng định giá tồn theo lớp nhập mới nhất, không FIFO; tiền công/phụ cấp/khấu trừ theo tháng. Ứng lương mới có lịch sử ngày, tiền và ghi chú, ghi chi thực tế và trừ thực lĩnh; số ứng tổng cũ không được tự gán ngày. Báo cáo thiếu giá vốn không hiển thị lợi nhuận giả bằng cách coi giá vốn là 0. Các bài MySQL phải dùng cấu hình thử riêng, không thay đổi database thật.

Audit b290e3c bổ sung ca lifecycle Xưởng trong StockLifecycleMysqlIT: chỉ cộng tăng ca đã duyệt, ứng có ngày ghi một khoản chi dù retry, từ chối ứng vượt thực lĩnh, tính lại lúc chốt giữ tiền ứng và khóa thay đổi sau chốt. Không đổi cách tính công theo tháng.

V43 đồng bộ CHECK chk_payroll_total với công thức thực lĩnh hiện có (trừ advance_amount và chặn dưới tại0). Không cập nhật số tiền lịch sử. Nếu dữ liệu cũ không khớp, ALTER thất bại thay vì sửa số tiền để vượt kiểm tra; cần đối chiếu riêng trước nâng cấp database thật.

PayrollMigrationMysqlIT kiểm thử6 fixture nâng V42→V43, gồm kỳ mở/chốt/đã trả và dữ liệu có ứng không khớp. Dòng lệch bị từ chối, CHECK cũ và các số tiền giữ nguyên; không sửa migration đã có. scripts/v43-preflight.sql chỉ SELECT số liệu đối chiếu trên bản sao; không tự UPDATE/repair lịch sử.
