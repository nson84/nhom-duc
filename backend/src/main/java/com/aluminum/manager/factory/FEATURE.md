# Quyết toán xưởng và tổng hợp Admin

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`FactoryService` xử lý kiểm kê, phế liệu, báo cáo khách và công nợ. `FactoryLayerAllocator` phân bổ tồn theo phiếu nhập gần nhất cho từng loại. Bảng V20–V22; lịch sử nhập thiếu kg sẽ báo lỗi, chưa có lớp tồn đầu kỳ theo loại riêng.

Phiếu bán phế liệu và lần thu phế liệu qua `ensureOpen` kiểm tra thêm tháng đã chốt. Quyết toán nhôm vẫn là phép tính của toàn kỳ năm; bảng phiếu bán thành phẩm, phiếu phế liệu và công nợ có thể xem theo tháng đang chọn.

Chốt năm ghi bản số dư `year_end_balances` trong cùng giao dịch với kiểm kê. Giá trị tồn cuối năm chuyển sang đầu năm sau; phải thu/phải trả tiếp tục theo phiếu gốc. Nợ bán phế liệu chưa gắn khách không nằm trong chỉ tiêu phải thu khách của bản chụp.

Kỳ quyết toán có `period_from` và `period_to` do xưởng chọn, không buộc 31/12. Báo cáo nhập, bán, phế liệu, chi phí và công nợ năm dùng khoảng ngày này. Kỳ tiếp theo bắt đầu ngay sau ngày chốt kỳ trước. Phiếu xưởng bị khóa theo ngày thuộc kỳ đã chốt, kể cả khi kỳ kéo qua hai năm dương lịch. Tiền công tháng được xếp vào kỳ chứa ngày cuối tháng; tiền công tháng giao giữa hai kỳ chưa được chia theo ngày.

Ngày bắt đầu và kết thúc dự kiến lưu riêng ở Tổng quan. Kiểm kê Xưởng chỉ lưu sau khi đã lưu ngày kết thúc; báo cáo quyết toán dùng đúng khoảng đã chọn. Ngày trong phần kiểm kê chỉ hiển thị, đổi tại Tổng quan.


## Màn hình liên quan

Chi phí nhân sự trong quyết toán năm tính thưởng lễ đã cấp một lần, gồm cả đã trả trực tiếp và chờ trả cuối tháng.

[factory](../../../../../../../../frontend/src/features/factory/FEATURE.md), [admin](../../../../../../../../frontend/src/features/admin/FEATURE.md)
