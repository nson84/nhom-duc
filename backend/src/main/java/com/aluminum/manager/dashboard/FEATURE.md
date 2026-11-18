# Tổng quan

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`DashboardService` tổng hợp doanh thu, tiền thu, chi phí, kho và biểu đồ; phân phối không tính tiền công. Xưởng sử dụng quyết toán năm cho lãi.

Tổng quan và biểu đồ theo kỳ gom tiền đã thu về **ngày lập phiếu bán** trong kỳ (mọi khoản trả đã ghi cho phiếu ấy); phần còn phải thu chỉ tính trên các phiếu bán trong kỳ, không kéo nợ kỳ khác vào. Phiếu nội bộ không tạo doanh thu/công nợ nhưng vẫn tính kg đã giao. `YearEndBalanceService` giữ bản chụp công nợ tại ngày chốt, còn Sổ Thu chi giữ ngày thực nhận tiền.

`YearEndBalanceService` lưu bản chụp tồn kho, phải thu, phải trả và lãi khi chốt năm. Công nợ chỉ gồm phiếu ngoài, tính đến trước ngày đầu năm sau; thanh toán nợ cũ sau đó không tính lãi lần hai.

`GET /api/v1/dashboard/year-period?year=...` trả ngày đầu/cuối kỳ và các cờ `started`, `endSaved`, `closed` theo cửa hàng hiện tại. Kỳ đã chốt lấy bản chụp; kỳ đang làm việc lấy ngày bắt đầu đã lưu và ngày hiện tại theo giới hạn báo cáo, kể cả khi đã đặt ngày dự kiến. Trường `plannedEnd` trả mốc dự kiến riêng. Kỳ chưa bắt đầu chỉ trả khoảng 01/01–31/12 hoặc mốc sau kỳ trước làm gợi ý, không dùng khoảng này để tính số liệu.

`POST /api/v1/dashboard/year-period/start?year=...&from=...&name=...` lưu ngày bắt đầu và tên kỳ tùy ý trong `year_period_starts`. Trước bước này kỳ chưa bắt đầu, không lấy số liệu theo năm. Kỳ đang làm việc dùng ngày hiện tại làm mốc cuối tạm thời để tính doanh thu/chi phí từ ngày bắt đầu. `POST /api/v1/dashboard/year-period?year=...&from=...&to=...` lưu riêng ngày kết thúc dự kiến trong `year_period_drafts`. Chỉ chốt sau khi cả hai mốc đã lưu; khi chốt, khoảng ngày phải khớp hai mốc. Kỳ đã chốt không đổi được; kỳ sau phải bắt đầu ngay sau kỳ trước. Tên kỳ được phép ghi “Kỳ 02/2026–02/2027” thay vì buộc vào một năm dương lịch.

`GET /api/v1/dashboard/month-status` trả `NOT_OPEN`, `OPEN` hoặc `CLOSED`; `POST /month-open` mở tháng và `POST /month-close` khóa tháng. Bảng `shop_month_periods` lưu theo cửa hàng và tháng dương lịch. Tháng đầu phải chứa ngày bắt đầu kỳ năm; mỗi tháng sau chỉ mở sau khi tháng trước đã khóa. Xưởng phải chốt kỳ công trước khi khóa tháng. Có thể đổi ngày kết thúc dự kiến sau khi mở hoặc khóa tháng. `requireWorkingDate` chỉ cho ghi vào tháng đang mở và, nếu request có `workingMonth`, chặn ngày ngoài tháng đang chọn. Khóa tháng không chốt năm hoặc xóa công nợ.

Lưu ngày bắt đầu không đòi người dùng đoán ngày kết thúc; nếu có mốc kỳ sau do dữ liệu cũ, chỉ kiểm tra hai ngày bắt đầu không chồng nhau. Quy tắc kết thúc sát kỳ sau được kiểm tra khi lưu ngày kết thúc.

Tham số `workingYear` chọn kỳ đang khóa theo cửa hàng. `requireWorkingDate` chặn phiếu trước kỳ đầu tiên, sau phạm vi làm việc, trong kỳ đã chốt và ngoài năm đang khóa. Phiếu kho và phiếu chi thủ công dùng chung quy tắc này; không xóa hoặc chuyển dữ liệu cũ.

Bản chụp lưu ngày bắt đầu/kết thúc kỳ do cửa hàng chọn. Nợ được tính đến hết ngày chốt; kỳ sau phải bắt đầu ngay ngày kế tiếp. Phân phối truyền tên năm chốt và khoảng ngày riêng; không cần chốt đúng 31/12. Không cần khóa tháng cuối trước khi chốt năm. Cần xử lý phiếu kho nháp trong khoảng chốt. Khoảng báo cáo tối đa 400 ngày để hỗ trợ năm âm lịch nhuận.

## Màn hình liên quan

[dashboard](../../../../../../../../frontend/src/features/dashboard/FEATURE.md)

Phân phối chỉ được chốt kỳ khi đã cân đủ từng SP/loại có biến động kho và lần cân mới nhất còn khớp tồn. Kiểm tra `distribution_inventory_count_evidence`: số cân, ngày cân, cả thời điểm tạo lẫn ngày nghiệp vụ của biến động sau kiểm kê, và các biến động sau ngày chốt. Không dùng tồn hiện tại để giả làm tồn quá khứ khi đã có giao dịch sau ngày chốt. Cần đối chiếu riêng dữ liệu lịch sử trước khi chốt.

Chốt sổ tháng tùy chọn tại /chot-so-thang, độc lập với Khóa tháng. Kiểm kê nguyên liệu và vụn theo loại, ô trống tính là 0 sau xác nhận. Bản lưu bất biến gồm doanh thu ngoài, giá trị nhập và chi hoạt động/lương theo ngày chi. Đây chưa phải tính lãi quyết toán tháng. Popup ngày cuối tháng theo ngày máy, rộng 60vw, nhắc sau 3 giờ hoặc bỏ qua tháng hiện tại. Trạng thái nhắc lưu trên trình duyệt theo cửa hàng/tháng.
