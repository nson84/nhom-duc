# Sổ cuối năm

Màn **Sổ cuối kỳ** in kỳ đang chọn ở Tổng quan. Đổi kỳ tại Tổng quan rồi tạo bản xem trước mới.
Sổ lấy đúng khoảng ngày của kỳ đã chốt hoặc kiểm kê nháp; kỳ tiếp theo bắt đầu sau ngày chốt kỳ trước ở cả hai cửa hàng.
Kỳ chưa lưu ngày bắt đầu chưa tạo được Sổ cuối năm; kỳ đang làm việc chưa lưu ngày kết thúc dùng ngày hiện tại cho bản xem trước.
Trang bìa mỗi sổ in tên kỳ tùy ý đã lưu, ví dụ “Kỳ 02/2026–02/2027”.

`/so-cuoi-nam` tạo bản xem trước khổ A4 và gọi lệnh in của trình duyệt. Người dùng chọn **Lưu thành PDF** để có một tệp đóng quyển. Xưởng và Phân phối chỉ xem sổ của mình; Admin không truy cập màn này.

Bản in lấy toàn bộ kỳ làm việc, kể cả khi Tổng quan đang chọn một tháng trong kỳ.

Mỗi sổ có trang bìa, tổng hợp khách, một mục bắt đầu trang mới cho từng khách với đầy đủ phiếu bán, dòng hàng, kg, các lần thanh toán và tổng kg/tiền/đã trả/còn nợ. Trang bìa không in danh sách khách dạng dòng. Sổ thu, sổ chi theo nhóm, phiếu nhập và nợ nhà cung cấp, công thợ Xưởng và tồn kho cuối năm in sau đó. Giao dịch thu/chi lấy theo ngày giao dịch trong năm. Nợ tại ngày chốt giữ nguyên mốc chốt; tình trạng đã trả/còn nợ trên từng phiếu và danh sách nợ hiện tại tính cả lần trả sau kỳ, có ghi chú khi hai mốc khác nhau. Phiếu cũ có thu/trả trong năm cũng được in. Phiếu chưa gắn khách hiện riêng.

Khách được tạo hồ sơ sau kỳ nhưng có phiếu bán được nhập lùi ngày vào kỳ vẫn hiện trong bảng khách và mục chi tiết hóa đơn của kỳ đó.
Chi tiết từng hóa đơn bán in bảng năm cột hàng hóa. Bên dưới là tổng đã trả, danh sách các lần trả theo ngày với nhãn hình thức, rồi số còn nợ. Nếu số tại mốc chốt khác hiện tại thì ghi chú riêng.
Tổng kết từng khách in tổng khối lượng và khối lượng từng loại nhôm theo tấn, tổng tiền các phiếu, tổng đã trả và tổng còn nợ đến lúc in.

Sổ chi nhóm riêng năm lựa chọn khoản chi mới của phiếu thủ công theo tên lưu trong nội dung; phiếu cũ vẫn theo nhóm kế toán hiện có. Bảng thu/chi không in cột Đối tác và Chứng từ.
Sổ thu tách tên người thu khỏi nội dung sang cột Người thu và căn giữa số tiền trên một dòng.
Sổ chi hiển thị cột Người trả và Người thu. Với khoản trả tiền nhập hàng, hai tên lấy từ thông tin đã ghi của giao dịch và được bỏ khỏi Nội dung. Số tiền căn giữa trên một dòng.

Phân phối không in chương Công thợ. Xưởng in tiền công từng tháng, từng người với các khoản lương cơ bản, tăng ca, thưởng, phụ cấp, khấu trừ, ứng, thực lĩnh và chi tiết ngày thưởng công/lễ. Thưởng lễ đã nhận trực tiếp không cộng lại vào lương; thưởng lễ chưa nhận được cộng vào lương và ghi rõ trạng thái. Dữ liệu tiền ứng cũ chỉ lưu tổng tháng, chưa có ngày ứng nên bản in ghi rõ điều này, không tự tạo ngày giả.

Xưởng in thêm phiếu nhập nội bộ từ Phân phối cùng kg và giá chuyển trong chương riêng; các phiếu này không cộng vào nợ nhà cung cấp.
Phiếu nhập nội bộ trước ngày đầu kỳ vẫn hiện để đối chiếu nguồn hàng, có nhãn **Nội bộ · Trước kỳ** và không cộng vào tổng chuyển trong kỳ. Phân phối in chương phiếu xuất nội bộ sang Xưởng, có nhãn **Nội bộ**; giá trị chuyển không cộng doanh thu, tiền thu hay công nợ khách.

Giá trị tồn và lãi lấy từ bản chốt năm nếu có; báo cáo đánh dấu tạm tính khi chưa chốt. Sổ này là bản in quản trị, không thay thế chứng từ kế toán hoặc báo cáo tài chính theo quy định. Dữ liệu có thể thay đổi khi tạo lại bản in sau khi nhập lùi ngày hoặc sửa lịch sử trước khi chốt.

Ngày đầu/kết thúc sổ lấy từ bản chốt của từng cửa hàng, nên hai sổ cùng tên năm có thể có ngày chốt khác nhau. Các số nợ và thanh toán dùng mốc cuối ngày chốt, không mặc định 31/12. Tiền công tháng được ghi vào kỳ chứa ngày cuối tháng.

Code: `AnnualBookPage.tsx`, `annual-book-api.ts`, `annual-book-print.ts`; backend `finance/AnnualBookController.java`, `finance/AnnualBookService.java`.

Phiếu bán trong mục chi tiết từng khách có bảng hàng, bảng tiền phiếu/đã thu/còn nợ; hóa đơn đã thanh toán đánh dấu tích xanh, chưa thanh toán hoặc thanh toán một phần được viền đỏ nổi bật khi in.

20/11: template tự sắp phiếu mới lên trước (ngày giảm dần, cùng ngày theo ID giảm dần), không phụ thuộc thứ tự API. Hình thức CASH/TRANSFER/OTHER in bằng Tiền mặt/Chuyển khoản/Khác. Regression dùng 40 phiếu, khách đủ/một phần/nợ, một phiếu 65 dòng; kiểm tra dữ liệu người dùng được escape và tổng tiền/thu/nợ.


Bản in Thu chi trình bày khoản tổng hợp từ sổ giấy theo tháng/năm. Ngày giao dịch có ngày thực tế hiển thị ngày/tháng/năm.
