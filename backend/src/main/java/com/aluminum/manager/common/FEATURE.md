# Thành phần dùng chung

`APPLICATION_STARTING` trả HTTP 503 khi endpoint readiness được gọi ngay trong lúc khởi tạo.

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`ApiResponse`, `BusinessException`, `ErrorCode`, `GlobalExceptionHandler`, `NameValidation` quy định phản hồi và kiểm tra chung; không có màn hình riêng.

## Màn hình liên quan

Không có màn hình riêng.
