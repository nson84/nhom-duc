# Thu chi và báo cáo

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

`GET /api/v1/reports/carryover-debt?year=...` dùng ngày đầu và cuối kỳ của cửa hàng đăng nhập. Chỉ lấy phiếu ngoài đã xác nhận trước ngày đầu kỳ và còn nợ tại mốc đó. Các lần thanh toán thuộc kỳ dùng ngày giao dịch thực tế. Báo cáo chỉ đọc.

## Chức năng hiện có

`FinanceService` ghi thu/chi, báo cáo và xuất CSV; thanh toán bán/nhập gắn chứng từ gốc. Phân phối không được tạo chi lương, vẫn ghi chi phí hoạt động.

Sổ thu chi theo kỳ gắn khoản `SALE_RECEIPT` và `PURCHASE_PAYMENT` với ngày phiếu kho gốc, kể cả khi ngày thanh toán thực tế ở kỳ sau; khoản thủ công vẫn theo ngày giao dịch. Ngày thực thu/chi không bị sửa và vẫn hiển thị trên từng dòng để đối chiếu. Các KPI Tổng quan/Báo cáo theo phiếu dùng cùng quy tắc gắn kỳ; bản chụp công nợ khi chốt năm vẫn theo mốc chốt.

Khi giao diện chọn tháng, khoảng ngày lọc Thu chi/Báo cáo là phần giao của tháng và kỳ năm. Phiếu chi thủ công và việc hủy giao dịch kiểm tra ngày gốc không thuộc tháng đã chốt; thanh toán nợ cũ có thể ghi vào tháng mới đang mở.

Bảng báo cáo theo ngày xếp ngày mới nhất trước và đếm tất cả phiếu bán đã xác nhận, gồm phiếu chuyển nội bộ sang Xưởng. Cột doanh thu và đã thu chỉ tính phiếu bán cho khách bên ngoài; chuyển nội bộ không tạo doanh thu hay lãi.

Khoảng báo cáo cho phép tối đa 400 ngày, khớp kỳ làm việc có thể đi qua Tết âm lịch; giới hạn 366 ngày cũ làm Báo cáo lỗi khi kỳ chưa chốt dài hơn một năm dương lịch.

Báo cáo lãi gộp hóa đơn chỉ dùng giá nhập tham khảo theo ngày bán. Giá tham khảo bằng 0 hoặc thiếu được coi là chưa đủ dữ liệu; lãi hóa đơn và tổng tháng tương ứng để trống, không coi giá vốn là 0. Lãi này không phải giá vốn thực tế hoặc lãi quyết toán cuối kỳ.

`AnnualBookService` đọc dữ liệu theo từng sổ/năm cho bản in A4: khách, phiếu bán/nhập và dòng hàng, thanh toán, thu chi, công thợ Xưởng, tồn kho. Endpoint chỉ đọc và chỉ cho role vận hành xem sổ của mình; Admin không được xem. Mỗi phiếu bán trả cả tiền đã thu tại mốc chốt và tiền đã thu đến hiện tại để bản in phân biệt nợ lịch sử với nợ hiện tại. Phiếu nhập nội bộ Xưởng được trả riêng và không tính vào nợ nhà cung cấp. Giá trị tồn và lãi chỉ có khi đã chốt năm; không cộng tiền thu/trả nợ cũ vào lãi lần hai.

Khách có phiếu bán được nhập lùi ngày vẫn xuất hiện trong Sổ cuối năm dù hồ sơ khách được tạo sau ngày kết thúc kỳ; ngày phiếu bán quyết định kỳ của giao dịch, không dùng `customers.created_at` để loại khách đó.

Sổ cuối năm từ chối kỳ chưa lưu ngày bắt đầu. Báo cáo tiền công theo tên kỳ lọc tháng theo ngày cuối tháng nằm trong khoảng đầu/cuối của kỳ, kể cả khi kỳ kéo qua năm dương lịch sau.
Trang bìa Sổ cuối năm in tên kỳ tự đặt của từng cửa hàng.

Sổ lấy khoảng ngày từ bản chốt năm của từng cửa hàng. Sổ Xưởng chưa chốt dùng khoảng ngày kiểm kê nháp nếu có. Tiền công tháng được in theo kỳ chứa ngày cuối tháng.

API tạo giao dịch thủ công chỉ nhận phiếu chi. Báo cáo và lịch sử vẫn đọc các khoản thu đã ghi.

Phiếu chi thưởng lễ dùng nhóm chi lương và được cập nhật từ Lịch nghỉ lễ.

Giao dịch Thu chi Xưởng chỉ bị khóa khi ngày giao dịch thuộc khoảng ngày của kỳ xưởng đã chốt; thu nợ cũ sau ngày chốt vẫn ghi vào kỳ mới.

## Màn hình liên quan

[finance](../../../../../../../../frontend/src/features/finance/FEATURE.md)
