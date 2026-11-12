# Chức năng landing

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

### `/gioi-thieu` — Trang giới thiệu (Landing)

| | |
|--|--|
| File | `features/landing/LandingPage.tsx` |
| Ai dùng | Khách (không đăng nhập) |
| API | `GET /api/v1/public/shop-info` |

**Chức năng**
- Hiển thị tên cửa hàng, địa chỉ, SĐT, loại nhôm, nội dung CMS (hero, điểm mạnh, CTA…)

**Không được hiện:** giá nhập, tồn, doanh thu, lãi, lương, cấu hình sao lưu.

### `/noi-dung-gioi-thieu` — Nội dung trang giới thiệu

| | |
|--|--|
| File | `features/landing/LandingContentPage.tsx` |
| Ai dùng | **Admin only** |
| API | landing-content get/update |

**Chức năng:** sửa chữ/sections trang `/gioi-thieu` (hero, điểm mạnh, CTA…).

## Bổ sung theo code hiện tại

Trang công khai không trả giá vốn, tồn, doanh thu hoặc lương.
