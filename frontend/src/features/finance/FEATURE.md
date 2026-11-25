# Chức năng finance

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

Khung Báo cáo giãn theo nội dung; bảng nhiều cột, gồm bảng tiền công, cuộn ngang trong bảng ở cửa sổ hẹp. Modal Thu chi dài cuộn thân và giữ footer nhìn thấy.

Tab **Công nợ chuyển kỳ** ở Báo cáo hiển thị cả kỳ năm đã chọn của cửa hàng. Hai bảng nợ khách và nợ nhà cung cấp ghi số phiếu, nợ đầu kỳ, số thu hoặc trả trong kỳ, nợ cũ cuối kỳ và ngày giờ từng lần thanh toán.

Menu **Công nợ** mở trang riêng `/cong-no` cho Xưởng và Phân phối. Trang dùng kỳ năm đang chọn ở Tổng quan và hiển thị toàn bộ công nợ chuyển kỳ của cửa hàng.
Form thanh toán công nợ dùng ngày lập phiếu gốc và khoảng kỳ đang mở để giới hạn ngày thu hoặc trả. Với phiếu lập trước khoảng đó, form gợi ý ngày đầu khoảng đang mở.

### `/thu-chi` — Sổ thu chi

| | |
|--|--|
| File | `features/finance/FinancePage.tsx` |
| Ai dùng | Mọi role; Admin chỉ xem + chọn scope |
| API | finance transactions / summary |

**Chức năng**
- Khoản thưởng lễ đã nhận hiện là **Chi thưởng lễ**. Sửa trạng thái nhận trong Lịch nghỉ lễ để cập nhật phiếu chi.
- KPI: thực thu, chi hoạt động và lương, chi nhập hàng, chênh lệch tiền thu và chi. Các thẻ trong cùng hàng có cùng chiều cao. Thẻ chênh lệch ghi rõ thu nhiều hơn chi, chi nhiều hơn thu hoặc hai khoản bằng nhau.
- Admin không truy cập Thu chi hoặc Báo cáo.
- Lập và hủy phiếu chi thủ công. Chi tiết phiếu chi tự lập còn hiệu lực có nút Sửa phiếu để đổi ngày, nhóm, số tiền, người chi và ghi chú. Phiếu công thợ và giao dịch liên kết dùng luồng gốc. Phiếu thuộc kỳ đã khóa hoặc tháng đã chốt sổ không sửa được.
- Dropdown **Khoản chi** giống nhau ở hai sổ: Tiền điện; Tiền ăn mỗi ngày; Tiền nước + bánh kẹo giữa giờ; Tiền vật tư máy móc; Tiền sửa chữa hoặc thay thế bảo dưỡng. Loại đã chọn hiện ở danh sách/chi tiết và trong sổ cuối năm. Bốn mục đầu ghi vào chi phí hoạt động, mục sửa chữa ghi vào bảo trì; ghi chú được nối sau tên khoản chi.
- Chọn năm theo kỳ bắt đầu/chốt đã lưu, hiện hai mốc ngày; lọc loại giao dịch; tách biệt doanh thu ≠ tiền thu ≠ chi phí; tiền nhập chưa bán ≠ chi phí kỳ
- Kỳ chưa lưu ngày bắt đầu không tải số Thu chi theo năm; kỳ đang làm việc lấy đến ngày hiện tại. Bảng Tiền công trong Báo cáo lấy các tháng có ngày cuối tháng thuộc khoảng kỳ đã chọn, kể cả khi qua năm dương lịch.
- Thu chi và Báo cáo hiển thị tên kỳ tùy ý đã lưu trên Tổng quan cạnh khoảng ngày.
- Khi Tổng quan chọn tháng, Thu chi và Báo cáo dùng phần giao của tháng với kỳ năm; chọn Cả kỳ để xem lại toàn kỳ. Phiếu chi thủ công thuộc tháng đã chốt bị backend từ chối sửa/hủy.

### `/bao-cao` — Báo cáo

Phân phối hiển thị năm thẻ tổng hợp doanh thu và công nợ theo bố cục ba thẻ trên, hai thẻ dưới trên màn hình đủ rộng. Hai hàng trải hết cùng chiều rộng.

| | |
|--|--|
| File | `features/finance/ReportsPage.tsx` |
| Ai dùng | Mọi role (tab lương ẩn Phân phối) |
| API | reports profit / price-variance / sales-by-product / inventory / payroll |

**Tabs**
1. **Doanh thu & lợi nhuận** — doanh thu, COGS, lãi gộp, LN thuần dự kiến, đã thu từ các hóa đơn lập trong kỳ (cộng cả lần trả sau ngày lập phiếu), chưa thu, nhập kỳ, lãi theo giá chuẩn, chênh lệch giá nhập; bảng theo ngày lập phiếu xếp ngày mới nhất trước. Cột số phiếu bán đếm cả phiếu chuyển nội bộ; doanh thu và đã thu loại chuyển nội bộ. Chi tiết phiếu nội bộ vẫn có trong tab lãi gộp hóa đơn với lãi bằng 0.
2. **Chênh lệch giá nhập** — thực tế vs chuẩn theo SP/phiếu
3. **Bán theo sản phẩm**
4. **Tồn / biến động kho**
5. **Hao hụt Phân phối** — tổng hao hụt và thừa kiểm kê hiển thị theo tấn trong hai thẻ cùng kích thước, kèm phiếu điều chỉnh trong khoảng ngày và liên kết đến Tồn kho để nhập số cân thực tế.
6. **Tiền công** — Admin + Xưởng; theo năm

Xưởng và Phân phối xuất CSV theo tab được hỗ trợ. Tab doanh thu và lợi nhuận của Xưởng hiển thị số liệu quyết toán theo năm, gồm trạng thái kiểm kê, doanh thu, tiền thực thu, chi phí và lãi quản trị.

Ô **Năm xem báo cáo** lấy ngày đầu/cuối kỳ đã chốt hoặc kỳ dự kiến, đồng thời chọn cùng năm cho quyết toán Xưởng và tiền công.

## Bổ sung theo code hiện tại

Phân phối ghi nước, bánh cho đội giao nhôm, bảo trì trong chi phí hoạt động kèm ghi chú; không có lựa chọn chi lương.

Báo cáo Phân phối hiển thị doanh thu, thực thu và công nợ.

Tab Doanh thu & Công nợ còn hiển thị lãi năm Phân phối từ bản chốt theo khoảng ngày đã chọn. Trước khi kiểm kê và chốt, báo chưa có lãi năm chính xác; lãi vật tư hóa đơn/tháng ở tab riêng chỉ là ước tính tham khảo. Sổ thu chi gom khoản thu bán hàng và chi trả hàng nhập theo **ngày phiếu kho gốc** trong kỳ (kể cả thanh toán được ghi sau kỳ), còn chi phí độc lập theo ngày chi. Cột ngày trên từng khoản giữ ngày thực thu/chi để đối chiếu; không thay đổi ngày giao dịch trong cơ sở dữ liệu.

Phiếu chi Phân phối có nhóm bảo trì và người chi. Ghi chú phiếu chi là tùy chọn ở cả hai bên. Phân phối ẩn tab bán theo sản phẩm.

### Lãi gộp vật tư tham khảo theo hóa đơn / tháng

Tab **Lãi gộp hóa đơn / tháng** có doanh thu, giá vật tư theo giá nhập chuẩn tại ngày giao và lãi vật tư ước tính cho từng hóa đơn. Giá này không phải giá vốn thực tế của lô hàng. Không trừ tiền công, phụ kiện, hao hụt và chi phí hoạt động; **không** dùng làm lãi đã quyết toán. Nếu thiếu giá nhập chuẩn ở phiếu khách ngoài, số lãi để trống thay vì giả định giá vốn bằng 0. Phiếu chuyển nội bộ vẫn hiện trong bảng chi tiết, đánh dấu Nội bộ và lãi 0; số phiếu tháng tính cả nội bộ nhưng doanh thu/giá vật tư/lãi tháng chỉ cộng phiếu khách ngoài. Dữ liệu lấy từ `GET /api/v1/reports/invoice-margins`, chỉ hiện một sổ (không gộp cả hai).

## Cập nhật rà soát 19/11/2026

Xem `docs/audit-progress-20261119.md` để biết thay đổi và giới hạn kiểm thử. Sổ tháng định giá tồn theo lớp nhập mới nhất, không FIFO; tiền công/phụ cấp/khấu trừ theo tháng. Ứng lương mới có lịch sử ngày, tiền và ghi chú, ghi chi thực tế và trừ thực lĩnh; số ứng tổng cũ không được tự gán ngày. Báo cáo thiếu giá vốn không hiển thị lợi nhuận giả bằng cách coi giá vốn là 0. Các bài MySQL phải dùng cấu hình thử riêng, không thay đổi database thật.


## Chi sổ giấy và lãi tháng

Thu chi có lựa chọn Tổng tháng từ sổ giấy. Chọn tháng, nhóm chi, tiền và ghi chú. Xưởng có thêm khoản Công thợ đã trả đủ từ sổ giấy: nhập tên, tổng công và tổng ứng để hiển thị phần trả cuối tháng. Dùng khi đã trả đủ và chưa tính trong kỳ công trên app. Đây là khoản chi tổng hợp, không thay bảng công hoặc theo dõi lương chưa trả.

Tab Lãi gộp hóa đơn / tháng có bảng Lãi tháng sau chi phí (tạm tính): lấy lãi hóa đơn theo ngày bán trừ chi hoạt động và công thợ. Ưu tiên giá vốn nhập tay, sau đó tra giá tham chiếu. Thiếu giá vốn thì để trống lãi, không giả định bằng 0.
Cột Khách / nơi nhận chỉ hiện nhãn Nội bộ cho phiếu chuyển nội bộ.
