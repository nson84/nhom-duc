# Flow Cho Dev — Backend

Dành cho dự án chỉ có backend (REST API cho mobile app, cho team frontend khác, hoặc cho bên thứ 3 sử dụng).

---

## Phase 1: Chuẩn Bị

**Thời gian ước tính:** 1-2 giờ. Làm 1 lần duy nhất.

### Project Rules

File AI Agent đọc tự động mỗi session (CLAUDE.md, .cursorrules... tùy tool). Nội dung cần có:

- Tech stack + versions (Java 25, Spring Boot 3.x, MySQL...)
- Cấu trúc project (feature-based)
- Naming conventions (Entity, DTO, Controller method, API path)
- Response format (wrapper class, error format, pagination format)
- Important rules (không dùng EAGER, luôn dùng DTO, luôn có test...)
- Decision Log (ghi lại quyết định quan trọng theo thời gian)

**Mẹo:** Khi muốn AI tạo code nhất quán giữa các feature, thêm **code example cụ thể** vào Project Rules. Ví dụ: thay vì ghi "dùng pagination chuẩn", ghi luôn format response pagination bạn muốn. AI follow example chính xác hơn follow mô tả bằng lời.

### Docs

```
docs/
├── DATABASE.md      ← Entity, quan hệ, fields
├── API_SPEC.md      ← Danh sách API endpoints (method + path + mô tả + quyền)
└── PROGRESS.md      ← Tracking tiến độ (ban đầu để trống)
```

**DATABASE.md** — Thiết kế tất cả entities và quan hệ trước khi code. Entity là xương sống của backend. Ghi rõ: tên entity, fields, kiểu dữ liệu, quan hệ (ManyToOne, ManyToMany...).

**API_SPEC.md** — Liệt kê tất cả API cần làm. Không cần chi tiết request/response body lúc này — chỉ cần đủ để biết dự án có bao nhiêu API, chia thành mấy feature.

---

## Phase 2: Dựng Nền Móng

### Session đầu tiên — quan trọng nhất

**Bước 1: Tạo project + cấu trúc folder feature-based**

Kiểm tra: cấu trúc folder đúng theo Project Rules, config cơ bản hoạt động, project build và chạy được.

**Bước 2: Tạo TẤT CẢ entities cùng lúc**

Prompt AI đọc DATABASE.md và tạo hết entities. Tại sao tạo cùng lúc? Vì quan hệ giữa các entity phụ thuộc lẫn nhau (User cần Company, Role cần Permission). Khi AI thấy toàn bộ schema, nó generate chính xác hơn.

Kiểm tra: fields đúng, quan hệ đúng, annotations đúng, project build được.

**Bước 3: Seed fake data**

Tạo file `data.sql` hoặc dùng `code` — 2-3 companies, 5-10 users, 2-3 roles. Fake data dùng để test API (Swagger) và chạy automated test.

**Bước 4: Setup Công cụ Test API (Postman, Swagger...)**

Đây là "công cụ kiểm tra" bạn dùng xuyên suốt dự án.

### Checklist trước khi sang Phase 3

- [ ] Project chạy được, không lỗi
- [ ] Tất cả entities đã tạo, quan hệ đúng
- [ ] Database có fake data
- [ ] Swagger hoạt động
- [ ] Git commit: *"Initial project setup with all entities"*
- [ ] Update PROGRESS.md

---

## Phase 3: Phát Triển Từng Feature

**Quy tắc cốt lõi: 1 feature = 1 nhóm API = 1 session.**

### Thứ tự feature đề xuất

| Thứ tự | Feature | Lý do làm trước/sau |
|:---|:---|:---|
| 1 | Auth (login/register) | Các feature khác cần authentication để test |
| 2 | User CRUD + pagination | Feature đơn giản nhất, dùng để "đặt chuẩn" |
| 3 | Company CRUD | Tương tự User nhưng có quan hệ ManyToOne |
| 4 | Role & Permission | Phức tạp nhất — ManyToMany, phân quyền |
| 5 | Phân quyền toàn bộ API | Áp dụng Role/Permission lên tất cả endpoints |

### Vòng lặp cho mỗi feature

**Bước 1 — Prompt tạo feature:**

```
Đọc docs/API_SPEC.md phần [X] APIs và docs/DATABASE.md phần [X] entity.
Tạo feature [X] trong folder [X]/:
- Controller với các API endpoints theo spec
- Service với business logic
- DTO cho request/response
Tham khảo [feature đã hoàn thành] cho coding style.
```

Điểm quan trọng: **từ feature thứ 2 trở đi, luôn trỏ AI đến feature đã xong.** Khi làm Company, trỏ đến User. Khi làm Role, trỏ đến Company. Đây là cách hiệu quả nhất giữ code nhất quán.

**Bước 2 — Đọc hiểu code:**

Đọc từ trên xuống. Trả lời được: code này làm gì, tại sao viết như vậy. Nếu không hiểu → hỏi AI ngay. Nếu AI sai convention → sửa và ghi thêm vào Project Rules.

**Bước 3 — Test thủ công (Swagger/Postman):**

| Hạng mục | Kiểm tra |
|:---|:---|
| Happy path | API trả đúng data, đúng status code |
| Validation | Gửi data sai → trả lỗi rõ ràng (400) |
| Business errors | Email đã tồn tại → 400, không tìm thấy → 404 |
| Authentication | Không có token → 401 |
| Authorization | User thường gọi API admin → 403 |

**Bước 4 — Viết automated test:**

```
Viết test cho feature [X]:
- Unit test cho Service: happy path + error cases (not found, duplicate)
- Integration test cho Controller: tất cả endpoints + validation errors
Tham khảo test của feature [Y] cho format và style.
```

Chạy **toàn bộ test suite** sau khi viết — bao gồm test feature cũ (regression).

**Bước 5 — Fix & refine:**

Các vấn đề thường gặp: test fail (copy error → AI fix), response format sai convention, thiếu validation, N+1 query. **Fix trong cùng session** — không để "fix sau".

**Bước 6 — Update docs + Git commit:**

1. Update PROGRESS.md (feature nào xong, tiếp theo làm gì)
2. Update Decision Log nếu có quyết định mới
3. Git commit: `feat: complete [feature] with tests`
4. **Đóng session**

---

## Phase 4: Hoàn Thiện

- [ ] Error handling tổng thể (GlobalExceptionHandler)
- [ ] Chạy full regression test — tất cả feature
- [ ] Security review (password không leak, token đúng, phân quyền chặt)
- [ ] Performance check (N+1 query, index database)
- [ ] API documentation hoàn chỉnh (Swagger)
- [ ] Build production

---

## Sai Lầm Hay Gặp

| Sai lầm | Hậu quả | Cách tránh |
|:---|:---|:---|
| Nhảy vào code không có docs | 3 feature, 3 kiểu code | 1-2 giờ chuẩn bị Phase 1 |
| Nhồi nhiều feature vào 1 session | AI quên convention cuối session | 1 feature = 1 session |
| Không test trước khi tiếp | 5 feature chồng lỗi, debug cực khó | Test + regression trước commit |
| Return entity trực tiếp | Leak password, sensitive data | Luôn dùng DTO |
| FetchType.EAGER everywhere | N+1 query, app chậm | LAZY mặc định, EAGER khi thực sự cần |
| Tin AI với security code | Lỗ hổng bảo mật | Hiểu 100% security code, verify với docs |
