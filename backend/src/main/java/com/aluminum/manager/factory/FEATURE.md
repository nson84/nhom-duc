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

## Tồn cuối chuyển sang đầu kỳ theo từng loại

GET /api/v1/dashboard/opening-inventory?year=...&month=... trả nguyên liệu đầu kỳ theo từng loại, ngày bắt đầu và ngày chốt nguồn. Bỏ month để xem đầu kỳ năm. Tồn đầu kỳ là bản đọc của lần chốt kết thúc ngay ngày trước đó; không tạo phiếu nhập, không ghi stock movement và không cộng kho lại khi reload. Nếu cùng ngày có bản chốt tháng và năm, lấy bản năm một lần. Bản tháng còn giữ vụn theo loại; vụn năm vẫn theo tổng kiểm kê hiện có, không tự chia vào loại.

V42 thêm year_end_inventory_categories, lưu kg và tên từng loại nguyên liệu trong cùng transaction chốt năm. Bản chốt cũ chỉ dùng chi tiết/historical movements khi tổng khớp; thiếu dữ liệu phải hiện thông báo cần đối chiếu, không tự chia tổng kg hoặc coi thiếu dữ liệu là 0. Không tự chạy migration vào database thật để test.

Audit b361221: closeYear và saveInventory khi kèm chốt lấy khóa ghi shop trước khi đọc/tính dữ liệu chốt, đồng bộ với guard ghi ngày nghiệp vụ. Không đổi phương pháp định giá, tiền công hoặc quy trình kiểm kê/chốt. Giới hạn nghiệm thu và kiểm thử đồng thời ghi tại docs/audit-next-main.md.

Kiểm tra ngày đầu kỳ xưởng dùng chung gợi ý ngày với Tổng quan: báo ngày chốt kỳ trước và ngày bắt đầu hợp lệ ngay sau đó.
