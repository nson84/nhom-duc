# Kiểm thử đã khóa và phần còn lại

## Đang có kiểm thử đơn vị

| ID | Việc đã khóa |
| --- | --- |
| INV-03 | Hệ số quy đổi bằng 0 bị từ chối |
| INV-05 | 10 cây × 3,5 kg làm tồn giảm 35 kg |
| INV-06 | Xuất vượt tồn bị từ chối |
| INV-07 | Phiếu không còn nháp thì không xác nhận lần nữa |
| INV-09 | Giá đã chốt không đi theo giá danh mục mới |
| INV-10 | Đảo phiếu đổi dấu biến động và chỉ đảo một lần |
| INV-11 | 100 kg × 70.000 và 50 kg × 80.000 ra giá vốn 73.333,333333 |
| INV-12 | Chiết khấu làm thành tiền âm thì bị từ chối |
| INV-13 | Chi phí chuẩn lấy theo giá nhập có hiệu lực đúng ngày phiếu, không theo giá hôm nay |
| INV-14 | Sửa giá nhập về sau không đổi chi phí chuẩn của phiếu đã xác nhận |
| INV-15 | Chênh lệch giá nhập = giá vốn thực tế − giá vốn chuẩn, dương là nhập đắt hơn chuẩn |
| INV-16 | Tạo phiếu nhập lệch giá: popup xuất hiện trước khi lưu, nêu giá cũ/mới và ngày hiệu lực; chọn chỉ lưu phiếu không đổi giá chuẩn |
| INV-17 | Đổi giá hai lần cùng ngày giữ hai mốc lịch sử; mốc mới nhất là giá hiện tại |
| INV-18 | Hủy phiếu nháp rồi chuyển thùng rác và khôi phục, không gặp lỗi backend khi `trashed_at` ban đầu trống |
| ATT-01 | Tháng 2 năm nhuận có 29 ngày |
| ATT-02 | Tháng 2 năm thường có 28 ngày |
| ATT-03 | Đủ công lấy hệ số trong cấu hình |
| ATT-04 | Nửa công lấy hệ số nửa ngày trong cấu hình |
| ATT-05 | 08:00–17:00 trừ 60 phút nghỉ bằng 8 giờ |
| ATT-06 | Ca 22:00–06:00 được tính sang ngày hôm sau khi cấu hình cho phép |
| ATT-07 | Tăng ca chưa duyệt không cộng vào tiền |
| ATT-08 | Điểm danh sáng hoặc chiều hoặc cả hai đều quy đổi một công |
| FAC-01 | Hao hụt trừ số kg phế liệu đã bán và kg kiểm kê cuối năm |
| FAC-02 | Tồn 10 tấn phân bổ 8 tấn lần nhập cuối và 2 tấn lần trước; từ chối khi lịch sử không đủ |
| SEC-01 | Phân phối bị chặn nhân sự/lương và vẫn được đi vào luồng thu chi |

## Cần MySQL trước khi phát hành

Có thể nạp bộ dữ liệu 2025 theo lệnh trong `README.md` để thử chốt năm và Sổ cuối năm. Bộ dữ liệu này được kiểm tra bằng chế độ `-DryRun` trước khi ghi: 30 phiếu nhập ngoài 1.000 tấn, 100 phiếu bán Phân phối, 12 cặp phiếu chuyển nội bộ 500 tấn, 80 phiếu bán Xưởng, tồn Phân phối 1 tấn, kiểm kê Xưởng 200 kg và hao hụt ước tính 100 kg. Cần xem báo cáo 2025 của cả hai sổ, đối chiếu công nợ còn mở và khoản thu/trả nợ đầu 2026, rồi thử chốt vào ngày người dùng chọn.

INV-01, INV-02, INV-04, INV-08 và toàn bộ luồng xác nhận phiếu trong transaction. ATT-08 đến ATT-12. FIN-01 đến FIN-08. APP-01 đến APP-10, trong đó cài đặt Windows cần Rust và MySQL trên máy sạch.

Kiểm tra thêm với MySQL: chốt Xưởng lưu tồn, lãi, phải thu và phải trả theo ngày chốt do người dùng chọn; thu/trả nợ sau ngày chốt không đổi bản chụp hoặc tính lãi lần hai. Chốt Phân phối với khoảng qua 31/12 tạo bản số dư đúng tên năm; kỳ sau bắt đầu ngay ngày tiếp theo, ngày trùng/khoảng hở bị từ chối. In Sổ cuối năm phải hiện mốc của từng sổ. Phiếu Xưởng thuộc kỳ đã chốt bị khóa, phiếu sau ngày chốt được phép ghi.

Kiểm tra Sổ cuối năm với dữ liệu nhiều năm: phiếu cũ còn nợ và khoản thu/trả nợ cũ xuất hiện đúng kỳ; đối chiếu tổng nợ theo từng phiếu, thu/chi theo ngày, tồn kho cuối năm và tiền công Xưởng. Kiểm tra quyền: Xưởng/Phân phối không lấy được sổ của bên kia, Admin in được cả hai sổ. Kiểm tra bản in A4 nhiều trang, tiếng Việt và bảng dài không cắt dòng.

Kiểm tra bộ lọc tên năm trên Tổng quan, Báo cáo, Thu chi: khi chọn kỳ đã chốt theo Tết âm lịch, hai ngày hiển thị phải khớp bản chốt và mọi phiếu, thu chi ngoài khoảng này bị loại. Kỳ chưa chốt tiếp theo phải bắt đầu đúng ngày sau kỳ trước; Sổ cuối năm dùng cùng mốc của từng cửa hàng. Ô chọn khoảng ngày trên Tổng quan chỉ dùng khi đặt kỳ chốt Phân phối.

Kiểm tra form phiếu chi ở cả hai sổ có đúng năm lựa chọn mới; lưu từng mục và xác nhận danh sách, chi tiết, Sổ cuối năm hiển thị tên khoản chi. Xác nhận bốn khoản đầu tính vào chi phí hoạt động, khoản sửa chữa vào bảo trì, và chi trả phiếu nhập vẫn chỉ ghi trên phiếu nhập.

Kiểm tra mở trang Tiền công hoặc tải lại trang nhiều lần liên tiếp khi kỳ tháng chưa có: các yêu cầu tạo/lấy kỳ đồng thời phải cùng trả về một kỳ, không hiện lỗi ràng buộc trùng `(period_year, period_month)`.
