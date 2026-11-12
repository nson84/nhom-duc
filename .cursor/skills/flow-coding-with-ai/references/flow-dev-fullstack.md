# Flow Cho Dev — Fullstack

Dành cho dự án có cả backend và frontend, do 1 người làm. Đây là trường hợp phức tạp nhất vì bạn quản lý 2 codebase cùng lúc.

Điểm khác biệt lớn nhất so với backend-only hoặc frontend-only: **bạn cần một "giao kèo" giữa 2 phía — API Contract.** Không có nó, BE trả format A mà FE expect format B → debug mệt mỏi.

---

## Phase 1: Chuẩn Bị

**Thời gian ước tính:** 2-3 giờ. Làm 1 lần duy nhất.

### Cấu trúc project

Có 2 cách phổ biến:

```
# Cách 1: Monorepo (khuyến nghị cho solo dev)
project-root/
├── CLAUDE.md              ← Rules chung (hoặc .cursorrules)
├── docs/
│   ├── API_CONTRACT.md    ← File quan trọng nhất
│   ├── DATABASE.md
│   ├── PAGES.md
│   └── PROGRESS.md
├── backend/
│   ├── CLAUDE.md          ← Rules riêng cho BE (nếu cần)
│   └── src/
└── frontend/
    ├── CLAUDE.md          ← Rules riêng cho FE (nếu cần)
    └── src/

# Cách 2: Tách repo (phổ biến khi làm team)
backend-repo/    ← có CLAUDE.md + docs/ riêng
frontend-repo/   ← có CLAUDE.md + docs/ riêng
```

**Nếu làm solo, Cách 1 đơn giản hơn** — mọi thứ ở 1 chỗ, docs dùng chung, không cần sync giữa 2 repo.

### Project Rules

Bạn cần conventions cho **cả 2 phía**:

**Phần Backend** (trong CLAUDE.md chung hoặc backend/CLAUDE.md):
- Tech stack + versions (Java, Spring Boot, MySQL...)
- Cấu trúc folder feature-based
- Naming conventions, response format, DTO pattern
- Important rules (LAZY, DTO, test...)

**Phần Frontend** (trong CLAUDE.md chung hoặc frontend/CLAUDE.md):
- Tech stack + versions (React, TypeScript, Tailwind...)
- Cấu trúc folder feature-based
- Component conventions, design tokens
- Important rules (shared components, loading/error/empty state...)

**Phần chung:**
- Quy ước giao tiếp FE ↔ BE (base URL, auth header format, error response format)
- Decision Log

### Docs

| File | Vai trò |
|:---|:---|
| `docs/API_CONTRACT.md` | **Quan trọng nhất.** Request/response format cho từng API. Cả BE lẫn FE đều follow file này |
| `docs/DATABASE.md` | Entity, quan hệ, fields |
| `docs/PAGES.md` | Danh sách pages/routes |
| `docs/PROGRESS.md` | Tracking tiến độ cả BE và FE |

**API_CONTRACT.md** là trung tâm của dự án fullstack. Khi bạn thay đổi 1 API, update file này trước → rồi sửa BE → rồi sửa FE. Thứ tự này đảm bảo 2 phía luôn khớp nhau.

---

## Phase 2: Dựng Nền Móng

Fullstack cần dựng nền móng **cả 2 phía**, sau đó **test kết nối ngay**.

### Backend

1. Tạo project + cấu trúc folder feature-based
2. Tạo TẤT CẢ entities cùng lúc (dựa vào DATABASE.md)
3. Seed fake data
4. Setup Swagger
5. Config CORS (cho phép frontend gọi)

### Frontend

1. Tạo project + cấu trúc folder feature-based
2. Setup routing — tạo tất cả routes (trỏ đến placeholder)
3. Tạo shared components (Layout, Button, Table, Loading, ErrorMessage...)
4. Setup Axios instance (base URL trỏ đến backend)

### Test kết nối FE ↔ BE

Đây là bước **bắt buộc** trước khi sang Phase 3. Chỉ cần 1 API đơn giản (ví dụ GET /api/health hoặc GET /api/v1/users) nhưng phải đảm bảo:

- [ ] CORS config đúng — FE gọi BE không bị block
- [ ] Axios call thành công — response về đúng format
- [ ] Console không có CORS error, network error

**Tại sao test ngay?** Vì nếu đợi đến khi cả 2 phía xong mới kết nối, lúc đó phát hiện CORS, format không khớp, auth header sai... sẽ rất đau đầu. Phát hiện sớm = fix nhanh.

### Checklist trước khi sang Phase 3

- [ ] Backend chạy, Swagger hoạt động, database có fake data
- [ ] Frontend chạy, routing hoạt động, shared components render đúng
- [ ] FE gọi được ít nhất 1 API của BE thành công
- [ ] CORS không bị block
- [ ] Git commit: *"Initial project setup — BE + FE connected"*
- [ ] Update PROGRESS.md

---

## Phase 3: Phát Triển Từng Feature

### 2 cách tiếp cận

**Cách A — Backend trước, Frontend sau (khuyến nghị cho người mới):**

```
Session 1: BE — Auth API + test
Session 2: FE — Auth UI + kết nối API
Session 3: BE — User CRUD API + test
Session 4: FE — User UI + kết nối API
Session 5: BE — Company CRUD API + test
Session 6: FE — Company UI + kết nối API
...
```

Ưu điểm: đơn giản, rõ ràng. Khi làm FE, API đã sẵn sàng → không cần mock.

Nhược điểm: nếu phát hiện API design không hợp lý khi làm FE, phải quay lại sửa BE.

**Cách B — Tách sub-session cho mỗi feature (cho người có kinh nghiệm):**

```
Session 1a: Feature auth — BE (API + test)
Session 1b: Feature auth — FE (UI + kết nối API thật)
Session 2a: Feature user — BE (API + test)
Session 2b: Feature user — FE (UI + kết nối API thật)
...
```

Ưu điểm: mỗi feature hoàn chỉnh (cả API lẫn UI) trước khi sang feature tiếp. Phát hiện vấn đề API design sớm.

Nhược điểm: cần kỷ luật tách rõ BE và FE, không nhồi cả 2 vào 1 session.

### Thứ tự feature đề xuất

| Thứ tự | Backend | Frontend |
|:---|:---|:---|
| 1 | Auth API (login/register + JWT) | Layout + Navigation + Auth UI |
| 2 | User CRUD API + pagination | User list + User form |
| 3 | Company CRUD API | Company list + Company form |
| 4 | Role/Permission API | Role/Permission pages |
| 5 | Phân quyền tất cả API | Route guards theo role |

### Vòng lặp cho mỗi feature

#### Khi làm phần Backend (session BE):

**Prompt:**
```
Đọc docs/API_CONTRACT.md phần [X] và docs/DATABASE.md phần [X] entity.
Tạo feature [X] trong folder backend/[X]/:
- Controller, Service, DTO theo API contract
Tham khảo [feature trước] cho coding style. Viết test.
```

**Test:** Swagger → happy path, validation, error cases → Automated test → Regression.

**Commit:** `feat(be): complete [feature] API with tests`

#### Khi làm phần Frontend (session FE):

**Prompt:**
```
Đọc docs/API_CONTRACT.md phần [X] và docs/PAGES.md phần [X].
Tạo feature [X] trong folder frontend/[X]/:
- ListPage + FormPage
- Dùng shared components từ components/
- API đã sẵn sàng tại [base URL]
Tham khảo [feature trước] cho coding style.
```

**Test:**

| Hạng mục | Kiểm tra |
|:---|:---|
| Kết nối API thật | Data hiển thị đúng, CRUD hoạt động |
| Loading/Error/Empty | Cả 3 state đều hiển thị đúng |
| Validation | Form validate đúng, error message rõ ràng |
| Responsive | Mobile, tablet, desktop |
| Auth flow | Chưa login → redirect, token hết hạn → redirect |

**Commit:** `feat(fe): complete [feature] UI`

#### Sau khi xong cả BE + FE cho 1 feature:

Chạy test tổng thể:
- BE: tất cả automated test pass
- FE: mở browser, test flow hoàn chỉnh (list → create → edit → delete)
- Không break feature cũ

Update PROGRESS.md → Đóng session → Sang feature tiếp.

---

## Phase 4: Hoàn Thiện

### Backend

- [ ] Error handling tổng thể
- [ ] Security review
- [ ] Performance check (N+1, index)
- [ ] Full regression test

### Frontend

- [ ] Error boundary + 404 page
- [ ] Responsive tất cả pages
- [ ] Loading/error/empty states tất cả pages
- [ ] E2E test flow chính

### Chung

- [ ] CORS config production (không để wildcard `*`)
- [ ] Auth flow end-to-end (login → access protected page → token refresh → logout)
- [ ] Build production cả 2 phía
- [ ] Test trên production build (không chỉ dev mode)

---

## Sai Lầm Hay Gặp

| Sai lầm | Hậu quả | Cách tránh |
|:---|:---|:---|
| Không có API_CONTRACT.md | BE trả format A, FE expect format B | Viết contract trước, cả 2 phía follow |
| Nhồi cả BE + FE vào 1 session | Context cực loãng, AI lẫn lộn | Tách sub-session: 1 cho BE, 1 cho FE |
| Không test kết nối FE ↔ BE sớm | Cuối dự án mới phát hiện CORS, auth, format lỗi | Phase 2: test kết nối ngay |
| CORS config "tạm" rồi quên | Production bị block, hoặc mở quá rộng (security risk) | Config CORS đúng từ đầu, review ở Phase 4 |
| Sửa API mà không update contract | FE break mà không biết tại sao | Quy trình: update contract → sửa BE → sửa FE |
| Fix nhanh bằng cách sửa cả 2 phía cùng lúc | Không biết lỗi ở BE hay FE, commit lộn xộn | Tách commit: `feat(be):` và `feat(fe):` riêng |
