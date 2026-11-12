# Phiếu nhập, bán và kho

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`StockDocumentService` quản lý nháp/xác nhận/đảo/thu chi, theo dõi tồn Phân phối theo số lượng và chuyển nội bộ. Phiếu bán xưởng không trừ tồn; có giá nhập tham chiếu riêng, không phải giá vốn hằng ngày.

Phiếu Xưởng bị khóa khi ngày phiếu thuộc khoảng ngày của kỳ kiểm kê đã chốt, kể cả khi kỳ chốt đi qua 31/12. Giao dịch sau ngày chốt vẫn ghi được trong cùng năm dương lịch.

Phiếu đã hủy hoặc đã đảo được chuyển vào thùng rác. Giá trị `trashed_at` trống trước lần chuyển đầu tiên là trạng thái hợp lệ; chuyển và khôi phục không lỗi khi đọc giá trị này.

## Màn hình liên quan

Khi tạo hoặc sửa phiếu bán Xưởng, backend tự ghi giá tham chiếu từ dòng nhập đã xác nhận, chưa đảo, gần nhất của cùng loại tính đến thời điểm phiếu bán.

API tồn kho trả thêm tổng kg nhập và giao từ phiếu đã xác nhận, chưa đảo, để popup kiểm kê tính hao hụt. Ghi điều chỉnh vẫn dùng chênh lệch giữa số cân và tồn sổ.

[stock](../../../../../../../../frontend/src/features/stock/FEATURE.md)
