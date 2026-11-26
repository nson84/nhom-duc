# Cài đặt, tài khoản QR và sẵn sàng

Đọc tài liệu này trước khi sửa code trong thư mục; cập nhật cùng thay đổi.

## Chức năng hiện có

`/system/ready` trả 503 khi Spring Boot còn chạy các bước khởi tạo, bao gồm tạo tài khoản lần đầu. Sau khi app nhận kết nối, endpoint mới kiểm tra database và báo sẵn sàng.

`SettingsController`, `BankReceivingAccountController`, `SystemController`; cài đặt, QR nhận tiền, audit và trạng thái sẵn sàng.

Trường cài đặt số ngày giữ backup được bảo lưu để tương thích API cũ. `BackupService` áp dụng giới hạn 50 bản trên máy.

## Màn hình liên quan

[settings](../../../../../../../../frontend/src/features/settings/FEATURE.md), [system](../../../../../../../../frontend/src/features/system/FEATURE.md)

20/11: GET /api/v1/system/ready trả thêm version và revision từ META-INF/build-info.properties. Không có metadata thì ghi development/unversioned; không suy đoán là bản mới nhất. build-windows.ps1 truyền SHA và hậu tố -dirty cho backend và frontend. Menu tài khoản hiện bản giao diện, backend và schema.

22/11: `/api/v1/system/ready` trả thêm `databaseName` từ `SELECT DATABASE()` để desktop xác nhận đúng DB sau khi chuyển phục hồi. Với runtime phục hồi, readiness cũng kiểm tra khóa giải mã đã lưu khớp khóa chuyển từ bản R2; khóa sai làm readiness lỗi để desktop quay lại DB trước. Không trả tên đăng nhập hay mật khẩu MySQL.
