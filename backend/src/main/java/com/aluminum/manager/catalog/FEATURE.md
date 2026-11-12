# Sản phẩm, loại nhôm và giá

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`CatalogService` quản lý sản phẩm, đơn vị, loại theo shop; `PurchasePriceService` quản lý lịch sử giá tham khảo theo loại và ngày. Đồng bộ danh mục Phân phối sang Xưởng không đồng bộ tồn.

Đổi sang giá khác tạo mốc lịch sử mới, kể cả cùng ngày, để còn tra được giá cũ và ngày bắt đầu áp dụng. Nếu nhiều mốc cùng ngày, mốc có ID mới nhất là giá dùng để tính theo ngày; phiếu đã xác nhận giữ snapshot giá chuẩn đã lưu.

## Màn hình liên quan

Ảnh mới lưu thành file, database giữ tham chiếu. Đồng bộ Phân phối sang Xưởng dùng chung file ảnh và chuyển ảnh base64 cũ của Phân phối sang thư mục ảnh khi đồng bộ.

[products](../../../../../../../../frontend/src/features/products/FEATURE.md)
