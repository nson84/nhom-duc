# Khách mua và công nợ

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

Danh sách khách trả thêm tiền thực thu và kg đã giao theo khoảng ngày bán, dựa trên phiếu xác nhận chưa đảo và chưa vào thùng rác.

Chi tiết từng khách trả thêm cùng hai số này để hiện đủ khối lượng đã giao, tiền hàng phải thu, tiền đã thu thực tế và nợ. Bấm từng hóa đơn để xem dòng hàng và các lần thanh toán.

`CustomerController`/`CustomerService` quản lý khách theo shop, nhắc nợ, danh sách phiếu và gắn khách; báo cáo năm của xưởng nằm trong factory.

## Màn hình liên quan

[customers](../../../../../../../../frontend/src/features/customers/FEATURE.md)
