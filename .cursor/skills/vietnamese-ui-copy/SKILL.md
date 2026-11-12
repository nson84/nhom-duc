---
name: vietnamese-ui-copy
description: >-
  Quy tắc chữ trên giao diện app Nhôm Đức: tiếng Việt đầy đủ, không viết tắt,
  không dùng tiếng Anh trên nhãn/cột/nút/thông báo người dùng thấy. Dùng khi
  sửa UI, form, bảng, modal, tag trạng thái, hoặc thêm màn hình mới.
---

# Chữ giao diện tiếng Việt đầy đủ

## Bắt buộc

- Mọi nhãn, tiêu đề cột, nút, tag, placeholder, thông báo lỗi/thành công mà **người dùng đọc được** phải là **tiếng Việt viết đủ từ**.
- **Không viết tắt** trên UI (ví dụ không dùng `TT`, `SP`, `NCC`, `SL`, `ĐVT` nếu có thể viết đủ).
- **Không dùng tiếng Anh** trên UI (ví dụ không `Status`, `Save`, `Cancel`, `Payment`, `Customer`, `OK`).

## Ví dụ đúng / sai

| Sai | Đúng |
|-----|------|
| TT | Thanh toán |
| Đã thu | Đã thu đến nay (khi cần phân biệt với từng lần) |
| Lần thu | Thời gian thanh toán |
| Status | Trạng thái |
| Save | Lưu |
| Cancel | Hủy / Đóng |
| Partial | Thanh toán một phần |

## Thanh toán một phần

- Không chỉ hiện tổng “đã thu X”. Mỗi lần thanh toán phải ghi **ngày giờ cụ thể**, **số tiền lần đó**, và **còn phải thu/chi sau lần đó** khi hiển thị lịch sử.
- Cột/trạng thái dùng đủ chữ: **Thanh toán**, **Thanh toán một phần**, **Chưa thanh toán**, **Đã thanh toán**.

## Ngoại lệ

- Mã kỹ thuật trong code, API field, enum (`PARTIAL`, `UNPAID`), class CSS, tên file — được giữ tiếng Anh.
- Đơn vị đo đã quen (`kg`, `m²`, `tạ`, `tấn`) và ký hiệu tiền `đ` giữ nguyên.
- Tên đăng nhập tài khoản hệ thống (`admin`, `phanphoi`, `xuong`) giữ nguyên.
