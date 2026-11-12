# Phân quyền và xác thực

JWT mới mặc định không hết hạn theo thời gian. Phiên đã thu hồi được lưu trong cơ sở dữ liệu để tiếp tục bị chặn sau khi khởi động lại.

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`SecurityConfig`, `JwtAuthenticationFilter`, `RoleAccessFilter`: Admin chỉ đọc nghiệp vụ; Phân phối không truy cập nhân sự/lương; Xưởng có quyền vận hành nhân sự. Đối chiếu router và menu khi thay đổi.

## Màn hình liên quan

[auth](../../../../../../../../frontend/src/features/auth/FEATURE.md), [shop](../../../../../../../../frontend/src/features/shop/FEATURE.md)
