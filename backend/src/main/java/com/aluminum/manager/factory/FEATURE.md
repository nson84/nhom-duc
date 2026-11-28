# Quyết toán xưởng và tổng hợp Admin

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

Mã kỳ dùng để tra cứu kiểm kê Xưởng, kể cả khi nhiều kỳ bắt đầu trong cùng một năm dương lịch.

Tồn đầu kỳ tổng và theo loại ưu tiên bản chốt chính thức kết thúc ngay trước ngày đầu kỳ, kể cả khi kỳ cũ chỉ có bản chốt số dư mà thiếu hồ sơ kiểm kê Xưởng. Số cân đã lưu vẫn là số người dùng kiểm kê.

`FactoryService` xử lý kiểm kê, phế liệu, báo cáo khách và công nợ. Bảng kiểm kê lọc nhập và giao theo đúng kỳ, và đọc tồn đầu kỳ theo loại từ số kiểm kê liền trước đã chốt. Giới hạn từng loại là `đầu kỳ + nhập − giao`, giới hạn tổng trừ thêm phế liệu đã bán. `FactoryLayerAllocator` vẫn định giá theo phiếu nhập gần nhất, có thể dùng phiếu trước kỳ để định giá số cân còn lại.

Phiếu bán phế liệu và lần thu phế liệu qua `ensureOpen` kiểm tra thêm tháng đã chốt. Quyết toán nhôm vẫn là phép tính của toàn kỳ năm; bảng phiếu bán thành phẩm, phiếu phế liệu và công nợ có thể xem theo tháng đang chọn.

Báo cáo năm lọc phiếu nhập và phiếu bán thành phẩm đúng ngày đầu và cuối kỳ, kể cả kỳ đầu tiên chưa có kiểm kê liền trước. Khi tồn kiểm kê vượt lượng có thể còn, báo cáo trả chênh lệch âm để đối chiếu và chưa tính chi phí nhôm hay lãi tạm tính. Nếu có bản chốt, `estimatedProfit` trả lãi đã lưu trong bản chốt.

Báo cáo `settlement` tổng hợp phải trả nhà cung cấp từ phiếu nhập ngoài đã xác nhận và các giao dịch trả tiền tương ứng, theo cùng khoảng ngày với phải thu khách.

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
Khi mở lại bản chốt năm Xưởng qua dashboard, kiểm kê trở về bản nháp, giữ các số lượng đã nhập để sửa và chốt lại.
