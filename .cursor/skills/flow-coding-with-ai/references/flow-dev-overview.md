# Flow Cho Dev — Overview

Bảng so sánh nhanh giữa 3 loại dự án. Đọc file này để có cái nhìn tổng quan, sau đó đọc file chi tiết tương ứng (Backend / Frontend / Fullstack).

---

## Nguyên Tắc Chung — Không Phụ Thuộc Loại Dự Án

```
Phase 1: Chuẩn bị (docs + rules)     ← Làm 1 lần, làm kỹ
         │
Phase 2: Dựng nền móng (skeleton)    ← Chạy được trước khi code feature
         │
Phase 3: Phát triển từng feature     ← 1 feature = 1 session
         │
Phase 4: Hoàn thiện                  ← Test tổng thể trước khi ship
```

**3 quy tắc vàng:**

1. **Docs trước, code sau.** AI nhất quán khi có rules rõ ràng — không có rules, AI tự "sáng tạo" mỗi session một kiểu.

2. **1 feature = 1 session.** Đóng session khi xong, không kéo dài. Session dài = context loãng = AI quên convention.

3. **Test trước khi tiếp.** Không bao giờ sang feature mới khi feature cũ chưa kiểm tra xong.

---

## Phase 1: Chuẩn Bị

### Project Rules

| Mục | Backend | Frontend | Fullstack |
|:---|:---:|:---:|:---:|
| Tech stack + versions | ✅ | ✅ | ✅ cả 2 phía |
| Cấu trúc project (feature-based) | ✅ | ✅ | ✅ cả 2 phía |
| Naming conventions | ✅ | ✅ | ✅ |
| Response/request format | ✅ | — | ✅ |
| Design tokens (colors, spacing, fonts) | — | ✅ | ✅ |
| Component conventions | — | ✅ | ✅ |
| Important rules | ✅ | ✅ | ✅ |
| Decision Log | ✅ | ✅ | ✅ |

### Docs

| File | Backend | Frontend | Fullstack | Vai trò |
|:---|:---:|:---:|:---:|:---|
| DATABASE.md | ✅ | — | ✅ | Entity, quan hệ |
| API_SPEC.md | ✅ | — | — | Danh sách API endpoints |
| PAGES.md | — | ✅ | ✅ | Danh sách pages/routes |
| API_CONTRACT.md | — | ✅ | ✅ ⭐ | Giao kèo FE ↔ BE |
| PROGRESS.md | ✅ | ✅ | ✅ | Tracking tiến độ |

⭐ API_CONTRACT.md là file quan trọng nhất cho fullstack.

---

## Phase 2: Dựng Nền Móng

| Bước | Backend | Frontend | Fullstack |
|:---|:---:|:---:|:---:|
| Tạo project + cấu trúc folder | ✅ | ✅ | ✅ |
| Tạo TẤT CẢ entities cùng lúc | ✅ | — | ✅ |
| Seed fake data | ✅ | — | ✅ |
| Setup Công cụ Test API (vd: Postman, Swagger...) | ✅ | — | ✅ |
| Tạo TẤT CẢ routes (placeholder) | — | ✅ | ✅ |
| Tạo shared components | — | ✅ | ✅ |
| Setup API service layer (Axios) | — | ✅ | ✅ |
| Config CORS | — | — | ✅ |
| Test kết nối FE ↔ BE | — | — | ✅ |

**Kết thúc Phase 2:** Dự án phải **chạy được** — dù chưa có feature nào hoàn chỉnh.

---

## Phase 3: Phát Triển Từng Feature

### Đơn vị 1 feature

| Loại | 1 feature = |
|:---|:---|
| Backend | 1 nhóm API (ví dụ: User CRUD) |
| Frontend | 1 page hoặc 1 flow (ví dụ: User list + form) |
| Fullstack | API + UI cho cùng 1 nghiệp vụ (tách sub-session BE + FE) |

### Thứ tự feature

| Thứ tự | Backend | Frontend | Fullstack |
|:---|:---|:---|:---|
| 1 | Auth API | Layout + Navigation | Auth (BE → FE) |
| 2 | User CRUD | Auth UI | User (BE → FE) |
| 3 | Company CRUD | User pages | Company (BE → FE) |
| 4 | Role/Permission | Company pages | Role/Permission (BE → FE) |
| 5 | Phân quyền API | Các page còn lại | Hoàn thiện |

### Vòng lặp mỗi feature

```
Mở session → AI đọc Project Rules
  │
  ├─ 1. Prompt tạo feature (trỏ đến feature đã xong để follow style)
  ├─ 2. Đọc hiểu code
  ├─ 3. Test
  ├─ 4. Viết automated test
  ├─ 5. Fix & refine
  ├─ 6. Update docs + Git commit
  │
Đóng session
```

### Cách test

| Hạng mục | Backend | Frontend | Fullstack |
|:---|:---|:---|:---|
| Happy path | Swagger/Postman | Browser | Cả hai |
| Validation | API trả lỗi đúng | Form hiển thị lỗi | Cả hai |
| Error states | 400/401/403/404/500 | Error message UI | FE hiển thị đúng error từ BE |
| Loading state | — | Có loading indicator | ✅ |
| Empty state | — | Thông báo "không có data" | ✅ |
| Responsive | — | Mobile / tablet / desktop | ✅ |
| Auth | Không token → 401 | Chưa login → redirect | Cả hai khớp nhau |

### Automated test

| Loại | Backend | Frontend | Fullstack |
|:---|:---|:---|:---|
| Unit test | ✅ Bắt buộc | Tùy dự án | ✅ BE bắt buộc |
| Integration test | ✅ Bắt buộc | — | ✅ BE bắt buộc |
| E2E test | Tùy dự án | Flow quan trọng | Flow quan trọng |

---

## Phase 4: Hoàn Thiện

| Hạng mục | Backend | Frontend | Fullstack |
|:---|:---:|:---:|:---:|
| Error handling tổng thể | ✅ | ✅ | ✅ |
| Full regression test | ✅ | ✅ | ✅ |
| Security review | ✅ | ✅ | ✅ |
| Performance check | ✅ | ✅ | ✅ |
| Responsive tất cả pages | — | ✅ | ✅ |
| E2E test flow chính | Tùy | ✅ | ✅ |
| CORS production config | — | — | ✅ |
| Auth flow end-to-end | ✅ | ✅ | ✅ |
| Build production | ✅ | ✅ | ✅|

---

## Sai Lầm Hay Gặp

### Chung

| Sai lầm | Cách tránh |
|:---|:---|
| Nhảy vào code không có docs | 1-2 giờ Phase 1 |
| Nhồi nhiều feature vào 1 session | 1 feature = 1 session |
| Không test trước khi sang feature kế tiếp | Test + regression trước commit |
| Không update PROGRESS / Decision Log | 2 phút cuối session, tiết kiệm hàng giờ |
| Tin AI 100% | Luôn Understand + Verify |

### Riêng theo loại

| Backend | Frontend | Fullstack |
|:---|:---|:---|
| Return full entity → leak data | Không tạo shared components trước | Không có API Contract |
| EAGER → N+1 | Bỏ qua responsive | Không test kết nối FE ↔ BE sớm |
| Không test security | Thiếu loading/error/empty state | CORS config "tạm" rồi quên |
| | AI tạo component mới thay vì dùng shared | Sửa API không update contract |

---

## Chọn Đọc File Nào?

| Bạn đang làm | Đọc file |
|:---|:---|
| Chỉ Backend (API cho mobile/team FE khác) | `flow-dev-backend.md` |
| Chỉ Frontend (gọi API có sẵn) | `flow-dev-frontend.md` |
| Cả Backend + Frontend | `flow-dev-fullstack.md` |
| Muốn xem tổng quan nhanh | Bạn đang đọc file này ✅ |
