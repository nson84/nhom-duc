# Tổng quan

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`DashboardService` tổng hợp doanh thu, tiền thu, chi phí, kho và biểu đồ; phân phối không tính tiền công. Xưởng sử dụng quyết toán năm cho lãi.

`YearEndBalanceService` lưu bản chụp tồn kho, phải thu, phải trả và lãi khi chốt năm. Công nợ chỉ gồm phiếu ngoài, tính đến trước ngày đầu năm sau; thanh toán nợ cũ sau đó không tính lãi lần hai.

`GET /api/v1/dashboard/year-period?year=...` trả ngày đầu/cuối kỳ theo cửa hàng hiện tại. Kỳ đã chốt lấy bản chụp, Xưởng chưa chốt lấy kiểm kê nháp; kỳ mới bắt đầu sau ngày kết thúc kỳ trước đã chốt. Chưa có kỳ trước thì dùng 01/01–31/12 của năm được chọn.

Bản chụp lưu ngày bắt đầu/kết thúc kỳ do cửa hàng chọn. Nợ được tính đến hết ngày chốt; kỳ sau phải bắt đầu ngay ngày kế tiếp. Phân phối truyền tên năm chốt và khoảng ngày riêng; không cần chốt đúng 31/12. Khoảng báo cáo tối đa 400 ngày để hỗ trợ năm âm lịch nhuận.

## Màn hình liên quan

[dashboard](../../../../../../../../frontend/src/features/dashboard/FEATURE.md)
