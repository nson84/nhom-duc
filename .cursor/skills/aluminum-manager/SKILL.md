---
name: aluminum-manager
description: >-
  Locked MVP rules for the Nhom-Duc aluminum shop Windows desktop app
  (React, Vite, TypeScript, Ant Design, Tauri 2, Spring Boot 4.1.2, Java 17,
  MySQL 8, Flyway). Use when changing this repo's frontend, backend, schema,
  stock, documents, attendance, payroll, finance, reports, backup, or packaging
  so work follows the existing architecture instead of redesigning it.
---

# Aluminum manager

Repo root is this project. One machine, three local accounts: `admin` (read-only reports/vouchers + backup/settings), `phanphoi` (DISTRIBUTION shop), `xuong` (MANUFACTURING shop). Default password `123456`. Two shops share one database: `DISTRIBUTION` (nhập/bán tấn, no payroll) and `MANUFACTURING` (nhập tạ/tấn, bán kg, payroll). Shop is fixed by role; admin may send `X-Shop-Code` or `scope=all` for combined finance/reports (internal transfers stay out of external totals). Do not add cloud multi-user, sync, deep accounting debt ledgers, fingerprint, bank API / statement sync, or e-invoice. Local VietQR receiving account in Settings (BIN + account) for debt-list print is in scope.

## Where code goes

- Frontend: `frontend/` (React + Vite + TypeScript + Ant Design). Features live in `frontend/src/features/` (`auth`, `dashboard`, `settings`, `system`, `modules`). HTTP client: `frontend/src/services/api-client.ts`.
- Tauri 2 owns the Spring Boot process: `frontend/src-tauri/`.
- Backend: `backend/`, package `com.aluminum.manager`. Existing packages: `auth`, `attendance`, `common`, `config`, `dashboard`, `inventory`, `security`, `system`.
- Schema is Flyway. `backend/src/main/resources/db/migration/V1__init_schema.sql` already ran. Add a new `V2__...sql` (and later) for schema changes. Do not edit V1.
- Domain math and codes already exist. Reuse them: `inventory/InventoryMath.java`, `inventory/DocumentSequenceService.java`, `inventory/StockConfirmationPolicy.java`, `attendance/AttendanceCalculator.java`, `config/ClockConfig.java` (`Asia/Ho_Chi_Minh`), `common/ApiResponse.java`.

## Local only & MySQL execution rule

- Tauri starts and stops Spring Boot. The API binds `127.0.0.1` (`backend/src/main/resources/application.yml`).
- **Always connect to real MySQL (`aluminum_app`)**: Default runtime must connect directly to MySQL (`jdbc:mysql://127.0.0.1:3306/aluminum_app?createDatabaseIfNotExist=true...`). Never use in-memory/local embedded DBs (H2, SQLite, mock DB) for application runtime — only for unit tests (`mvnw test`).
- **Auto-apply DB files to MySQL**: Whenever creating or updating any DB/SQL/Flyway migration file (`backend/src/main/resources/db/migration/V*__*.sql` or `scripts/*.sql`), immediately run/restart the backend or execute the migration against MySQL so tables and schema changes are materialized in MySQL right away, and verify via MySQL queries.
- Do not put the DB password or JWT secret in React, Vite env files committed to the app, or the Tauri frontend.

## Stock and documents

- One base stock unit per product (`product_units`, factor 1). Stock is `stock_balances.quantity_base`. It cannot go negative.
- Every document line stores document quantity, selling unit, `factor_to_base_snapshot`, and `quantity_base`. Quantities are decimal. Do not invent a factor. Do not change a conversion factor that a confirmed line already snapshotted; add history instead of rewriting the used factor.
- Draft documents do not move stock. Confirmed documents are not edited in place. Corrections are reversing documents, once only (`reversal_of_document_id` is unique).
- Trash (`trashed_at`): only `CANCELLED` or confirmed+reversed documents. Cancel draft first; do not trash `DRAFT` directly. Cancelled trash rows purge after 90 days when unconstrained; reversed docs are hide-only.
- Cost is weighted average (`InventoryMath`). A sale stores `cost_per_base_snapshot` / `cost_total_snapshot`. Do not recompute a confirmed sale from today's average.
- Document codes come from `document_sequences` inside the same transaction (`DocumentSequenceService`). Do not build codes from timestamps.

## Selling / purchase prices (tấn)

- Form danh mục chỉ nhập **Giá nhập / tấn** và **Giá bán / tấn**.
- `products.default_selling_price` = đ/kg (= đ/tấn ÷ 1000). `default_selling_price_ta` đồng bộ nội bộ (= đ/kg × 100), không hiện trên UI.
- Phiếu bán gợi ý đơn giá = `default_selling_price × factor_to_base` của đơn vị dòng.

## Purchase price over time and standard cost

- Purchase price changes by day, so it lives in `product_purchase_prices` (`effective_from`, `effective_to`), not in a single editable column. `products.reference_purchase_price` is only a mirror of today's effective price. Use `PurchasePriceService`; never update that column directly.
- A confirmed line snapshots `purchase_price_id`, `standard_cost_per_base_snapshot`, and `standard_cost_total_snapshot` from the price effective on the **document date**. Reports read those snapshots, never the current price row.
- Two cost layers must stay separate: actual cost (weighted average) and standard cost (price list for that day). `priceVariance = cogs − standardCogs`; positive means purchases cost more than the standard.
- Never rewrite a price history row that a document already used, and never insert a price dated before the earliest row. Changing the price opens a new row; several rows may share a day and the higher `id` wins.

## Finance

- Revenue, cash collected, and operating expenses are separate.
- Purchases are not period expenses. A purchase moves stock and cost. Cash leaving for that purchase is `PURCHASE_PAYMENT`, not an operating expense.
- Payment statuses on stock documents are only `UNPAID`, `PARTIAL`, `PAID`.
- A payment already linked to a source document (`sales_payments.financial_transaction_id`) must not get a second finance row.

## Attendance and reports

- Overtime counts only when `overtime_records.is_approved` is true.
- A closed payroll period does not change when employee, rate, or attendance rows change. Reopen writes audit: who (`reopened_by_admin_id`), when (`reopened_at`), and why (`reopen_reason`).
- Overnight shifts and rounding come from `attendance_policies` (`overnight_shift_allowed`, `rounding_minutes`). Do not hardcode one formula.
- Timezone is `Asia/Ho_Chi_Minh`. A report range is from the start of `from` until before the start of the day after `to`.

## API and UI

- Prefix is `/api/v1`. Responses are `ApiResponse`: `{ success, data, message, code, details }`. Return DTOs. Do not return JPA entities.
- Controllers stay thin. Services own transactions.
- UI copy is Vietnamese.
- Money is `DECIMAL` with `HALF_UP`. Quantity scale 6, money scale 4, cost scale 6 (`InventoryMath`). Do not use binary floating point for money or stock.

## Buttons (bắt buộc đọc trước khi thêm / sửa nút)

- Dùng `AppActionButton` từ `frontend/src/components/AppButtons.tsx` (hoặc `AppDeleteButton`). Icon bắt buộc từ `@ant-design/icons` — đã có trong project, không invent icon khác.
- Mỗi nút phải có: **nền đặc trưng theo vai trò** + **border** + **chữ trắng** + **icon**. Không để nút default trắng / chỉ viền đỏ Ant Design.
- Chọn `tone` đúng vai trò: `create` (Tạo/Thêm/Lưu chính), `edit` (Sửa), `cancel` (Hủy/Đóng/Quay lại), `delete` (Xóa/Hủy nháp), `confirm` (Xác nhận phiếu), `pay` (Thu/Chi tiền), `reverse` (Đảo phiếu), `print` (In), `sync` (Đồng bộ), `secondary` (Cấu hình/phụ).
- **Không hardcode màu nút.** Màu nền / viền / chữ từng tone nằm trong `frontend/src/features/theme/palette.ts` (`btn`) và tự đổi theo Light / Dark, đã kiểm tra tương phản AA.- Modal Ant Design: `okButtonProps` / `cancelButtonProps` set `style` từ `appButtonStyle(tone)` và gắn `icon` qua `okText`/`footer` custom nếu cần; ưu tiên `footer` tự dựng bằng `AppActionButton`.
- Khi thêm nút mới: luôn mở `AppButtons.tsx` và skill này trước, rồi mới code.

## Form spacing (bắt buộc khi code giao diện)

- Đọc thêm rule `.cursor/rules/ui-form-spacing.mdc`.
- Mỗi ô nhập / hàng fill phải có **khoảng cách dưới ≈ 1 dòng chiều cao ô đó** (thường ≥ 32px) để các hàng không dính nhau.
- Không set `margin-bottom: 0` hàng loạt trên form nhiều dòng nếu làm tiêu đề/ô hàng trên dính sát hàng dưới.

## Public landing page

- Exactly two public endpoints: `GET /api/v1/public/shop-info` and `POST /api/v1/public/leads`. Everything else stays behind the JWT filter. Do not widen `permitAll` for the management API.
- `/public/shop-info` may return only shop name, address, phone, active category names, and editable landing copy (`landing.*` keys in `app_settings`). Never return prices, stock, payroll, or backup config through a public endpoint.
- Landing section texts are edited via authenticated `GET/PUT /api/v1/landing-content`; public page always reads through `/public/shop-info`.
- Customer leads land in `customer_leads`. Reading them requires login. Bots must be met with the honeypot plus IP rate limit, and a honeypot hit must look like success to the caller.
- The public CORS list (`aluminum.cors.public-origins`) is registered only for `/api/v1/public/**`, so adding the landing domain never grants access to shop data.

## Khung trang luôn đầy màn hình (bắt buộc cho mọi giao diện)

- Khung (`Card`) chính của mỗi trang **luôn kéo dài hết chiều cao màn hình**, kể cả khi chỉ có 1 bản ghi hoặc không có bản ghi nào. Không để khung co lại theo số dòng.
- Bố cục trong khung: bộ lọc/tìm kiếm ở trên → bảng chiếm phần giữa → **phân trang dính đáy khung, căn giữa**.
- Đã làm sẵn trong `frontend/src/index.css`: thẻ duy nhất của trang (`.app-content > .ant-card:only-child`) hoặc thẻ gắn class `app-fill-card` tự `flex: 1` + `min-height: calc(100vh - 64px - 32px)`. Bảng phải là con trực tiếp của phần thân thẻ để được kéo giãn.
- Trang mới: chỉ để **một `Card` gốc** (hoặc thêm `className="app-fill-card"`). Không đặt `minHeight`/`height` rải rác trong từng component, không bọc thêm `div` giữa `Card` và `Table`.
- Trang có nhiều thẻ con (Tổng quan, Báo cáo, Chi tiết khách) chỉ cần các thẻ nhỏ; thẻ bảng chính vẫn theo quy tắc trên.

## Phân trang (bắt buộc)

- Dùng `PagedTable` (`frontend/src/components/PagedTable.tsx`) thay cho `Table` khi dữ liệu có thể nhiều dòng. **Tối đa 30 bản ghi mỗi trang** (`PAGE_SIZE`). Không dùng `pagination={{ pageSize: ... }}` của Ant Design.
- Phân trang **căn giữa**, có nút về trang đầu «, trang trước ‹, trang sau ›, đến trang cuối ». Số trang hiển thị: trang đang chọn + 2 trang kế, dấu "…", rồi 2 trang cuối (ví dụ chọn 3 → `3 4 5 … 89 90`); gần cuối thì hiện liền 5 trang cuối.
- Dòng tóm tắt dưới nút: "Trang X / N · Tổng … bản ghi · Tối đa 30 bản ghi mỗi trang". Nhãn tiếng Việt đủ từ.

## Theme Sáng / Tối (bắt buộc)

- Toàn bộ màu là design token trong `frontend/src/features/theme/palette.ts` (Light = Blue Gray, Dark = Deep Navy). CSS dùng `var(--color-*)`; biểu đồ dùng `useThemeColors()`. **Không hardcode màu** (`#fff`, `rgba(0,0,0,.45)`, `#8c8c8c`…) trong component; dùng `var(--color-text-secondary)`, `var(--color-success)`, `var(--color-danger)`, `var(--color-warning)`…
- Chữ phụ trong Dark là `#CBD5E1`; không dùng xám tối kiểu `#64748B` cho nội dung quan trọng. Cặp chữ/nền phải đạt WCAG AA (≥ 4,5:1; chữ lớn ≥ 3:1).
- Lưu lựa chọn ở localStorage (`aluminum-theme`), lần đầu theo hệ điều hành; chuyển theme 180ms, tôn trọng `prefers-reduced-motion`.