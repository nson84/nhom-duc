# Sản phẩm, loại nhôm và giá

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`CatalogService` quản lý sản phẩm, đơn vị, loại theo shop; `PurchasePriceService` quản lý lịch sử giá tham khảo theo loại và ngày. Đồng bộ danh mục Phân phối sang Xưởng không đồng bộ tồn.

Tạo loại, sửa giá nhập hoặc một trong ba giá bán lưu cả bộ giá vào lịch sử theo ngày lưu. Áp dụng lại một dòng có đủ giá bán cập nhật danh mục và lưu mốc mới khi giá thay đổi. Mốc cũ thiếu giá bán không thể áp dụng lại cả bộ.

Mốc đang áp dụng thiếu giá bán lấy giá hiện tại từ danh mục. Khi lưu loại nhôm, hệ thống ghi lại các giá còn thiếu của mốc đang áp dụng trước khi cập nhật danh mục, kể cả khi bộ giá không đổi. Các mốc đã hết hiệu lực giữ dữ liệu đã lưu.

Khi đồng bộ lại, sản phẩm Xưởng đã có chỉ nhận tên/ảnh; loại Xưởng đã có chỉ nhận giá nhập tham khảo. Mô tả, trạng thái và giá bán Xưởng được giữ riêng.

Đổi sang giá khác tạo mốc lịch sử mới, kể cả cùng ngày, để còn tra được giá cũ và ngày bắt đầu áp dụng. Nếu nhiều mốc cùng ngày, mốc có ID mới nhất là giá dùng để tính theo ngày; phiếu đã xác nhận giữ snapshot giá chuẩn đã lưu.

Ứng dụng tạo sản phẩm mới với loại mặc định để trống. Khi sửa sản phẩm cũ, ứng dụng giữ trường loại cũ để tương thích dữ liệu; trường này không dùng để chọn loại trên phiếu.

## Màn hình liên quan

Ảnh mới lưu thành file, database giữ tham chiếu. Đồng bộ Phân phối sang Xưởng dùng chung file ảnh và chuyển ảnh base64 cũ của Phân phối sang thư mục ảnh khi đồng bộ.

[products](../../../../../../../../frontend/src/features/products/FEATURE.md)
