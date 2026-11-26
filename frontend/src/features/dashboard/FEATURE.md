# Chức năng dashboard

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

### `/` — Tổng quan (Dashboard)

| | |
|--|--|
| File | `features/dashboard/DashboardPage.tsx` |
| Ai dùng | Mọi role (số theo shop context) |
| API | `GET /api/v1/dashboard/summary`, `.../charts`, backup status |

**Chức năng**
- Sáu thẻ số liệu Tổng quan cùng kích thước và tự giảm số cột khi màn hình hẹp.
- Chọn tên năm để xem đúng kỳ của năm đó; ngày bắt đầu/kết thúc hiện cạnh bộ lọc.
- Chọn năm ở Tổng quan áp dụng ngay kỳ làm việc cho cửa hàng hiện tại: danh sách phiếu nhập/bán, Thu chi và Báo cáo chỉ xem khoảng ngày của năm đó. Nút **Khóa năm** giữ bộ chọn ở năm hiện tại; **Mở khóa năm** hỏi xác nhận trước khi cho đổi năm. Năm và tháng dùng chung trong các màn, được lưu trên thiết bị qua lần chuyển trang và tải lại.
- Thẻ **Kỳ kinh doanh** ở đầu Tổng quan cho cả hai sổ hiển thị trạng thái và tên kỳ tùy ý; tên kỳ từ 02/2026 đến 02/2027 có thể là “Kỳ 2026–2027”. Bộ chọn năm vẫn là khóa tra cứu số năm. Kỳ chưa bắt đầu không hiện số liệu theo năm.
- Người dùng chọn ngày bắt đầu rồi bấm **Lưu ngày bắt đầu**. Ô ngày được xóa khi đổi năm để tránh mang ngày của năm khác; không cho chọn ngày tương lai. Từ đó Tổng quan, Thu chi và Báo cáo lọc từ ngày bắt đầu đến ngày hiện tại. Ngày kết thúc được chọn và lưu riêng bằng **Lưu ngày dự kiến**; chỉ sau bước này mới bật **Chốt năm**. Phân phối chốt tại /chot-nam; Xưởng mở đúng năm tại Quyết toán Xưởng để xác nhận. Có thể sửa ngày dự kiến khi kỳ đang làm việc. Mốc dự kiến không đổi phạm vi lọc và ghi phiếu. Có thể chốt kỳ ngắn hơn một năm mà không cần khóa tháng cuối. Ngày kỳ đã chốt không sửa được. Kỳ sau bắt đầu sát ngày chốt kỳ trước.
- KPI chính: tổng tiền hàng nhập, tiền bán dự kiến, đã thu từ các phiếu bán lập trong kỳ, phần còn phải thu của chính các phiếu đó, doanh nghiệp còn nợ nhà cung cấp từ phiếu nhập trong kỳ, tổng khối lượng đã giao (kg quy đổi thành tấn). Tiền đã thu và công nợ phản ánh mọi lần thanh toán đã ghi cho phiếu trong kỳ, kể cả thanh toán sau kỳ; đây không phải bản chụp công nợ tại ngày chốt. Bản chụp tại ngày chốt ở Sổ cuối năm. Sổ Thu chi vẫn dùng ngày nhận/chi tiền thực tế.
- Ô tổng khối lượng đã giao liệt kê thêm số tấn theo từng loại nhôm trong cùng kỳ; phiếu chuyển nội bộ vẫn nằm trong khối lượng giao Phân phối.
- Tổng quan tự chọn tháng bắt đầu của kỳ năm và có nút **Mở tháng** / **Khóa tháng**. Chọn tháng lấy giao của tháng với khoảng đầu/cuối kỳ, ví dụ kỳ bắt đầu 18/02 thì tháng 02 chỉ tính từ ngày 18. Tháng sau chỉ mở khi tháng trước đã khóa; Xưởng cần chốt kỳ công trước khi khóa. Khóa tháng không tự chốt năm, không đổi tồn hay công nợ.
- Hộp xác nhận **Khóa tháng** hiển thị tên/khoảng ngày của kỳ và nhắc đối chiếu phiếu, thu chi, công nợ; mặc định đặt focus ở **Quay lại**.
- Chọn tháng đã khóa rồi bấm **Mở lại tháng** để chỉnh chứng từ. Có thể mở lại khi tháng sau đang mở. Kỳ đã chốt hoặc có bản chốt sổ từ tháng đó trở đi sẽ bị từ chối.
- Biểu đồ cột theo ngày (lọc ngày có phát sinh / tất cả)
- Biểu đồ dùng hết chiều rộng vùng nội dung ở màn hình lớn và co theo vùng xem ở màn hình nhỏ.
- Dashboard chỉ giữ biểu đồ cột so sánh doanh thu dự kiến và tiền đã thu; bỏ các KPI chi phí/lãi khỏi trang này.
- Cảnh báo nếu lần sao lưu gần nhất thất bại

## Bổ sung theo code hiện tại

Xưởng ẩn giá vốn và lãi ngày, nhưng Tổng quan hiện lãi năm ước tính khi đã có kiểm kê; nhãn cho biết năm chưa chốt. Phân phối không hiện tiền công.

Phân phối xem tổng chi, nợ khách và nợ nhà cung cấp. Sau khi chốt, hiển thị lãi và lượng, giá trị tồn cuối kỳ. Định giá tồn dùng phiếu nhập mới nhất trước.

Phân phối chọn năm và khoảng ngày riêng để chốt, rồi bấm **Chốt năm** để lưu bản số dư: kg và giá trị tồn, nợ khách, nợ nhà cung cấp, lãi. Có thể chọn mốc Tết âm lịch; kỳ kế tiếp bắt đầu ngay ngày sau kỳ trước. Bộ lọc năm xem tự lấy mốc đã chốt, không cần nhập lại ngày.

Hộp thoại chốt năm Phân phối hỏi đã kiểm kê tồn kho chưa và hiển thị khoảng ngày của kỳ. Chọn **Đã kiểm kê, chốt năm** để tiếp tục hoặc **Quay lại** để kiểm tra.

Chốt sổ tháng tùy chọn tại /chot-so-thang độc lập với Khóa tháng. Bản chụp gồm kiểm kê nguyên liệu/vụn, tồn đầu/cuối, doanh thu ngoài, nhập, chuyển nội bộ ra, chi hoạt động và tiền công theo tháng để tính lãi quản trị tháng. Snapshot đã lưu không đổi theo giao dịch mới; muốn ngăn ghi thêm phải Khóa tháng. Chưa có thao tác ghi đè/mở lại snapshot. Popup ngày cuối tháng theo ngày máy, rộng 60vw; trạng thái nhắc lưu theo cửa hàng/tháng.

## Cập nhật rà soát 19/11/2026

Xem `docs/audit-progress-20261119.md` để biết thay đổi và giới hạn kiểm thử. Sổ tháng định giá tồn theo lớp nhập mới nhất, không FIFO; tiền công/phụ cấp/khấu trừ theo tháng. Ứng lương mới có lịch sử ngày, tiền và ghi chú, ghi chi thực tế và trừ thực lĩnh; số ứng tổng cũ không được tự gán ngày. Báo cáo thiếu giá vốn không hiển thị lợi nhuận giả bằng cách coi giá vốn là 0. Các bài MySQL phải dùng cấu hình thử riêng, không thay đổi database thật.

## Tồn cuối chuyển sang đầu kỳ theo từng loại

GET /api/v1/dashboard/opening-inventory?year=...&month=... trả nguyên liệu đầu kỳ theo từng loại, ngày bắt đầu và ngày chốt nguồn. Bỏ month để xem đầu kỳ năm. Tồn đầu kỳ là bản đọc của lần chốt kết thúc ngay ngày trước đó; không tạo phiếu nhập, không ghi stock movement và không cộng kho lại khi reload. Nếu cùng ngày có bản chốt tháng và năm, lấy bản năm một lần. Bản tháng còn giữ vụn theo loại; vụn năm vẫn theo tổng kiểm kê hiện có, không tự chia vào loại.

V42 thêm year_end_inventory_categories, lưu kg và tên từng loại nguyên liệu trong cùng transaction chốt năm. Bản chốt cũ chỉ dùng chi tiết/historical movements khi tổng khớp; thiếu dữ liệu phải hiện thông báo cần đối chiếu, không tự chia tổng kg hoặc coi thiếu dữ liệu là 0. Không tự chạy migration vào database thật để test.

## Chốt tháng chỉ nhập khối lượng

Form chỉ nhập kg nguyên liệu còn tồn; kg vụn là tùy chọn, bỏ trống thì tháng đó tạm ghi0. Không nhập giá trị vụn hoặc tiền tồn đầu tháng. Tồn đầu tháng hiển thị dạng text, server lấy từ bản chốt liền trước và không dùng số tiền do client tự nhập. Thiếu giá trị tồn đầu hoặc có vụn chưa định giá thì vẫn lưu kiểm kê nhưng chưa hiển thị lãi tháng đầy đủ. Các thẻ tiền có border, padding20px và lưới responsive. Kiểm kê năm độc lập, được nhập lại vụn thực tế khi chốt năm; không cộng dồn snapshot tháng vào tồn năm.

Nếu đã nhập kg vụn nhưng chưa có giá trị, endingValue và profit tháng để null; kg vẫn lưu và chuyển theo loại, không biến giá trị vụn chưa biết thành0 ở tháng sau. Đã kiểm tra74 unit test,6 ca MySQL liên quan và build frontend. Không kiểm thử UI trực quan trong lượt này.

Ở Chốt sổ tháng, ô nguyên liệu để trống chưa được coi là số cân 0. Có thể bấm Bỏ qua, về Tổng quan để không tạo bản chốt. Phân phối nhập theo sản phẩm và loại nhôm, xem số chênh lệch rồi cập nhật kho và lưu bản chốt trong một lần. Xưởng lưu bản kiểm kê tháng, không điều chỉnh tồn kho liên tục.


/chot-nam dùng cùng kỳ đang chọn. Lưu ngày kết thúc dự kiến, kiểm kê Phân phối tại Tồn kho và quay lại xác nhận sau khi đến ngày chốt. Xưởng mở kiểm kê đúng kỳ tại Quyết toán Xưởng. Chốt sổ tháng là tùy chọn.


Trang Chốt năm có nút Xóa kỳ với hộp xác nhận hiện ngày bắt đầu. Kỳ đã mở tháng hoặc có dữ liệu bị từ chối.


Ngày đã lưu không thể xóa bằng bộ chọn ngày. Đổi ngày đầu hoặc ngày kết thúc đã lưu có xác nhận ngày cũ và mới. Lưu ngày bắt đầu mới thành công tự chọn tháng chứa ngày đó. Tổng quan báo lỗi khi chọn một tháng chưa mở mà tháng trước chưa khóa.
Chế độ **Theo tháng** xem khoảng tháng làm việc. **Từ đầu kỳ** áp dụng ngày bắt đầu đến hôm nay hoặc ngày chốt cho toàn bộ chỉ tiêu, khối lượng giao theo loại và biểu đồ. Bộ chọn tháng làm việc vẫn dùng cho thao tác mở và khóa tháng. Tổng nhôm nhập quy đổi từ kg sang tấn, gồm phiếu nhập nội bộ đã xác nhận.
