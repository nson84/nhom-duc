# Phạm vi MVP

Một máy Windows, hai sổ Xưởng và Phân phối; các vai trò Admin, Xưởng và Phân phối. Phần mềm quản lý sản phẩm, phiếu nhập/bán, khách mua, nhân sự xưởng, thu chi, báo cáo và sao lưu. Admin xem gộp hai bên; phân phối do chủ quản lý, không có nhân viên hay tiền công.

## Đã khóa

- Đơn vị tồn cơ sở là duy nhất. Mỗi dòng phiếu lưu số lượng bán, đơn vị, hệ số và số lượng cơ sở tại thời điểm giao dịch.
- Hệ số bằng 0 bị từ chối. Không suy đoán hệ số khi chưa có số đã kiểm.
- Phân phối quản lý tồn liên tục và không cho tồn âm. Xưởng quyết toán nhôm theo kiểm kê cuối năm; phiếu bán thành phẩm không trừ tồn từng ngày.
- Phiếu nháp không đổi tồn. Phiếu đã xác nhận không sửa dòng. Sửa sai bằng phiếu đảo, mỗi phiếu chỉ đảo một lần.
- Thùng rác chỉ nhận phiếu **đã hủy** hoặc phiếu **đã xác nhận và đã đảo**. Phiếu nháp phải **hủy nháp** trước; không chuyển thẳng từ nháp vào thùng rác. Phiếu đã hủy trong thùng rác giữ tối đa 90 ngày rồi xóa cứng (nếu không ràng buộc kho/thu); phiếu đã đảo chỉ ẩn danh sách, không xóa cứng.
- Phân phối theo dõi tồn tổng theo số lượng, không tự tính giá vốn khi xuất từng phiếu. Xưởng tính chi phí nhôm năm bằng giá trị đầu năm + nhập trong năm - giá trị kiểm kê cuối năm.
- Phân phối giữ lịch sử giá nhập chuẩn theo ngày và snapshot trên dòng phiếu để tra cứu giá nhập. Giá tham khảo không thay đổi giá thực tế của phiếu nhập. Phiếu bán xưởng có thể gắn dòng phiếu nhập làm giá nhôm tham chiếu; không dùng giá đó làm giá vốn hằng ngày.
- Nếu đơn giá thực tế trên phiếu nhập mới khác giá nhập chuẩn của loại, người dùng được chọn trước khi lưu phiếu có ghi giá chuẩn mới theo ngày phiếu hay chỉ lưu phiếu. Popup nêu giá cũ và ngày bắt đầu, giá mới và ngày hiệu lực. Lịch sử giữ cả mốc cũ khi đổi giá cùng ngày; mốc mới nhất trong ngày được dùng để tính theo ngày. Chấp nhận cập nhật tạo nhắc kiểm tra giá bán từng đơn vị ở chuông header; nhắc lưu tại trình duyệt của shop hiện tại.
- Mỗi phiếu nhập là một đợt; phiếu bán xưởng ghi khối lượng thành phẩm thực giao và khách mua. Báo cáo năm cộng kg theo khách và toàn xưởng; phiếu bán xưởng mới không trừ tồn kho theo ngày.
- Cuối năm cân nguyên liệu theo loại nhôm. Giá trị nguyên liệu được phân bổ vào các dòng nhập đã xác nhận, chưa đảo, theo thứ tự mới nhất trước; thiếu thì lấy tiếp dòng cũ. Nếu lịch sử nhập không đủ kg, hiện hệ thống báo lỗi và chưa có màn nhập lớp tồn đầu kỳ theo loại. Các nhóm phế liệu chưa bán, hàng đang làm, thành phẩm chưa bán lưu kg và giá trị riêng.
- Hao hụt ước tính = kg đầu năm + kg nhập − kg thành phẩm bán − kg phế liệu bán − kg cuối năm. Vụn chưa được ghi bán hoặc kiểm kê có thể nằm trong phần chênh lệch này.
- Lãi quản trị xưởng ước tính = doanh thu thành phẩm + doanh thu phế liệu − (giá trị đầu năm + tiền nhập − giá trị cuối năm) − lương xưởng − chi phí khác. Doanh thu và tiền thực thu được hiển thị riêng; chuyển nội bộ không cộng vào doanh thu ngoài trong báo cáo gộp.
- Phân phối ghi nước, bánh cho đội giao hàng, bảo trì và chi phí nhỏ vào chi phí hoạt động trong Thu chi; không tạo nhân viên hay phiếu chi lương cho phân phối.
- Form phiếu chi thủ công của cả Xưởng và Phân phối dùng năm lựa chọn: điện, ăn mỗi ngày, nước và bánh kẹo giữa giờ, vật tư máy móc, sửa chữa/thay thế/bảo dưỡng. Bốn khoản đầu thuộc chi phí hoạt động, khoản sửa chữa thuộc bảo trì; chi trả phiếu nhập vẫn ghi trên phiếu nhập để tránh trùng.
- Doanh thu, tiền thực thu và chi phí hoạt động là ba số khác nhau. Tiền nhập hàng chưa bán không phải chi phí kỳ.
- Kỳ chốt năm là khoảng ngày do từng cửa hàng chọn, có thể theo Tết âm lịch và đi qua 31/12. Năm trên màn hình là tên kỳ. Kỳ sau bắt đầu ngay sau ngày chốt kỳ trước. Tồn và công nợ chuyển tiếp theo chứng từ gốc; thu/trả nợ cũ sau ngày chốt không tính lãi lần hai. Tiền công tháng thuộc kỳ chứa ngày cuối tháng.
- Tăng ca chỉ tính khi đã duyệt. Kỳ công đã chốt không đổi theo hồ sơ mới.
- Thưởng lễ theo từng nhân viên được tính vào chi phí Xưởng. Khoản đã nhận trực tiếp không cộng lại vào tiền công cuối tháng.
- Giờ qua đêm và làm tròn là cấu hình, không hard-code một công thức cho mọi cửa hàng.

## Chưa làm trong MVP

Nhiều tài khoản đám mây, đồng bộ Internet, công nợ chuyên sâu (sổ cái kế toán), máy chấm công, kết nối API ngân hàng / đối soát sao kê, hóa đơn điện tử, nhiều chi nhánh.

**Đã có trong MVP (nhẹ):** sổ khách mua theo shop, trả nợ một phần trên phiếu bán, in danh sách nợ + mã VietQR từ TK nhận khai báo local ở Cài đặt (không gọi API ngân hàng).

Phân phối không có lương. Lãi kỳ chỉ hiện sau khi chốt. Tồn đầu và cuối kỳ được định giá từ phiếu nhập mới nhất trở về trước.
