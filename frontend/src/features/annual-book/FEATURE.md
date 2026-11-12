# Sổ cuối năm

Màn hình mặc định chọn năm vừa kết thúc; có thể chọn năm khác trước khi tạo bản xem trước.
Sổ lấy đúng khoảng ngày của kỳ đã chốt hoặc kiểm kê nháp; kỳ tiếp theo bắt đầu sau ngày chốt kỳ trước ở cả hai cửa hàng.

`/so-cuoi-nam` tạo bản xem trước khổ A4 và gọi lệnh in của trình duyệt. Người dùng chọn **Lưu thành PDF** để có một tệp đóng quyển. Admin có thể gộp hai sổ vào một bản in; Xưởng và Phân phối chỉ xem sổ của mình.

Mỗi sổ có trang bìa, tổng hợp từng khách, phiếu bán và dòng hàng, các lần thanh toán, nợ cuối năm theo từng phiếu, sổ thu, sổ chi theo nhóm, phiếu nhập và nợ nhà cung cấp, công thợ theo tháng và tồn kho cuối năm. Giao dịch thu/chi lấy theo ngày giao dịch trong năm; công nợ lấy tất cả phiếu trước cuối năm trừ thanh toán trước mốc đó. Phiếu cũ có thu/trả trong năm cũng được in. Phiếu chưa gắn khách hiện riêng.

Sổ chi nhóm riêng năm lựa chọn khoản chi mới của phiếu thủ công theo tên lưu trong nội dung; phiếu cũ vẫn theo nhóm kế toán hiện có.

Giá trị tồn và lãi lấy từ bản chốt năm nếu có; báo cáo đánh dấu tạm tính khi chưa chốt. Sổ này là bản in quản trị, không thay thế chứng từ kế toán hoặc báo cáo tài chính theo quy định. Dữ liệu có thể thay đổi khi tạo lại bản in sau khi nhập lùi ngày hoặc sửa lịch sử trước khi chốt.

Ngày đầu/kết thúc sổ lấy từ bản chốt của từng cửa hàng, nên hai sổ cùng tên năm có thể có ngày chốt khác nhau. Các số nợ và thanh toán dùng mốc cuối ngày chốt, không mặc định 31/12. Tiền công tháng được ghi vào kỳ chứa ngày cuối tháng.

Code: `AnnualBookPage.tsx`, `annual-book-api.ts`, `annual-book-print.ts`; backend `finance/AnnualBookController.java`, `finance/AnnualBookService.java`.
