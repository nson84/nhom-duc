# Phiếu nhập, bán và kho

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`StockDocumentService` quản lý nháp/xác nhận/đảo/thu chi, theo dõi tồn Phân phối theo số lượng và chuyển nội bộ. Phiếu bán xưởng không trừ tồn; có giá nhập tham chiếu riêng, không phải giá vốn hằng ngày.

Phiếu nhập/bán đã xác nhận (không phải chuyển nội bộ) được sửa trong 24 giờ tính từ `confirmed_at`. Dịch vụ hoàn tác biến động kho cũ, cập nhật dòng hàng rồi xác nhận lại trong một transaction, giữ nguyên mã phiếu, thời điểm xác nhận gốc và các khoản thanh toán. Tổng mới phải không nhỏ hơn tiền đã thanh toán; kỳ đã chốt bị khóa. Phiếu chuyển nội bộ phải đảo cả cặp trước khi lập lại.

Phiếu Xưởng bị khóa khi ngày phiếu thuộc khoảng ngày của kỳ kiểm kê đã chốt, kể cả khi kỳ chốt đi qua 31/12. Giao dịch sau ngày chốt vẫn ghi được trong cùng năm dương lịch.

Phiếu đã hủy hoặc đã đảo được chuyển vào thùng rác. Giá trị `trashed_at` trống trước lần chuyển đầu tiên là trạng thái hợp lệ; chuyển và khôi phục không lỗi khi đọc giá trị này.

Khi request mang tham số `workingYear`, API chỉ liệt kê/mở phiếu trong kỳ của cửa hàng đó. Tạo, sửa và xác nhận phiếu phải có ngày từ mốc đầu kỳ đến mốc cuối đã lưu; kỳ đã chốt không cho ghi thêm. Kiểm tra server áp dụng cả khi nhập ngày bằng tay.

Tham số `workingMonth` thu hẹp ngày tạo/sửa phiếu vào tháng đang chọn. `shop_month_periods` chỉ cho tạo, sửa, xác nhận, hủy, đảo phiếu thuộc tháng đã mở và chưa khóa; thanh toán nợ cũ vẫn được ghi ở ngày thuộc tháng đang mở.

## Màn hình liên quan

Khi tạo hoặc sửa phiếu bán Xưởng, backend tự ghi giá tham chiếu từ dòng nhập đã xác nhận, chưa đảo, gần nhất của cùng loại tính đến thời điểm phiếu bán.

API tồn kho trả thêm tổng kg nhập và giao từ phiếu đã xác nhận, chưa đảo, để popup kiểm kê tính hao hụt. Ghi điều chỉnh vẫn dùng chênh lệch giữa số cân và tồn sổ.

API tồn kho còn trả tổng tiền phiếu nhập đã xác nhận, chưa đảo phân bổ theo tỷ trọng dòng hàng sau giảm giá, để trang Phân phối hiện tổng tiền đã nhập. Số này là chi phí mua tích lũy, không phải giá trị tồn kho hiện tại hay giá vốn hàng đã bán.

`GET /stock-documents/delivered-by-category` cộng kg theo tên loại từ phiếu bán đã xác nhận, chưa đảo. Có thể lọc ngày phiếu, loại nhôm và danh sách khách; quyền cửa hàng áp dụng như các API kho khác. Phiếu chuyển nội bộ Phân phối → Xưởng vẫn tính vào kg đã giao của Phân phối.

[stock](../../../../../../../../frontend/src/features/stock/FEATURE.md)

Kiểm kê Phân phối gửi ngày nghiệp vụ theo tháng đang chọn. Tháng quá khứ dùng cuối ngày cuối khoảng đang xem, tháng hiện tại dùng thời điểm hiện tại. Backend kiểm tra tháng mở trước khi tạo điều chỉnh kho.

Rà soát 19/11: sửa phiếu nhập Phân phối sau khi đã bán một phần cho phép tổng nhập mới hợp lệ; dựng lại phiếu rồi kiểm tra tồn cuối trong cùng transaction, rollback nếu thiếu hàng. Test MySQL bảo vệ số đã thu, hạn sửa gốc, hai request thu đồng thời và đảo cặp nội bộ. Xem docs/audit-followup-20261119.md.
