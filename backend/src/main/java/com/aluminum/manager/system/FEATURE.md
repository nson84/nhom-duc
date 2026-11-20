# Cài đặt, tài khoản QR và sẵn sàng

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`SettingsController`, `BankReceivingAccountController`, `SystemController`; cài đặt, QR nhận tiền, audit và trạng thái sẵn sàng.

## Màn hình liên quan

[settings](../../../../../../../../frontend/src/features/settings/FEATURE.md), [system](../../../../../../../../frontend/src/features/system/FEATURE.md)

20/11: GET /api/v1/system/ready trả thêm version và revision từ META-INF/build-info.properties. Không có metadata thì ghi development/unversioned; không suy đoán là bản mới nhất. build-windows.ps1 truyền SHA và hậu tố -dirty cho backend và frontend. Menu tài khoản hiện bản giao diện, backend và schema.
