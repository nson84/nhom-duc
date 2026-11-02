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

Repo root is this project. One machine, one Admin account. Phase 1 (auth, settings, dashboard, V1 schema, domain math) is already in place. Later phases are products, stock, attendance, finance, and backup/packaging. Extend the layout below. Do not add multi-user, sync, debt, fingerprint, bank, e-invoice, or multi-branch.

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
- Chọn `tone` đúng vai trò (không tô hết một màu Sienna):
  - `create` — Tạo / Thêm / Primary lưu chính → xanh biển tối `#2f6f8f`
  - `edit` — Sửa → vàng tối `#B8860B`
  - `cancel` — Hủy / Đóng / Quay lại → Sienna `#8B4513`
  - `delete` — Xóa / Hủy nháp → nâu đỏ `#A52A2A`
  - `confirm` — Xác nhận phiếu → xanh rừng `#1F6B4A`
  - `pay` — Thu / Chi tiền → teal `#0F766E`
  - `reverse` — Đảo phiếu → cam đất `#9A3412`
  - `print` — In → xám thrép `#334155`
  - `secondary` — Cấu hình / lịch sử / phụ → xám `#475569`
- Modal Ant Design: `okButtonProps` / `cancelButtonProps` set `style` từ `appButtonStyle(tone)` và gắn `icon` qua `okText`/`footer` custom nếu cần; ưu tiên `footer` tự dựng bằng `AppActionButton`.
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
