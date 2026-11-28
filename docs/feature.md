# Danh mục chức năng

Khung giao diện dùng chung và phiên bản đang chạy: [layout](../frontend/src/components/layout/FEATURE.md). Phục hồi/quay lại: [runbook](recovery-runbook.md). Trạng thái nghiệm thu mới nhất: [20/11/2026](completion-20261120.md).

Mỗi thư mục chức năng có `FEATURE.md` mô tả hành vi hiện tại. Trước khi sửa code, đọc tài liệu của mọi frontend/backend liên quan và [requirements.md](requirements.md); sau khi sửa, cập nhật tài liệu tương ứng trong cùng thay đổi. Các quy tắc thao tác nằm trong [AGENTS.md](../AGENTS.md).

| Vai trò | Phạm vi hiện tại |
| --- | --- |
| Admin | Sao lưu, Cài đặt và tài khoản QR nhận tiền; không truy cập nghiệp vụ, báo cáo gộp hoặc Sổ cuối năm. |
| Phân phối | Vận hành sổ phân phối; không truy cập nhân viên, chấm công hay tiền công. |
| Xưởng | Vận hành sổ xưởng, quyết toán và nhân sự xưởng. |

Router nằm tại `frontend/src/app/router.tsx`, menu và bộ chọn sổ tại `frontend/src/components/layout/AppShell.tsx`, kiểm tra quyền backend tại `backend/src/main/java/com/aluminum/manager/security/RoleAccessFilter.java`. Dưới 1200 px, AppShell dùng menu dạng ngăn kéo; có tùy chọn giao diện sáng/tối và đăng xuất. Kết quả rà soát viewport nằm ở [responsive-audit.md](responsive-audit.md).

| Màn hình | Tài liệu chức năng |
| --- | --- |
| Đăng nhập, đổi mật khẩu | [auth](../frontend/src/features/auth/FEATURE.md) |
| Tổng quan | [dashboard](../frontend/src/features/dashboard/FEATURE.md) |
| Giới thiệu, khách để lại thông tin | [landing](../frontend/src/features/landing/FEATURE.md) |
| Sản phẩm, loại nhôm, giá | [products](../frontend/src/features/products/FEATURE.md) |
| Phiếu nhập, bán và tồn kho | [stock](../frontend/src/features/stock/FEATURE.md) |
| Khách mua, công nợ | [customers](../frontend/src/features/customers/FEATURE.md) |
| Quyết toán xưởng, phế liệu | [factory](../frontend/src/features/factory/FEATURE.md) |
| Nhân viên, chấm công, tiền công xưởng | [attendance](../frontend/src/features/attendance/FEATURE.md) |
| Thu chi, báo cáo | [finance](../frontend/src/features/finance/FEATURE.md) |
| Công nợ chuyển kỳ | [finance](../frontend/src/features/finance/FEATURE.md) |
| Sổ cuối năm để in PDF | [annual-book](../frontend/src/features/annual-book/FEATURE.md) |
| Quyền Admin | [admin](../frontend/src/features/admin/FEATURE.md) |
| Sao lưu, cài đặt, tài khoản QR | [backup](../frontend/src/features/backup/FEATURE.md), [settings](../frontend/src/features/settings/FEATURE.md) |

## Theo màn hình / frontend

- [auth](../frontend/src/features/auth/FEATURE.md)
- [dashboard](../frontend/src/features/dashboard/FEATURE.md)
- [landing](../frontend/src/features/landing/FEATURE.md)
- [products](../frontend/src/features/products/FEATURE.md)
- [stock](../frontend/src/features/stock/FEATURE.md)
- [customers](../frontend/src/features/customers/FEATURE.md)
- [factory](../frontend/src/features/factory/FEATURE.md)
- [attendance](../frontend/src/features/attendance/FEATURE.md)
- [finance](../frontend/src/features/finance/FEATURE.md)
- [annual-book](../frontend/src/features/annual-book/FEATURE.md)
- [backup](../frontend/src/features/backup/FEATURE.md)
- [settings](../frontend/src/features/settings/FEATURE.md)
- [admin](../frontend/src/features/admin/FEATURE.md)
- [shop](../frontend/src/features/shop/FEATURE.md)
- [system](../frontend/src/features/system/FEATURE.md)
- [theme](../frontend/src/features/theme/FEATURE.md)
- [modules](../frontend/src/features/modules/FEATURE.md)

## Theo module backend

- [Nhân sự, điểm danh, tăng ca và lương của xưởng](../backend/src/main/java/com/aluminum/manager/attendance/FEATURE.md)
- [Đăng nhập và phiên](../backend/src/main/java/com/aluminum/manager/auth/FEATURE.md)
- [Sao lưu và phục hồi](../backend/src/main/java/com/aluminum/manager/backup/FEATURE.md)
- [Sản phẩm, loại nhôm và giá](../backend/src/main/java/com/aluminum/manager/catalog/FEATURE.md)
- [Khách mua và công nợ](../backend/src/main/java/com/aluminum/manager/customer/FEATURE.md)
- [Tổng quan](../backend/src/main/java/com/aluminum/manager/dashboard/FEATURE.md)
- [Quyết toán xưởng và tổng hợp Admin](../backend/src/main/java/com/aluminum/manager/factory/FEATURE.md)
- [Thu chi và báo cáo](../backend/src/main/java/com/aluminum/manager/finance/FEATURE.md)
- [Phiếu nhập, bán và kho](../backend/src/main/java/com/aluminum/manager/inventory/FEATURE.md)
- [Cài đặt, tài khoản QR và sẵn sàng](../backend/src/main/java/com/aluminum/manager/system/FEATURE.md)
- [Landing và khách liên hệ](../backend/src/main/java/com/aluminum/manager/web/FEATURE.md)
- [Phân quyền và xác thực](../backend/src/main/java/com/aluminum/manager/security/FEATURE.md)
- [Ngữ cảnh và phạm vi hai cửa hàng](../backend/src/main/java/com/aluminum/manager/shop/FEATURE.md)
- [Thành phần dùng chung](../backend/src/main/java/com/aluminum/manager/common/FEATURE.md)
- [Cấu hình khởi tạo](../backend/src/main/java/com/aluminum/manager/config/FEATURE.md)

Các tài liệu xuyên chức năng: [API](api-specification.md), [cơ sở dữ liệu](database-erd.md), [kiểm thử](test-plan.md), [hướng dẫn chạy](../README.md).

Mục Chốt sổ tháng (/chot-so-thang) thuộc dashboard cho Xưởng và Phân phối.

Tồn đầu kỳ theo từng loại: OpeningInventory dùng chung ở Nhập kho, Tồn kho, Chốt sổ tháng và Quyết toán Xưởng; API InventoryCarryoverController/Service thuộc dashboard. Tự xem nguồn chốt liền trước, không có thao tác nhập lại hàng.


Nhóm Kiểm kê & chốt sổ có Chốt sổ tháng (`/chot-so-thang`) và Chốt kỳ (`/chot-nam`) cho Xưởng và Phân phối. Chọn kỳ, chọn tháng và khóa tháng tại Tổng quan. Bản in Sổ cuối kỳ ở `/so-cuoi-nam`.
