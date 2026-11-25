# Chức năng theme

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

Modal dùng cùng màu surface và viền theo theme sáng/tối; phần thân cuộn mà không cắt footer ở viewport thấp.
Tiêu đề và nội dung hộp xác nhận dùng màu chữ của giao diện hiện tại, kể cả hộp mở bằng Modal.confirm.



## Bổ sung theo code hiện tại

`theme-store.ts` và `palette.ts` quản lý giao diện sáng/tối; AppShell cho phép chuyển theme.
