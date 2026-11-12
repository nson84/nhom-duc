# Chức năng settings

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

### `/cai-dat` — Cài đặt

| | |
|--|--|
| File | `features/settings/SettingsPage.tsx` |
| Ai dùng | **Admin only** |
| API | `/api/v1/settings` |

**Chức năng**
- Tên cửa hàng, địa chỉ, SĐT (feed landing công khai)
- Giờ sao lưu, số ngày giữ, thư mục sao lưu
- Link **Tài khoản QR nhận tiền** → `/tai-khoan-qr`
- Link sang sửa nội dung trang giới thiệu

### `/tai-khoan-qr` — Tài khoản QR nhận tiền

| | |
|--|--|
| File | `features/settings/BankAccountsPage.tsx` |
| Ai dùng | Mọi role đăng nhập (Admin cũng ghi được) |
| API | `/api/v1/bank-accounts` |
| Vào từ | Menu avatar → **Tài khoản QR nhận tiền** |

**Chức năng**
- Thêm nhiều tài khoản: tên gợi nhớ, ngân hàng (BIN), số TK, tên chủ TK, **ảnh thẻ QR** (upload ảnh như thẻ MB/VietQR)
- Đặt một tài khoản **mặc định**; sửa / xóa
- Khi in danh sách nợ: chọn tài khoản → hệ thống **chỉ in một QR động** gắn số tiền + nội dung. Ảnh thẻ upload chỉ để nhận diện trong màn quản lý, không in cạnh mã thanh toán
- Không lộ qua `/public/shop-info`

## Bổ sung theo code hiện tại

Cài đặt cửa hàng dành cho Admin; tài khoản QR theo API và quyền trong RoleAccessFilter.
