# Chức năng auth

Phiên đăng nhập mới được lưu trong trình duyệt và không hết hạn theo thời gian. Đăng xuất xóa phiên ở các tab cùng địa chỉ.

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

### `/dang-nhap` — Đăng nhập

| | |
|--|--|
| File | `features/auth/LoginPage.tsx` |
| Ai dùng | Tất cả |
| API | `POST /api/v1/auth/login` |

**Chức năng:** chọn tài khoản (`admin` / `phanphoi` / `xuong`) + mật khẩu → JWT; sync `shopCode` vào store; nếu `mustChangePassword` → `/doi-mat-khau`.

### `/doi-mat-khau` — Đổi mật khẩu bắt buộc

| | |
|--|--|
| File | `features/auth/ChangePasswordPage.tsx` |
| Ai dùng | User đang bị bắt đổi MK |
| API | `PUT /api/v1/auth/password` |

**Chức năng:** đổi mật khẩu lần đầu / bắt buộc; sau thành công vào app.

## Bổ sung theo code hiện tại

Đổi mật khẩu dùng `PUT /api/v1/auth/password`; đăng nhập dùng `POST /api/v1/auth/login`.

Form tự điền `1234567` khi chọn một trong ba tài khoản mặc định. Đây là mật khẩu khởi tạo cho tài khoản mới; tài khoản đã đổi mật khẩu phải nhập mật khẩu hiện tại. Backend không đặt lại mật khẩu khi khởi động; xem [tài liệu auth backend](../../../../backend/src/main/java/com/aluminum/manager/auth/FEATURE.md).
