# Thu chi và báo cáo

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`FinanceService` ghi thu/chi, báo cáo và xuất CSV; thanh toán bán/nhập gắn chứng từ gốc. Phân phối không được tạo chi lương, vẫn ghi chi phí hoạt động.

Báo cáo lãi gộp hóa đơn chỉ dùng giá nhập tham khảo theo ngày bán. Giá tham khảo bằng 0 hoặc thiếu được coi là chưa đủ dữ liệu; lãi hóa đơn và tổng tháng tương ứng để trống, không coi giá vốn là 0. Lãi này không phải giá vốn thực tế hoặc lãi quyết toán cuối kỳ.

`AnnualBookService` đọc dữ liệu theo từng sổ/năm cho bản in A4: khách, phiếu bán/nhập và dòng hàng, thanh toán, thu chi, công thợ Xưởng, tồn kho. Endpoint chỉ đọc và kiểm tra role trước khi cho xem sổ khác. Giá trị tồn và lãi chỉ có khi đã chốt năm; không cộng tiền thu/trả nợ cũ vào lãi lần hai.

Sổ lấy khoảng ngày từ bản chốt năm của từng cửa hàng. Sổ Xưởng chưa chốt dùng khoảng ngày kiểm kê nháp nếu có. Tiền công tháng được in theo kỳ chứa ngày cuối tháng.

API tạo giao dịch thủ công chỉ nhận phiếu chi. Báo cáo và lịch sử vẫn đọc các khoản thu đã ghi.

Phiếu chi thưởng lễ dùng nhóm chi lương và được cập nhật từ Lịch nghỉ lễ.

Giao dịch Thu chi Xưởng chỉ bị khóa khi ngày giao dịch thuộc khoảng ngày của kỳ xưởng đã chốt; thu nợ cũ sau ngày chốt vẫn ghi vào kỳ mới.

## Màn hình liên quan

[finance](../../../../../../../../frontend/src/features/finance/FEATURE.md)
