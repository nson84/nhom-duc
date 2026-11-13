# Chức năng factory

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

Các bảng kiểm kê, phân bổ, công nợ và phế liệu cuộn ngang trong bảng khi cửa sổ nhỏ; modal dài giữ footer thao tác trong viewport.

### `/quyet-toan-xuong` — Quyết toán nhôm cuối năm

Màn hình mặc định mở năm vừa kết thúc để xem lại lãi và kiểm kê; có thể chọn năm khác.

- Xưởng thao tác; Admin có thể xem khi chọn sổ xưởng. Tổng hợp nhôm nhập, kg nhôm thành phẩm đã bán, kg phế liệu đã bán nhiều lần trong năm
- Form bán phế liệu chỉ nhập thời gian, khối lượng, đơn vị và đơn giá theo đơn vị đã chọn. Người bán lấy từ tài khoản đang đăng nhập. Có thể ghi thu một phần hoặc đủ sau khi lưu phiếu.
- Bảng phế liệu chỉ hiện thời gian, người bán, khối lượng, đơn giá và thành tiền. Bấm dòng để xem chi tiết và thao tác.
- Mục **Nhập số kg còn trong kho cuối năm** tự liệt kê các loại nhôm và có nút **Lưu tồn nguyên liệu**. Dòng không còn hàng dùng 0. Tồn đầu năm lấy từ số chốt cuối năm trước, mặc định 0 nếu chưa có số dư đã lưu.
- Biểu mẫu kiểm kê cuối năm ghi nhôm nguyên liệu và phế liệu chưa bán. Có thể để trống phế liệu, khi lưu sẽ tính là 0. Khi lưu kiểm kê, hàng đang làm và thành phẩm chưa bán được tính là 0. Số đã chốt cuối năm chuyển làm đầu năm sau.
- Khi chốt, lưu bản số dư năm gồm tồn nhôm, nợ khách, nợ nhà cung cấp và lãi; thẻ dưới báo cáo năm hiển thị bản đã chốt.
- Chênh lệch ban đầu = đầu năm + nhập - thành phẩm bán. Hao hụt ước tính = chênh lệch ban đầu - phế liệu bán - nhôm còn cuối năm
- Lãi quản trị ước tính = doanh thu thành phẩm + phế liệu - (giá trị nhôm đầu năm + mua trong năm - giá trị nhôm cuối năm) - tiền công xưởng - chi phí khác đã ghi. Tiền thực thu báo riêng
- Công nợ khách theo tháng/năm: nợ đầu kỳ + bán trong kỳ - thu trong kỳ = nợ cuối kỳ; xem các phiếu chi tiết. Tính theo mốc cuối kỳ khi xem; dữ liệu nhập lùi ngày có thể làm thay đổi báo cáo
- Bộ chọn tháng công nợ liệt kê cả các tháng nằm trong hai năm dương lịch nếu kỳ quyết toán đi qua 31/12; tháng đầu/cuối được cắt đúng ngày đầu/chốt kỳ.
- Năm đã chốt khóa việc sửa phiếu thuộc năm đó; cho phép thu nợ của năm cũ vào kỳ mới
- Có thể chốt năm hiện tại ngay khi đã lưu kiểm kê và đáp ứng điều kiện chốt.
- Chọn ngày đầu kỳ và ngày chốt khi kiểm kê; kỳ có thể đi qua 31/12 và năm nhuận âm. Kỳ sau bắt đầu ngày kế tiếp. Năm trên bộ chọn là tên kỳ, không buộc trùng năm dương lịch của ngày đầu kỳ.

## Bổ sung theo code hiện tại

Bảng **Phiếu bán thành phẩm** hiển thị số lượng, đơn giá bán và thành tiền theo dữ liệu trên phiếu. Đơn giá dùng đúng đơn vị đã chọn trên dòng bán, chẳng hạn đồng mỗi tấn.

Nguyên liệu kiểm kê theo từng loại. Phân bổ kg vào các phiếu nhập xưởng đã xác nhận, chưa đảo, mới nhất trước; thiếu lấy tiếp phiếu cũ. Nếu lịch sử nhập không đủ thì từ chối tính, hiện chưa có màn nhập lớp tồn đầu kỳ theo loại. Phân bổ được lưu và tính lại khi chốt. Phiếu bán theo giá nhập tham chiếu lọc theo loại/giá; dòng cũ chưa gắn có thể gắn khi năm còn mở. Giá trị tham chiếu không được trừ thêm vào lãi năm. Công nợ kỳ được tính theo dữ liệu tại thời điểm xem, không phải bản chụp bất biến.
