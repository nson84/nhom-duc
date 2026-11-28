# Phiếu nhập, bán và kho

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

Khi xác nhận thanh toán nợ cũ trong tháng đã khóa mà yêu cầu không gửi năm làm việc, xác định kỳ theo ngày thanh toán và kiểm tra phiếu gốc lập trước kỳ đó.

`POST /stock-documents/payments/batch` thu hết số còn nợ của các phiếu bán ngoài đã chọn của một khách trong shop đăng nhập. Kiểm tra số dư sau khi khóa phiếu, lưu cả đợt trong một transaction, và dùng khóa đợt trên từng phiếu để gửi lại an toàn. Ngày nhận tiền phải thuộc tháng mở của kỳ đang làm việc.

`StockDocumentService` quản lý nháp/xác nhận/đảo/thu chi, theo dõi tồn Phân phối theo số lượng và chuyển nội bộ. Phiếu bán xưởng không trừ tồn; có giá nhập tham chiếu riêng, không phải giá vốn hằng ngày.

`GET /stock-balances` cộng cả phiếu nhập Xưởng nội bộ và ngoài vào tồn sổ hiện tại; ngày đầu kỳ không lọc tồn hiện tại. `GET /stock-movements` trả `internal` từ phiếu gốc để màn Tồn kho gắn nhãn trên dòng biến động.

Phiếu nhập/bán đã xác nhận (không phải chuyển nội bộ) được sửa trong 24 giờ tính từ `confirmed_at`. Dịch vụ hoàn tác biến động kho cũ, cập nhật dòng hàng rồi xác nhận lại trong một transaction, giữ nguyên mã phiếu, thời điểm xác nhận gốc và các khoản thanh toán. Tổng mới phải không nhỏ hơn tiền đã thanh toán; kỳ đã chốt bị khóa. Phiếu chuyển nội bộ phải đảo cả cặp trước khi lập lại.

Phiếu Xưởng bị khóa khi ngày phiếu thuộc khoảng ngày của kỳ kiểm kê đã chốt, kể cả khi kỳ chốt đi qua 31/12. Giao dịch sau ngày chốt vẫn ghi được trong cùng năm dương lịch.

Phiếu đã hủy hoặc đã đảo được chuyển vào thùng rác. Giá trị `trashed_at` trống trước lần chuyển đầu tiên là trạng thái hợp lệ; chuyển và khôi phục không lỗi khi đọc giá trị này.

Khi request mang tham số `workingYear`, API chỉ liệt kê/mở phiếu trong kỳ của cửa hàng đó. Tạo, sửa và xác nhận phiếu phải có ngày từ mốc đầu kỳ đến mốc cuối đã lưu; kỳ đã chốt không cho ghi thêm. Kiểm tra server áp dụng cả khi nhập ngày bằng tay.

Tham số `workingMonth` thu hẹp ngày tạo/sửa phiếu vào tháng đang chọn. `shop_month_periods` chỉ cho tạo, sửa, xác nhận, hủy, đảo phiếu thuộc tháng đã mở và chưa khóa; thanh toán nợ cũ vẫn được ghi ở ngày thuộc tháng đang mở.
Ghi thanh toán mới từ chối ngày trước ngày lập phiếu. Sửa phiếu đã xác nhận không cho dời ngày lập qua ngày của bất kỳ khoản thanh toán đã ghi nào.

## Màn hình liên quan

Khi tạo hoặc sửa phiếu bán Xưởng, backend tự ghi giá tham chiếu từ dòng nhập đã xác nhận, chưa đảo, gần nhất của cùng loại tính đến thời điểm phiếu bán.

API tồn kho trả thêm tổng kg nhập và giao từ phiếu đã xác nhận, chưa đảo, để popup kiểm kê tính hao hụt. Ghi điều chỉnh vẫn dùng chênh lệch giữa số cân và tồn sổ.

API tồn kho còn trả tổng tiền phiếu nhập đã xác nhận, chưa đảo phân bổ theo tỷ trọng dòng hàng sau giảm giá, để trang Phân phối hiện tổng tiền đã nhập. Số này là chi phí mua tích lũy, không phải giá trị tồn kho hiện tại hay giá vốn hàng đã bán.

`GET /stock-documents/delivered-by-category` cộng kg theo tên loại từ phiếu bán đã xác nhận, chưa đảo. Có thể lọc ngày phiếu, loại nhôm và danh sách khách; quyền cửa hàng áp dụng như các API kho khác. Phiếu chuyển nội bộ Phân phối → Xưởng vẫn tính vào kg đã giao của Phân phối.

[stock](../../../../../../../../frontend/src/features/stock/FEATURE.md)

Kiểm kê Phân phối nhận ngày kiểm kê từ popup (mặc định theo ngày kết thúc dự kiến hoặc tháng đang chọn). Khối lượng còn trong kho không được lớn hơn `tồn sổ − vụn nhôm` (chỉ điều chỉnh thiếu, không tạo tồn thừa). Backend cho phép kiểm kê trong cả tháng đang mở và tháng đã khóa (nhưng chặn tháng chưa mở, kỳ đã chốt và trường hợp kho đã có biến động sau ngày kiểm kê); các phiếu nhập/bán/sửa/đảo thông thường vẫn bị khóa tháng chặn.

Rà soát 19/11: sửa phiếu nhập Phân phối sau khi đã bán một phần cho phép tổng nhập mới hợp lệ; dựng lại phiếu rồi kiểm tra tồn cuối trong cùng transaction, rollback nếu thiếu hàng. Test MySQL bảo vệ số đã thu, hạn sửa gốc, hai request thu đồng thời và đảo cặp nội bộ. Xem docs/audit-followup-20261119.md.

Audit b290e3c bổ sung ca MySQL: thanh toán đủ rồi retry không nhân đôi dòng tiền/biến động; phiếu hai loại đổi thứ tự, tăng lượng và rollback khi đổi sang loại không đủ tồn; tháng khóa từ chối tạo/sửa/xác nhận/hủy/đảo. Không đổi nghiệp vụ hoặc công thức kho.

20/11: đảo phiếu dùng chung requireWorkingDate cho từng đầu nội bộ, giữ khóa tháng đến commit. Khóa shop rồi các phiếu theo ID tăng dần để hai bên đảo cùng lúc không deadlock. Ngày phiếu đảo/biến động nằm ở ngày gốc trong tháng đang mở, còn ngày xác nhận/hủy/nhật ký là lúc thao tác thật. Test mô phỏng máy 2026 đảo phiếu 2025, chờ chốt tháng và đảo đồng thời từ hai đầu.

Pair ID được đọc hiện hành bằng FOR UPDATE và so với tập phiếu đã khóa. Nếu snapshot cũ thấy nháp chưa ghép cặp nhưng giao dịch khác vừa xác nhận nội bộ, trả lỗi yêu cầu tải lại trước khi ghi, không đảo riêng một đầu.


V45 lưu nguồn Ghi từ sổ trên phiếu và giá vốn nhập tay theo kg ở dòng bán ngoài. Endpoint PUT /api/v1/stock-documents/{id}/payments/{paymentId}/date sửa ngày theo phạm vi sổ, cập nhật thanh toán và thu chi trong một giao dịch, ghi nhật ký ngày cũ/mới và tác giả. Không thay số tiền. Chỉ sửa khoản còn hiệu lực trong kỳ năm chưa chốt.

Chốt sổ tháng Phân phối gọi lại luồng kiểm kê hao hụt để điều chỉnh từng cặp sản phẩm và loại nhôm trước khi lưu bản chốt tháng. Thao tác từ chối khi kho có biến động sau ngày chốt tháng.


Bộ lọc internal-history chỉ truy vấn phiếu nhập nội bộ đã xác nhận, chưa vào thùng rác của Xưởng. Chi tiết internalHistory chỉ đọc đúng loại phiếu và phạm vi sổ.
# Thanh toán nợ chuyển kỳ qua khóa tháng

`StockDocumentService.addPayment` nhận xác nhận riêng cho phiếu gốc trước ngày đầu kỳ đang chọn. Client cũ có thể bỏ trường xác nhận và giữ quy tắc ghi trong tháng mở. Khoản thu hoặc trả vẫn kiểm tra quyền cửa hàng, số còn nợ và ngày thanh toán; nhật ký ghi nhận xác nhận qua khóa tháng.

Danh sách chứng từ thường lấy giao giữa ngày lọc và kỳ đã chọn, kể cả chứng từ điều chỉnh dùng trong báo cáo hao hụt.
