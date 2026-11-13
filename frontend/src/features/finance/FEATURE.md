# Chức năng finance

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

### `/thu-chi` — Sổ thu chi

| | |
|--|--|
| File | `features/finance/FinancePage.tsx` |
| Ai dùng | Mọi role; Admin chỉ xem + chọn scope |
| API | finance transactions / summary |

**Chức năng**
- Khoản thưởng lễ đã nhận hiện là **Chi thưởng lễ**. Sửa trạng thái nhận trong Lịch nghỉ lễ để cập nhật phiếu chi.
- KPI: thực thu, chi hoạt động & lương, chi nhập hàng, chênh lệch dòng tiền
- Admin: scope một shop hoặc **cả hai**; hiện thu/chi **nội bộ** (không cộng số ngoài)
- Lập phiếu chi thủ công; hủy phiếu. Khoản thu đi từ phiếu bán hoặc bán phế liệu.
- Dropdown **Khoản chi** giống nhau ở hai sổ: Tiền điện; Tiền ăn mỗi ngày; Tiền nước + bánh kẹo giữa giờ; Tiền vật tư máy móc; Tiền sửa chữa hoặc thay thế bảo dưỡng. Loại đã chọn hiện ở danh sách/chi tiết và trong sổ cuối năm. Bốn mục đầu ghi vào chi phí hoạt động, mục sửa chữa ghi vào bảo trì; ghi chú được nối sau tên khoản chi.
- Chọn năm theo kỳ bắt đầu/chốt đã lưu, hiện hai mốc ngày; lọc loại giao dịch; tách biệt doanh thu ≠ tiền thu ≠ chi phí; tiền nhập chưa bán ≠ chi phí kỳ

### `/bao-cao` — Báo cáo

| | |
|--|--|
| File | `features/finance/ReportsPage.tsx` |
| Ai dùng | Mọi role (tab lương ẩn Phân phối) |
| API | reports profit / price-variance / sales-by-product / inventory / payroll |

**Tabs**
1. **Doanh thu & lợi nhuận** — doanh thu, COGS, lãi gộp, LN thuần dự kiến, tiền thu, chưa thu, nhập kỳ, lãi theo giá chuẩn, chênh lệch giá nhập; bảng doanh thu theo ngày
2. **Chênh lệch giá nhập** — thực tế vs chuẩn theo SP/phiếu
3. **Bán theo sản phẩm**
4. **Tồn / biến động kho**
5. **Hao hụt Phân phối** — tổng kg hao hụt/thừa và phiếu điều chỉnh từ kiểm kê trong khoảng ngày; dẫn sang Tồn kho để nhập cân thực tế
6. **Tiền công** — Admin + Xưởng; theo năm

Admin chọn sổ một shop / cả hai; xuất CSV theo tab được hỗ trợ. Tab doanh thu và lợi nhuận của Xưởng hiển thị số liệu quyết toán theo năm, gồm trạng thái kiểm kê, doanh thu, tiền thực thu, chi phí và lãi quản trị. Chế độ gộp dẫn đến Tổng hợp hai bên.

Ô **Năm xem báo cáo** lấy ngày đầu/cuối kỳ đã chốt hoặc kỳ dự kiến, đồng thời chọn cùng năm cho quyết toán Xưởng và tiền công.

## Bổ sung theo code hiện tại

Phân phối ghi nước, bánh cho đội giao nhôm, bảo trì trong chi phí hoạt động kèm ghi chú; không có lựa chọn chi lương. Admin có thể xem báo cáo gộp theo scope.

Báo cáo Phân phối hiển thị doanh thu, thực thu và công nợ.

Tab Doanh thu & Công nợ còn hiển thị lãi năm Phân phối từ bản chốt theo khoảng ngày đã chọn. Trước khi kiểm kê và chốt, báo chưa có lãi năm chính xác; lãi vật tư hóa đơn/tháng ở tab riêng chỉ là ước tính tham khảo.

Phiếu chi Phân phối có nhóm bảo trì và người chi. Ghi chú phiếu chi là tùy chọn ở cả hai bên. Phân phối ẩn tab bán theo sản phẩm.

### Lãi gộp vật tư tham khảo theo hóa đơn / tháng

Tab **Lãi gộp hóa đơn / tháng** có doanh thu, giá vật tư tham khảo theo ngày giao và lãi vật tư ước tính cho từng hóa đơn, kèm tổng theo tháng. Không trừ tiền công, phụ kiện, hao hụt và chi phí hoạt động; **không** dùng làm lãi đã quyết toán. Nếu thiếu giá vốn tham khảo, số lãi để trống thay vì giả định giá vốn bằng 0. Dữ liệu lấy từ `GET /api/v1/reports/invoice-margins`, chỉ hiện một sổ (không gộp cả hai).
