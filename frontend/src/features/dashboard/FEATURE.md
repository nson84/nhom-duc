# Chức năng dashboard

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

### `/` — Tổng quan (Dashboard)

| | |
|--|--|
| File | `features/dashboard/DashboardPage.tsx` |
| Ai dùng | Mọi role (số theo shop context) |
| API | `GET /api/v1/dashboard/summary`, `.../charts`, backup status |

**Chức năng**
- Chọn tên năm để xem đúng kỳ của năm đó; ngày bắt đầu/kết thúc hiện cạnh bộ lọc.
- Chọn năm ở Tổng quan áp dụng ngay kỳ làm việc cho cửa hàng hiện tại: danh sách phiếu nhập/bán, Thu chi và Báo cáo chỉ xem khoảng ngày của năm đó. Nút **Khóa năm** giữ bộ chọn ở năm hiện tại; **Mở khóa năm** hỏi xác nhận trước khi cho đổi năm. Năm và tháng dùng chung trong các màn, được lưu trên thiết bị qua lần chuyển trang và tải lại.
- Thẻ **Kỳ kinh doanh** ở đầu Tổng quan cho cả hai sổ hiển thị trạng thái và tên kỳ tùy ý; tên kỳ từ 02/2026 đến 02/2027 có thể là “Kỳ 2026–2027”. Bộ chọn năm vẫn là khóa tra cứu số năm. Kỳ chưa bắt đầu không hiện số liệu theo năm.
- Người dùng chọn ngày bắt đầu rồi bấm **Lưu ngày bắt đầu**. Ô ngày được xóa khi đổi năm để tránh mang ngày của năm khác; không cho chọn ngày tương lai. Từ đó Tổng quan, Thu chi và Báo cáo lọc từ ngày bắt đầu đến ngày hiện tại. Ngày kết thúc được chọn và lưu riêng bằng **Lưu ngày dự kiến**; chỉ sau bước này mới bật **Chốt năm**. Phân phối chốt tại Tổng quan; Xưởng mở đúng năm tại kiểm kê để xác nhận. Có thể sửa ngày dự kiến khi kỳ đang làm việc. Mốc dự kiến không đổi phạm vi lọc và ghi phiếu. Có thể chốt kỳ ngắn hơn một năm mà không cần khóa tháng cuối. Ngày kỳ đã chốt không sửa được. Kỳ sau bắt đầu sát ngày chốt kỳ trước.
- KPI chính: tổng tiền hàng nhập, tiền bán dự kiến, đã thu từ các phiếu bán lập trong kỳ, phần còn phải thu của chính các phiếu đó, doanh nghiệp còn nợ nhà cung cấp từ phiếu nhập trong kỳ, tổng khối lượng đã giao (kg quy đổi thành tấn). Tiền đã thu và công nợ phản ánh mọi lần thanh toán đã ghi cho phiếu trong kỳ, kể cả thanh toán sau kỳ; đây không phải bản chụp công nợ tại ngày chốt. Bản chụp tại ngày chốt ở Sổ cuối năm. Sổ Thu chi vẫn dùng ngày nhận/chi tiền thực tế.
- Ô tổng khối lượng đã giao liệt kê thêm số tấn theo từng loại nhôm trong cùng kỳ; phiếu chuyển nội bộ vẫn nằm trong khối lượng giao Phân phối.
- Tổng quan tự chọn tháng bắt đầu của kỳ năm và có nút **Mở tháng** / **Khóa tháng**. Chọn tháng lấy giao của tháng với khoảng đầu/cuối kỳ, ví dụ kỳ bắt đầu 18/02 thì tháng 02 chỉ tính từ ngày 18. Tháng sau chỉ mở khi tháng trước đã khóa; Xưởng cần chốt kỳ công trước khi khóa. Khóa tháng không tự chốt năm, không đổi tồn hay công nợ.
- Biểu đồ cột theo ngày (lọc ngày có phát sinh / tất cả)
- Biểu đồ dùng hết chiều rộng vùng nội dung ở màn hình lớn và co theo vùng xem ở màn hình nhỏ.
- Dashboard chỉ giữ biểu đồ cột so sánh doanh thu dự kiến và tiền đã thu; bỏ các KPI chi phí/lãi khỏi trang này.
- Cảnh báo nếu lần sao lưu gần nhất thất bại

## Bổ sung theo code hiện tại

Xưởng ẩn giá vốn và lãi ngày, nhưng Tổng quan hiện lãi năm ước tính khi đã có kiểm kê; nhãn cho biết năm chưa chốt. Phân phối không hiện tiền công.

Phân phối xem tổng chi, nợ khách và nợ nhà cung cấp. Sau khi chốt, hiển thị lãi và lượng, giá trị tồn cuối kỳ. Định giá tồn dùng phiếu nhập mới nhất trước.

Phân phối chọn năm và khoảng ngày riêng để chốt, rồi bấm **Chốt năm** để lưu bản số dư: kg và giá trị tồn, nợ khách, nợ nhà cung cấp, lãi. Có thể chọn mốc Tết âm lịch; kỳ kế tiếp bắt đầu ngay ngày sau kỳ trước. Bộ lọc năm xem tự lấy mốc đã chốt, không cần nhập lại ngày.

Hộp thoại chốt năm Phân phối hỏi đã kiểm kê tồn kho chưa và hiển thị khoảng ngày của kỳ. Chọn **Đã kiểm kê, chốt năm** để tiếp tục hoặc **Quay lại** để kiểm tra.

Chốt sổ tháng tùy chọn tại /chot-so-thang, độc lập với Khóa tháng. Kiểm kê nguyên liệu và vụn theo loại, ô trống tính là 0 sau xác nhận. Bản lưu bất biến gồm doanh thu ngoài, giá trị nhập và chi hoạt động/lương theo ngày chi. Đây chưa phải tính lãi quyết toán tháng. Popup ngày cuối tháng theo ngày máy, rộng 60vw, nhắc sau 3 giờ hoặc bỏ qua tháng hiện tại. Trạng thái nhắc lưu trên trình duyệt theo cửa hàng/tháng.
