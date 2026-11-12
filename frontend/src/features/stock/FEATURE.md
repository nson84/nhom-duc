# Chức năng stock

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

### `/nhap-kho` — Nhập kho

| | |
|--|--|
| File | `features/stock/DocumentsPage.tsx` (`kind=PURCHASE`) |
| Ai dùng | Mọi role; Admin chỉ xem |
| API | stock documents purchase |

**Chức năng**
- Danh sách: nhãn **Nội bộ** / **Nhà cung cấp** trên số phiếu; lọc mặc định **Chưa thanh toán**; thêm Thanh toán một phần / Đã thanh toán / Nháp / Đảo·Hủy / Thùng rác
- Tạo/sửa phiếu nháp: NCC, ngày, dòng hàng (**SP + loại nhôm**, ĐVT, SL, đơn giá autofill từ loại)
- Ngày phiếu có thể chọn lùi về năm 2025 để tự nhập dữ liệu lịch sử; kỳ Xưởng đã chốt sẽ khóa phiếu thuộc khoảng ngày đó.
- Khi tạo phiếu nhập mới có đơn giá khác giá nhập chuẩn của loại, trước khi lưu phiếu sẽ hỏi có ghi mốc giá nhập mới từ ngày phiếu không; popup hiện giá cũ và ngày bắt đầu áp dụng, giá mới và ngày hiệu lực. Chọn “Chỉ lưu phiếu” thì giữ giá chuẩn hiện tại và chỉ lưu đơn giá thực tế trên phiếu. Một loại có nhiều đơn giá khác nhau trong cùng phiếu phải cập nhật giá chuẩn thủ công.
- Đơn vị: Phân phối chỉ **tấn**; Xưởng nhập **tạ** hoặc **tấn**
- Xác nhận → cập nhật tồn/giá vốn theo `(product, category)`; không sửa dòng sau xác nhận
- Thanh toán (có **popup xác nhận** trước khi ghi) / đảo phiếu (reversal, mỗi phiếu đảo một lần)
- In phiếu PDF (xem trước + Lưu PDF); draft session. Bảng chi tiết và bản in không hiện cột mã nhôm.
- **Nháp / hủy / đảo / thùng rác** — xem mục [Luồng phiếu kho](#luồng-phiếu-kho-nháphủyđảo--thùng-rác) bên dưới

### `/ban-hang` — Bán hàng

| | |
|--|--|
| File | `features/stock/DocumentsPage.tsx` (`kind=SALE`) |
| Ai dùng | Mọi role; Admin chỉ xem |
| API | stock documents sale |

**Chức năng**
- Danh sách: nhãn **Nội bộ** / **Khách hàng** trên số phiếu; lọc mặc định **Chưa thanh toán**; Thùng rác cho phiếu **đã hủy / đã đảo**
- Khách hàng: **dropdown tìm kiếm** sổ khách; gõ tên mới được; sau lưu phiếu nếu chưa gắn khách → hỏi **Lưu khách hàng?**
- Mỗi dòng: chọn **SP → loại nhôm** → đơn giá autofill từ loại
- **Cách bán**: chỉ theo cân. Xưởng nhập kg/tạ/tấn thành phẩm đã giao (quy đổi kg nhôm, không gồm phụ kiện); Phân phối bán tấn. Phiếu bán xưởng mới không trừ tồn; Phân phối cũng không trừ tồn khi xác nhận phiếu bán mới. Đơn giá lấy từ loại nhôm và có thể sửa trên phiếu
- Xưởng phải gắn khách mua trước khi xác nhận để cộng sản lượng và công nợ theo người. Phiếu xác nhận là hàng đã giao, không tạo đơn đặt hàng hoặc số còn phải giao
- Khi tạo hoặc sửa phiếu bán Xưởng, ô chọn đơn vị nằm ngang hàng với ô khối lượng trên màn hình rộng. Giá nhập tham chiếu được lưu nội bộ và ẩn trên biểu mẫu lẫn chi tiết phiếu bán.
- Phiếu m² đã xác nhận vẫn xem/in/đảo theo dữ liệu cũ. Phiếu nháp m² phải chuyển sang cân và nhập lại đơn giá trước khi xác nhận
- Phân phối **Nội bộ**: khớp SP theo mã **và** loại theo **tên** ở xưởng
- Snapshot: categoryId/tên loại, billingUnit, dài/rộng, quantityBase
- Chi tiết phiếu bán ẩn mã nhôm, giá vốn thực tế, giá vốn chuẩn và chênh lệch giá nhập.
- Ghi thu (có **popup xác nhận**), đảo
- **In PDF** trên bán hàng của cả Phân phối và Xưởng là một nút: xem trước hóa đơn rồi in / tải PDF (cùng cách với danh sách nợ). Hóa đơn có người bán, tên khách hàng, sản phẩm, loại nhôm, số lượng, đơn vị, đơn giá, thành tiền, và một VietQR
- **Nháp / hủy / đảo / thùng rác** — cùng quy tắc với nhập kho (mục luồng bên dưới)

### `/ton-kho` — Tồn kho

| | |
|--|--|
| File | `features/stock/StockPage.tsx` |
| Ai dùng | Mọi role (đọc) |
| API | stock balances / movements |

**Chức năng**
- Bảng tồn theo **SP + loại nhôm** + giá trị tồn
- Phân phối hiển thị tấn. Xưởng dùng trang Quyết toán xưởng và kiểm kê cuối năm
- Phân phối có **Kiểm kê / Hao hụt**: chỉ nhập kg còn trong kho theo từng SP + loại. Popup tính hao hụt = kg nhập − kg giao (gồm nội bộ) − kg còn, lấy phiếu đã xác nhận và chưa đảo. Khi lưu, chỉ các dòng đã nhập được điều chỉnh để tồn sổ khớp số cân.
- Click dòng → biến động tồn

### Luồng phiếu kho (nháp / hủy / đảo → thùng rác)

Khi đóng form phiếu nhập/bán đã thay đổi hoặc chuyển trang trong ứng dụng, chọn **Tiếp tục nhập** để giữ form mở hoặc **Lưu nháp và rời** để nhập tiếp sau. Phiếu đủ thông tin vào danh sách **Nháp** của sổ. Phiếu chưa đủ thông tin nằm trong mục **Phiếu nháp trên thiết bị này** và có thể mở lại để sửa rồi lưu.

Lịch sử chi tiền trên phiếu nhập hiển thị hình thức thanh toán, người trả và người nhận ở các cột riêng.

Popup ghi chi và ghi thu bố trí hai ô mỗi hàng trên màn hình rộng và một ô mỗi hàng trên màn hình hẹp. Khi số tiền nhập lớn hơn 0 và nhỏ hơn số dư, dưới ô số tiền hiện “Thanh toán 1 phần”. Nút “Thanh toán hết số còn lại” điền số dư vào ô số tiền để người dùng kiểm tra trước khi ghi nhận.

Áp dụng `/nhap-kho` và `/ban-hang`. Backend: `StockDocumentService.moveToTrash` chỉ chấp nhận phiếu **CANCELLED** hoặc phiếu **CONFIRMED đã có phiếu đảo** (`reversedBy`).

| Trạng thái khi mở chi tiết | Nút chính | Ghi chú |
|----------------------------|-----------|---------|
| **Nháp** (`DRAFT`) | In PDF, Sửa, **Hủy nháp**, Xác nhận | **Không** có “Chuyển thùng rác”. Muốn dọn → Hủy nháp trước |
| **Đã hủy** (`CANCELLED`) | In PDF, **Chuyển thùng rác** | Ẩn khỏi danh sách chính |
| **Đã xác nhận**, chưa đảo | In PDF, Thu/Chi (nếu còn nợ), Đảo phiếu… | Không vào thùng rác |
| **Đã xác nhận + đã đảo** | In PDF, **Chuyển thùng rác** | Chỉ ẩn danh sách; **không** xóa cứng (đã gắn kho / phiếu đảo) |
| **Trong thùng rác** | In PDF, **Khôi phục** | Lọc danh sách “Thùng rác” |

**Hủy nháp vs thùng rác**
- **Hủy nháp:** `DRAFT` → `CANCELLED`. Không đổi tồn/tiền. Phiếu còn trong lọc “Đảo·Hủy” để tra cứu, không sửa/xác nhận lại.
- **Chuyển thùng rác:** ẩn khỏi danh sách chính (`trashed_at`). Phiếu **đã hủy** giữ tối đa **90 ngày** rồi purge cứng nếu không còn ràng buộc kho/thu. Phiếu **đã đảo** chỉ ẩn, không đếm ngày xóa cứng.

```
Nháp ──Hủy nháp──► Đã hủy ──Chuyển thùng rác──► Thùng rác ──(90 ngày, chỉ CANCELLED)──► Xóa cứng
  │                     ▲
  └─Xác nhận──► Đã xác nhận ──Đảo phiếu──► (gốc đã đảo) ──Chuyển thùng rác──► Thùng rác (chỉ ẩn)
```

## Bổ sung theo code hiện tại

Phiếu bán xưởng tự lấy dòng nhập gần nhất làm giá nhôm tham chiếu; giá bán nhập riêng. Phiếu xưởng mới không trừ tồn và không tính giá vốn bán hằng ngày. Mục tồn kho ẩn ở xưởng.

Tồn Phân phối theo số lượng. Phiếu bán gợi ý giá theo loại nhôm và cho sửa giá trên phiếu.

Tạo phiếu bán Phân phối xác nhận và trừ tồn trong một giao dịch. Thiếu tồn thì không tạo phiếu.
