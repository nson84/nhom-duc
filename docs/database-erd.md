# Sơ đồ dữ liệu

Migration phát hành đầu là `backend/src/main/resources/db/migration/V1__init_schema.sql`. File này đã gồm cấp số phiếu, một đơn vị cơ sở, chống ghi trùng, đảo phiếu một lần, cấu hình chấm công và các chỉ mục báo cáo.

```mermaid
erDiagram
    product_categories ||--o{ products : phan_loai
    products ||--o{ product_units : quy_doi
    products ||--o{ product_purchase_prices : lich_su_gia_nhap
    product_purchase_prices ||--o{ stock_document_items : chuan
    customer_leads }o--o| admin_users : nguoi_goi
    products ||--|| stock_balances : ton
    products ||--o{ stock_document_items : dong
    stock_documents ||--o{ stock_document_items : gom
    stock_documents ||--o{ stock_movements : bien_dong
    stock_document_items ||--|| stock_movements : mot_lan
    stock_documents ||--o| stock_documents : dao
    stock_documents ||--o{ sales_payments : thu_tien
    sales_payments |o--o| financial_transactions : chung_tu
    employees ||--o{ employee_pay_rates : muc_cong
    employees ||--o{ attendance_records : cham_cong
    attendance_records ||--o{ overtime_records : tang_ca
    payroll_periods ||--o{ payroll_details : chot
    employees ||--o{ payroll_details : luong
    admin_users ||--o{ audit_logs : nhat_ky
```

Quy tắc không thể hiện hết bằng khóa ngoại, ví dụ phiếu bán phải có người nhận, được kiểm tra ở tầng nghiệp vụ khi làm module kho.

`product_purchase_prices` giữ lịch sử giá nhập theo khoảng hiệu lực (`effective_from`, `effective_to`). Nhiều mốc có thể cùng một ngày khi Admin đổi giá trong ngày mà mốc cũ đã dùng trên phiếu; mốc có `id` lớn hơn là mốc được áp dụng. Mỗi dòng phiếu trỏ `purchase_price_id` và lưu `standard_cost_per_base_snapshot` / `standard_cost_total_snapshot`, nên sửa giá về sau không làm đổi chi phí chuẩn của phiếu đã xác nhận.

`customer_leads` nhận thông tin khách điền trên landing page công khai. Bảng này tách hẳn khỏi dữ liệu kinh doanh: chỉ Admin đăng nhập mới đọc được, và không có khóa ngoại nào từ bảng nghiệp vụ trỏ sang.
