---
name: flow-coding-with-ai
description: >-
  Flow Coding with AI — quy trình phát triển 4 phase (docs → skeleton → từng
  feature → hoàn thiện) cho backend, frontend hoặc fullstack. Dùng khi bắt đầu
  feature mới, dựng nền, viết/ cập nhật docs tiến độ, API contract, PAGES,
  DATABASE, chia session BE/FE, hoặc khi user nhắc Flow Coding / flow-dev /
  1 feature = 1 session.
---

# Flow Coding with AI

Skill này chứa bộ tài liệu **Flow Cho Dev**. Repo **nhom-duc** là **fullstack** (React + Spring Boot) → ưu tiên đọc overview rồi **fullstack**.

## Bắt buộc làm ngay khi skill được gọi

1. Đọc `references/flow-dev-overview.md` (nguyên tắc chung + chọn file).
2. Đọc file chi tiết đúng ngữ cảnh:
   - Fullstack (mặc định dự án này) → `references/flow-dev-fullstack.md`
   - Chỉ backend → `references/flow-dev-backend.md`
   - Chỉ frontend → `references/flow-dev-frontend.md`
3. Áp dụng **3 quy tắc vàng**: docs trước code sau; **1 feature = 1 session**; test xong mới sang feature tiếp.
4. Không nhồi BE + FE vào một session khi làm feature mới — tách sub-session hoặc session riêng.

## Ánh xạ docs Flow → docs hiện có trong repo

| Flow gọi tên | Trong repo nhom-duc |
|---|---|
| API_CONTRACT / API_SPEC | `docs/api-specification.md` (+ cập nhật khi đổi API) |
| DATABASE | `docs/database-erd.md` |
| PAGES | `docs/feature.md` (route + chức năng từng màn) |
| PROGRESS | Tạo/cập nhật `docs/PROGRESS.md` nếu chưa có khi làm feature mới |
| Project rules | `.cursor/rules/*`, `.cursor/skills/aluminum-manager/SKILL.md` |
| Quy tắc nghiệp vụ khóa | `docs/requirements.md`, `docs/feature.md` |

Khi Flow bảo “update contract → sửa BE → sửa FE”: với repo này, cập nhật `docs/api-specification.md` và mục màn trong `docs/feature.md` trước khi đổi code.

## Phase ngắn gọn (fullstack)

```
Phase 1  Chuẩn bị     docs + rules (đã có một phần — bổ sung khi thiếu)
Phase 2  Nền móng     chạy được FE↔BE (CORS/local 5173↔8080) trước feature mới
Phase 3  Từng feature 1 nghiệp vụ; BE rồi FE (hoặc 2 sub-session); test + commit
Phase 4  Hoàn thiện   regression, security, build
```

### Vòng lặp mỗi feature (Phase 3)

1. Mở session → đọc rules + `docs/feature.md` mục liên quan.
2. Prompt tạo feature (trỏ feature cũ để follow style).
3. Understand code → implement.
4. Test (Swagger/API + UI loading/error/empty nếu FE).
5. Automated test BE khi đụng tính toán/quyền.
6. Update `docs/feature.md` (+ API/PROGRESS) → commit theo loại `feat(be):` / `feat(fe):`.
7. Đóng session.

## Tài liệu gốc (đọc đầy đủ)

- `references/flow-dev-overview.md`
- `references/flow-dev-fullstack.md`
- `references/flow-dev-backend.md`
- `references/flow-dev-frontend.md`

Nội dung các file trên giữ nguyên từ bộ **Flow Coding with AI**; khi conflict với skill `aluminum-manager` hoặc `docs/feature.md` về nghiệp vụ nhôm, **ưu tiên aluminum-manager + feature.md** cho domain, ưu tiên Flow cho **cách chia session / quy trình**.
