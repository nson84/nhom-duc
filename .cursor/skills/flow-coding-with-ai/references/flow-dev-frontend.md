# Flow Cho Dev — Frontend

Dành cho dự án chỉ có frontend (React, Vue, Angular...) — gọi API từ backend đã có sẵn hoặc từ bên thứ 3.

---

## Phase 1: Chuẩn Bị

**Thời gian ước tính:** 1-2 giờ. Làm 1 lần duy nhất.

### Project Rules

File AI Agent đọc tự động mỗi session. Nội dung cần có:

- Tech stack + versions (React 19, TypeScript, Tailwind, React Router, Axios...)
- Cấu trúc project (feature-based)
- Component conventions (naming, file structure, props pattern)
- Design tokens (primary/secondary colors, spacing scale, font sizes, breakpoints)
- State management pattern (Context, Zustand, Redux...)
- Important rules (không dùng inline styles, luôn có loading/error/empty state, luôn dùng shared components...)
- Decision Log

**Điểm khác biệt so với backend:** Frontend cần **design tokens** trong Project Rules. Nếu không có, AI sẽ tự chọn màu, font size, spacing mỗi component một kiểu → UI không nhất quán. Ghi rõ: primary color là gì, spacing dùng scale nào, breakpoints là bao nhiêu.

### Docs

```
docs/
├── PAGES.md            ← Danh sách tất cả pages/routes
├── API_CONTRACT.md     ← API mà frontend sẽ gọi (URL, method, request/response format)
└── PROGRESS.md         ← Tracking tiến độ
```

**PAGES.md** — Liệt kê tất cả pages và routes của ứng dụng. Đây là xương sống của frontend, giống entities là xương sống của backend. Ví dụ:

```
/login              → LoginPage
/register           → RegisterPage
/dashboard          → DashboardPage (protected)
/users              → UserListPage (protected, ADMIN)
/users/create       → UserFormPage (protected, ADMIN)
/users/:id/edit     → UserFormPage (protected, ADMIN)
/companies          → CompanyListPage (protected)
...
```

**API_CONTRACT.md** — Mô tả các API mà frontend sẽ gọi. Ghi rõ: URL, method, request body, response format, status codes. Đây là "nguồn sự thật duy nhất" để bạn và AI đều biết API trả về gì.

---

## Phase 2: Dựng Nền Móng

### Session đầu tiên

**Bước 1: Tạo project + cấu trúc folder feature-based**

```
src/
├── config/              ← Axios instance, constants, routes config
├── components/          ← Shared components (Layout, Button, Table...)
├── hooks/               ← Shared custom hooks
├── auth/                ← Feature: Login, Register
├── user/                ← Feature: User list, User form
├── company/             ← Feature: Company list, Company form
└── ...
```

**Bước 2: Setup routing**

Tạo tất cả routes — có thể trỏ đến trang trống (placeholder). Mục đích: nhìn vào router biết ngay app có bao nhiêu page, page nào protected, page nào public.

**Bước 3: Tạo shared components**

Đây là bước quan trọng nhất của frontend. Tạo trước các component dùng chung:

| Component | Vai trò |
|:---|:---|
| Layout (Header, Sidebar, Content) | Khung chung cho mọi page |
| Button | Nút bấm thống nhất (primary, secondary, danger...) |
| Table | Bảng dữ liệu + pagination |
| Form components (Input, Select, DatePicker) | Form thống nhất |
| Loading | Loading indicator |
| ErrorMessage | Hiển thị lỗi |
| EmptyState | Khi không có dữ liệu |
| Modal / Confirm Dialog | Popup xác nhận |

**Tại sao tạo trước?** Vì chúng được dùng ở mọi page. Nếu không thống nhất từ đầu, AI sẽ tự tạo Button, Table riêng cho mỗi feature → UI không nhất quán, phải refactor lại.

**Bước 4: Setup API service layer**

Tạo Axios instance (hoặc fetch wrapper) với: base URL, interceptors (gắn token, handle 401 redirect), error handling chung.

**Bước 5: Setup mock data (nếu API chưa sẵn sàng)**

Nếu backend chưa có hoặc bạn muốn làm FE trước, tạo mock data để hiển thị. Có thể dùng: JSON file, mock server (json-server), hoặc hardcode trong service.

### Checklist trước khi sang Phase 3

- [ ] Project chạy được (`npm run dev` không lỗi)
- [ ] Routing hoạt động — navigate giữa các page được
- [ ] Shared components render đúng
- [ ] Axios instance hoạt động (gọi được API hoặc mock)
- [ ] Git commit: *"Initial project setup with shared components"*
- [ ] Update PROGRESS.md

---

## Phase 3: Phát Triển Từng Feature

**Quy tắc cốt lõi: 1 feature = 1 page hoặc 1 flow = 1 session.**

### Thứ tự feature đề xuất

| Thứ tự | Feature | Lý do |
|:---|:---|:---|
| 1 | Layout + Navigation | Khung chung cho toàn bộ app |
| 2 | Auth (Login/Register) | Các page khác cần auth state để biết user đã login chưa |
| 3 | Page đơn giản nhất (User list) | "Đặt chuẩn": cách dùng Table, pagination, search, gọi API |
| 4 | Page CRUD đầy đủ (User form) | Template cho create/edit form, validation UI, submit flow |
| 5 | Các feature còn lại | Follow pattern đã có từ User |

### Vòng lặp cho mỗi feature

**Bước 1 — Prompt tạo feature:**

```
Đọc docs/PAGES.md phần [X] và docs/API_CONTRACT.md phần [X] APIs.
Tạo feature [X] trong folder [X]/:
- [X]ListPage: table + pagination + search
- [X]FormPage: create/edit form + validation
- Dùng shared components: Table, Button, Loading, ErrorMessage
- Dùng Axios instance từ config/api
Tham khảo [feature đã hoàn thành] cho coding style.
```

Điểm quan trọng: **luôn yêu cầu AI dùng shared components** thay vì tự tạo mới. Và **trỏ đến feature đã xong** để AI follow cùng style.

**Bước 2 — Đọc hiểu code:**

Đọc từ trên xuống. Kiểm tra: có dùng shared components không, state management đúng pattern không, API call đúng contract không. Nếu AI tự tạo component mới thay vì dùng shared → yêu cầu sửa.

**Bước 3 — Test thủ công (mở browser):**

| Hạng mục | Kiểm tra |
|:---|:---|
| Happy path | Hiển thị đúng data, click đúng hành vi |
| Loading state | Có loading indicator khi gọi API |
| Error state | API lỗi → hiển thị error message, không trang trắng |
| Empty state | Không có data → hiển thị thông báo phù hợp |
| Validation | Nhập sai form → hiển thị lỗi rõ ràng từng field |
| Responsive | Test 3 breakpoints: mobile (~375px), tablet (~768px), desktop (~1280px) |
| Navigation | Click link/button → đúng trang, back button hoạt động |
| Auth guard | Chưa login → redirect login page |

**Mẹo:** Mở DevTools → tab Network để kiểm tra API call đúng URL, method, payload. Tab Console để xem có warning/error không.

**Bước 4 — Viết automated test (tùy dự án):**

Frontend automated test không bắt buộc cho mọi component, nhưng nên viết cho:

- **Flow quan trọng** (login, tạo/sửa/xóa record) → E2E test (Cypress, Playwright)
- **Logic phức tạp** (custom hooks, utility functions) → Unit test (Vitest, Jest)

Nếu dự án nhỏ, test thủ công kỹ ở Bước 3 là đủ. Nếu dự án cần maintain lâu dài, E2E test cho happy path chính rất đáng đầu tư.

**Bước 5 — Fix & refine:**

Các vấn đề thường gặp:

| Vấn đề | Cách xử lý |
|:---|:---|
| UI vỡ layout trên mobile | Mô tả breakpoint cho AI → sửa responsive |
| Thiếu loading/error state | Yêu cầu AI bổ sung |
| Không dùng shared component | Yêu cầu AI thay bằng shared component |
| API call sai format | Check API_CONTRACT.md → sửa cho đúng |
| UX flow không mượt | Mô tả flow mong muốn → AI điều chỉnh |

**Bước 6 — Update docs + Git commit:**

1. Update PROGRESS.md
2. Update Decision Log nếu có quyết định mới (ví dụ: "dùng Zustand thay Context cho user state vì cần share giữa nhiều page")
3. Git commit: `feat: complete user list and form pages`
4. **Đóng session**

---

## Phase 4: Hoàn Thiện

- [ ] Error handling tổng thể (error boundary, 404 page, network error)
- [ ] Test responsive tất cả pages — mobile, tablet, desktop
- [ ] Test tất cả loading/error/empty states
- [ ] E2E test cho flow chính (login, CRUD chính)
- [ ] Performance check (lazy loading routes, image optimization, bundle size)
- [ ] Accessibility cơ bản (alt text, keyboard navigation, focus management)
- [ ] Build production (`npm run build` không warning)

---

## Sai Lầm Hay Gặp

| Sai lầm | Hậu quả | Cách tránh |
|:---|:---|:---|
| Không tạo shared components trước | Mỗi page một kiểu Button/Table | Phase 2: tạo shared trước khi làm feature |
| Không ghi design tokens | AI chọn màu/spacing ngẫu nhiên mỗi lần | Ghi rõ trong Project Rules |
| Bỏ qua loading/error/empty state | User thấy trang trắng, không biết chuyện gì xảy ra | Ghi trong rules: "mọi page PHẢI có 3 state này" |
| Chỉ test desktop | Mobile vỡ layout, user phàn nàn | Test 3 breakpoints trước mỗi commit |
| Không có API_CONTRACT | Gọi API sai URL, sai format, debug mệt | Viết contract trước, FE và BE cùng follow |
| AI tự tạo component mới thay vì dùng shared | UI không nhất quán | Prompt rõ: "dùng shared components từ components/" |
