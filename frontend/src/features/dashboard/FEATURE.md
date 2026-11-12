# Chức năng dashboard

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

### `/` — Tổng quan (Dashboard)

| | |
|--|--|
| File | `features/dashboard/DashboardPage.tsx` |
| Ai dùng | Mọi role (số theo shop context) |
| API | `GET /api/v1/dashboard/summary`, `.../charts`, backup status |

**Chức năng**
- Chọn tên năm để xem đúng kỳ của năm đó; ngày bắt đầu/kết thúc hiện cạnh bộ lọc.
- KPI: doanh thu, tiền thực thu, lãi gộp, chi phí…
- Biểu đồ cột theo ngày (lọc ngày có phát sinh / tất cả)
- Biểu đồ tròn cấu trúc (giá vốn / lãi / chi phí / chưa thu)
- Cảnh báo nếu lần sao lưu gần nhất thất bại

## Bổ sung theo code hiện tại

Xưởng ẩn giá vốn và lãi ngày, nhưng Tổng quan hiện lãi năm ước tính khi đã có kiểm kê; nhãn cho biết năm chưa chốt. Phân phối không hiện tiền công.

Phân phối xem tổng chi, nợ khách và nợ nhà cung cấp. Sau khi chốt, hiển thị lãi và lượng, giá trị tồn cuối kỳ. Định giá tồn dùng phiếu nhập mới nhất trước.

Phân phối chọn năm và khoảng ngày riêng để chốt, rồi bấm **Chốt năm** để lưu bản số dư: kg và giá trị tồn, nợ khách, nợ nhà cung cấp, lãi. Có thể chọn mốc Tết âm lịch; kỳ kế tiếp bắt đầu ngay ngày sau kỳ trước. Bộ lọc năm xem tự lấy mốc đã chốt, không cần nhập lại ngày.
