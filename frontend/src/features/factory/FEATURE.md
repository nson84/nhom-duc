# Chức năng factory

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

Các bảng kiểm kê, phân bổ, công nợ và phế liệu cuộn ngang trong bảng khi cửa sổ nhỏ; modal dài giữ footer thao tác trong viewport.

### `/quyet-toan-xuong` — Quyết toán nhôm cuối năm

Quyền sửa kiểm kê dựa vào ngày bắt đầu kỳ và trạng thái chốt. Hộp xác nhận chốt hiển thị khoảng ngày của kỳ và ngày bắt đầu kỳ kế tiếp.

Bảng đối chiếu dùng tồn đầu kỳ từ bản chốt liền trước, kể cả với bản chốt cũ chỉ có số dư. Cột còn trong kho hiển thị số cân đã nhập.

Màn hình chỉ xem và kiểm kê kỳ đang chọn ở Tổng quan. Tiêu đề hiển thị tên và ngày của kỳ. Liên kết từ Chốt kỳ đưa đến vị trí kiểm kê. Bảng kiểm kê không hiện số liệu kỳ cũ trong lúc chuyển kỳ.

- Xưởng thao tác; Admin không truy cập. Tổng hợp nhôm nhập, kg nhôm thành phẩm đã bán, kg phế liệu đã bán nhiều lần trong năm
- Form bán phế liệu chỉ nhập thời gian, khối lượng, đơn vị và đơn giá theo đơn vị đã chọn. Người bán lấy từ tài khoản đang đăng nhập. Có thể ghi thu một phần hoặc đủ sau khi lưu phiếu.
- Bảng phế liệu chỉ hiện thời gian, người bán, khối lượng, đơn giá và thành tiền. Bấm dòng để xem chi tiết và thao tác.
- Ô kg thành phẩm đã giao trong danh sách phiếu bán hiển thị thêm số kg theo từng loại nhôm trong kỳ và bộ lọc loại đang chọn.
- Mục kiểm kê tách **Tồn đầu kỳ** theo từng loại khỏi **Nhập trong kỳ** và **Giao trong kỳ**. Giới hạn số cân từng loại dựa trên tồn đầu kỳ cộng nhập trừ giao. Với tổng kiểm kê, trừ thêm phế liệu đã bán. Ô hao hụt chờ đến khi đã nhập số cân của các loại.
- Biểu mẫu kiểm kê cuối năm ghi nhôm nguyên liệu và phế liệu chưa bán. Có thể để trống phế liệu, khi lưu sẽ tính là 0. Khi lưu kiểm kê, hàng đang làm và thành phẩm chưa bán được tính là 0. Số đã chốt cuối năm chuyển làm đầu năm sau.
- Khi chốt, lưu bản số dư năm gồm tồn nhôm, nợ khách, nợ nhà cung cấp và lãi; thẻ dưới báo cáo năm hiển thị bản đã chốt.
- Chênh lệch ban đầu = đầu năm + nhập - thành phẩm bán. Hao hụt ước tính = chênh lệch ban đầu - phế liệu bán - nhôm còn cuối năm
- Lãi quản trị ước tính = doanh thu thành phẩm + phế liệu - (giá trị nhôm đầu năm + mua trong năm - giá trị nhôm cuối năm) - tiền công xưởng - chi phí khác đã ghi. Tiền thực thu báo riêng
- Phiếu nhập và phiếu bán trước ngày bắt đầu không cộng vào tổng trong kỳ. Nếu tồn đầu kỳ đã lưu và số cân cuối kỳ chưa khớp với các phát sinh, cần đối chiếu trước khi tính lãi và chốt lại.
- Kỳ đã chốt hiển thị lãi quản trị từ bản chốt. Nếu số cân vượt tồn tính theo sổ, thẻ ghi lượng vượt và nhắc đối chiếu thay cho nhãn hao hụt.
- Công nợ khách theo tháng/năm: nợ đầu kỳ + bán trong kỳ - thu trong kỳ = nợ cuối kỳ; xem các phiếu chi tiết. Tính theo mốc cuối kỳ khi xem; dữ liệu nhập lùi ngày có thể làm thay đổi báo cáo
- Quyết toán công nợ ưu tiên số còn phải thu cuối kỳ và ghi riêng số lúc mở kỳ. Phần nhà cung cấp hiển thị phải trả lúc mở kỳ, nhập mua, đã trả và còn phải trả cuối kỳ theo cùng khoảng ngày.
- Thẻ nợ cũ còn phải thu cuối kỳ cộng số dư của các phiếu bán lập trước ngày đầu kỳ, sau khi đã trừ tiền thu đến hết kỳ. Số lúc mở kỳ vẫn hiện bên dưới để đối chiếu.
- Bộ chọn tháng công nợ liệt kê cả các tháng nằm trong hai năm dương lịch nếu kỳ quyết toán đi qua 31/12; tháng đầu/cuối được cắt đúng ngày đầu/chốt kỳ.
- Năm đã chốt khóa việc sửa phiếu thuộc năm đó; cho phép thu nợ của năm cũ vào kỳ mới
- Có thể chốt năm hiện tại ngay khi đã lưu kiểm kê và đáp ứng điều kiện chốt.
- Chọn ngày đầu kỳ và ngày chốt khi kiểm kê; kỳ có thể đi qua 31/12 và năm nhuận âm. Kỳ sau bắt đầu ngày kế tiếp. Năm trên bộ chọn là tên kỳ, không buộc trùng năm dương lịch của ngày đầu kỳ.
- Nút Mở kiểm kê Xưởng trên trang Chốt kỳ dẫn đến phần kiểm kê của kỳ đang chọn ở Tổng quan. Chốt chỉ hoàn tất sau khi lưu kiểm kê và xác nhận tại trang này.
- Hộp thoại chốt năm Xưởng hỏi đã kiểm kê tồn kho chưa và hiển thị khoảng ngày của kỳ. Chọn **Đã kiểm kê, chốt năm** để tiếp tục hoặc **Quay lại** để kiểm tra.
- Ngày kỳ đã lưu trên trang Chốt năm được nạp vào kiểm kê Xưởng, kể cả khi chưa từng lập kiểm kê; báo cáo Xưởng dùng cùng mốc. Ngày trong kiểm kê chỉ đọc, muốn đổi phải lưu lại ở trang Chốt năm. Cần lưu ngày kết thúc trước khi lưu kiểm kê.

## Bổ sung theo code hiện tại

Bảng **Phiếu bán thành phẩm** hiển thị số lượng, đơn giá bán và thành tiền theo dữ liệu trên phiếu. Đơn giá dùng đúng đơn vị đã chọn trên dòng bán, chẳng hạn đồng mỗi tấn.

Nguyên liệu kiểm kê theo từng loại. Phân bổ kg vào các phiếu nhập xưởng đã xác nhận, chưa đảo, mới nhất trước; thiếu lấy tiếp phiếu cũ. Nếu lịch sử nhập không đủ thì từ chối tính, hiện chưa có màn nhập lớp tồn đầu kỳ theo loại. Phân bổ được lưu và tính lại khi chốt. Phiếu bán theo giá nhập tham chiếu lọc theo loại/giá; dòng cũ chưa gắn có thể gắn khi năm còn mở. Giá trị tham chiếu không được trừ thêm vào lãi năm. Công nợ kỳ được tính theo dữ liệu tại thời điểm xem, không phải bản chụp bất biến.

## Tồn cuối chuyển sang đầu kỳ theo từng loại

GET /api/v1/dashboard/opening-inventory?year=...&month=... trả nguyên liệu đầu kỳ theo từng loại, ngày bắt đầu và ngày chốt nguồn. Bỏ month để xem đầu kỳ năm. Tồn đầu kỳ là bản đọc của lần chốt kết thúc ngay ngày trước đó; không tạo phiếu nhập, không ghi stock movement và không cộng kho lại khi reload. Nếu cùng ngày có bản chốt tháng và năm, lấy bản năm một lần. Bản tháng còn giữ vụn theo loại; vụn năm vẫn theo tổng kiểm kê hiện có, không tự chia vào loại.

V42 thêm year_end_inventory_categories, lưu kg và tên từng loại nguyên liệu trong cùng transaction chốt năm. Bản chốt cũ chỉ dùng chi tiết/historical movements khi tổng khớp; thiếu dữ liệu phải hiện thông báo cần đối chiếu, không tự chia tổng kg hoặc coi thiếu dữ liệu là 0. Không tự chạy migration vào database thật để test.
- Nếu nhập nhầm, dùng **Mở lại kiểm kê** ở kỳ Xưởng đã chốt, sửa số đã lưu rồi chốt lại. Hệ thống từ chối nếu đã có bản chốt phụ thuộc.
