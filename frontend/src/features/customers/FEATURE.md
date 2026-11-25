# Chức năng customers

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

Bảng khách, hóa đơn và nhắc nợ cuộn ngang bên trong bảng khi cửa sổ hẹp; các modal chi tiết cuộn nội dung theo chiều cao viewport.
Các thẻ tổng hợp trên danh sách khách dùng nhãn ngắn để các số thẳng hàng. Thẻ Đã thu đến nay tính trên các hóa đơn đang lọc.
Khung thông tin khách đặt nhãn, tổng khối lượng và số kg theo loại cùng hàng khi đủ chỗ. Chi tiết theo loại tự xuống hàng khi khung hẹp.
Trong bản in lịch sử mua hàng, ngày trả, số tiền và nhãn thanh toán nằm ở các cột thẳng hàng. Nhãn tiền mặt màu xanh lá, chuyển khoản màu xanh dương.
Tại chi tiết khách, tài khoản vận hành của sổ có thể bấm Sửa thông tin để cập nhật tên, số điện thoại, ghi chú và trạng thái khách. Thông tin hiển thị cập nhật ngay sau khi lưu.

### `/khach-mua` — Khách mua (công nợ nhẹ)

Phiếu xuất nội bộ Phân phối → Xưởng vẫn cộng kg đã giao cho khách/nơi nhận nội bộ, nhưng không cộng giá chuyển vào tiền hàng phải thu, tiền đã thu hoặc nợ khách. Danh sách và chi tiết gắn nhãn Nội bộ; giá trị chuyển vẫn xem được trong chi tiết phiếu, tách khỏi công nợ khách.

| | |
|--|--|
| File | `features/customers/CustomersPage.tsx`, `CustomerDetailPage.tsx`, `debt-print.ts` |
| Ai dùng | PP + Xưởng ghi/xem shop mình; **Admin chỉ đọc, xem gộp** cả hai shop |
| API | `/customers`, reminders, `stock-documents/{id}/customer`, payments; settings (đọc TK ngân hàng khi in QR) |

**Chức năng**
- Bốn ô tổng kg đã giao, tiền hàng phải thu, tiền đã thu từ các hóa đơn đã lọc tính đến nay và nợ còn lại cộng theo danh sách khách và khoảng ngày bán đang lọc; bảng khách hiện tiền hàng, tiền thu, kg giao và nợ. Tiền thu tính từ các hóa đơn thuộc khoảng ngày bán đã lọc, kể cả lần thanh toán sau khoảng đó.
- Tổng kg đã giao ở danh sách khách và chi tiết từng khách hiển thị thêm số kg theo từng loại, cùng phạm vi khách/ngày đang xem.
- Danh sách và chi tiết khách mặc định theo kỳ năm đang chọn ở Tổng quan; nếu đã chọn tháng thì chỉ lấy các phiếu trong phần giao của tháng và kỳ năm. Tổng tiền thu gắn với các phiếu đang lọc, kể cả lần thanh toán ghi sau ngày bán.
- **In hóa đơn đã lọc** dùng mẫu gọn như Sổ cuối năm: bảng hàng năm cột, từng lần trả có ngày và nhãn hình thức, dòng còn nợ. Tổng kết các phiếu đang lọc ghi tổng khối lượng và từng loại theo tấn, tổng tiền các phiếu, đã trả và còn nợ. Phiếu nội bộ chỉ cộng khối lượng.
- Danh sách khách theo shop (Admin: gộp + cột cửa hàng); badge còn nợ; banner nhắc đến hạn
- Chi tiết: kg đã giao / tiền hàng phải thu / đã thu từ các hóa đơn đã lọc theo ngày bán (tính đến nay) / còn nợ; bảng hóa đơn mặc định hiện tất cả, có thể lọc còn nợ hoặc trạng thái thanh toán. Bấm một dòng để xem chi tiết: sản phẩm, loại nhôm, số lượng, đơn giá, thành tiền, người xuất, người nhận, nhãn thanh toán. Thống kê năm Xưởng mặc định chọn năm của phiếu mua gần nhất. Lịch nhắc nợ
- **Trả nợ** từng phiếu: trả hết hoặc một phần, chọn **Hải**, **Thuấn** hoặc nhập tên người thu khác (không tự điền tên tài khoản) → **popup xác nhận** (số tiền, hình thức, người thu, còn phải thu sau lần này) rồi mới ghi `sales_payments`
- **In danh sách nợ** (nút trên chi tiết khách): xem trước PDF; mỗi hóa đơn còn nợ (cũ → mới) in sản phẩm, loại nhôm, số lượng, đơn giá, thành tiền, người xuất, người nhận, và **nhãn thanh toán** (chưa thanh toán / một phần / đã thanh toán, kèm tiền mặt hoặc chuyển khoản). Dòng đã thu là bảng thẳng cột: ngày giờ, số tiền, hình thức thanh toán, người thu (tên nhập lúc ghi thu). Không in số phiếu. Cuối trang là tổng còn nợ và bằng chữ
  - Tùy chọn **Gắn mã QR VietQR** (số tiền = tổng còn nợ; nội dung dạng `TenKhach tra 7500000 18h05 05-11-2026`, không dấu, ≤50 ký tự)
  - Cần khai báo TK nhận ở `/cai-dat`; quét bằng app ngân hàng điền sẵn số tiền + nội dung
  - **In / Tải PDF** → chọn máy in “Lưu thành PDF” / “Microsoft Print to PDF”
- Sổ khách **theo shop** — cùng tên ở PP và Xưởng là hai bản ghi

### Luồng trả nợ / ghi thu

1. Mở khách mua → chi tiết → **Trả nợ** trên phiếu còn nợ **hoặc** chi tiết phiếu bán → nút thu.
2. Chọn trả hết / một phần + hình thức thanh toán.
3. Bấm **Ghi thu** / **Ghi nhận** → **popup xác nhận** (số phiếu, số tiền, hình thức, còn phải thu/chi sau lần này).
4. Xác nhận → ghi `sales_payments` (+ thu chi gắn phiếu). Hủy popup thì không ghi.

### Luồng in danh sách nợ + QR

1. Chi tiết khách → **In danh sách nợ**.
2. Hệ thống lấy phiếu bán đã xác nhận còn nợ, sắp **ngày cũ → mới**.
3. Nếu đã khai TK ở Cài đặt: mặc định gắn QR VietQR (có thể bỏ tích).
4. Xem trước trong modal → **In / Tải PDF**.

File in: `features/customers/debt-print.ts`, QR: `utils/vietqr.ts`.

## Bổ sung theo code hiện tại

Chi tiết khách xưởng có thống kê năm và dòng phiếu theo kg; phiếu đã lập và xác nhận được xem là đã giao.

Chi tiết khách ẩn ghi chú hồ sơ. Nút Lịch nhắc nợ mở bảng lịch trong hộp thoại.

## Cập nhật rà soát 19/11/2026

Xem `docs/audit-progress-20261119.md` để biết thay đổi và giới hạn kiểm thử. Sổ tháng định giá tồn theo lớp nhập mới nhất, không FIFO; tiền công/phụ cấp/khấu trừ theo tháng. Ứng lương mới có lịch sử ngày, tiền và ghi chú, ghi chi thực tế và trừ thực lĩnh; số ứng tổng cũ không được tự gán ngày. Báo cáo thiếu giá vốn không hiển thị lợi nhuận giả bằng cách coi giá vốn là 0. Các bài MySQL phải dùng cấu hình thử riêng, không thay đổi database thật.


Trong chi tiết hóa đơn, app hiện nguồn phiếu, ngày và các lần đã trả. Nút Sửa cạnh ngày thanh toán có nền cam, dùng DD/MM/YYYY và không yêu cầu lý do. Bản in công nợ dùng ngày thanh toán đã lưu sau sửa.
