# Chức năng stock

VietQR trên hóa đơn bán ngoài chỉ điền số còn phải trả. Hóa đơn đã trả đủ hiện QR tài khoản nhận với số tiền trống và nhãn đã thanh toán.

Màn Công nợ đọc chi tiết phiếu kỳ trước qua `getCarryoverDocument`. Form cho chọn ngày thực thu hoặc thực trả trong kỳ báo cáo; yêu cầu thanh toán gửi tháng tương ứng với ngày đó và cờ xác nhận nếu tháng đã khóa. Tên người thu, trả hoặc nhận có thể bổ sung nếu cần in. Với phiếu bán, khi để trống người thu, backend dùng tên tài khoản theo quy tắc hiện có. Form từ màn phiếu vẫn theo tháng làm việc.

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

Danh sách phiếu, tồn kho và bảng dòng hàng cuộn ngang riêng khi cần; modal nhập/bán ở cửa sổ thấp cuộn phần thân, giữ nút lưu/đóng ở footer trong màn hình.
Hàng đầu biểu mẫu phiếu giữ chiều cao nhãn nhất quán để các ô Nguồn phiếu, Ngày phiếu và Người nhập cùng mép trên.
Trong chi tiết phiếu, nút Sửa cạnh ngày thanh toán có nền cam.
Ngày thu hoặc trả sớm nhất là ngày lập phiếu và phải thuộc kỳ đang mở khi ghi khoản mới. Chi tiết phiếu đánh dấu khoản cũ có ngày thanh toán trước ngày lập phiếu để đối chiếu và sửa bằng nút **Sửa**.
Form ghi thanh toán mặc định lấy ngày lập phiếu. Nếu phiếu thuộc kỳ trước, form gợi ý ngày đầu khoảng làm việc đang chọn. Người dùng có thể đổi sang ngày thực trả trước khi lưu.
Dòng bán dùng hai cột cùng độ rộng khi đủ chỗ. Nút xóa dòng nằm ở góc dưới trái, thành tiền ở góc dưới phải.
DeliveredByCategory hỗ trợ trình bày cùng hàng với tổng khối lượng trên chi tiết khách mua.
Các thẻ thống kê tồn kho có cùng kích cỡ. Phân phối hiển thị hai hàng, mỗi hàng ba thẻ trên màn hình rộng và giảm số cột khi màn hình hẹp.

### `/nhap-kho` — Nhập kho

| | |
|--|--|
| File | `features/stock/DocumentsPage.tsx` (`kind=PURCHASE`) |
| Ai dùng | Mọi role; Admin chỉ xem |
| API | stock documents purchase |

**Chức năng**
- Danh sách: nhãn **Nội bộ** / **Nhà cung cấp** trên số phiếu; lọc mặc định **Chưa thanh toán**; thêm Thanh toán một phần / Đã thanh toán / Nháp / Đảo·Hủy / Thùng rác
- Tạo/sửa phiếu nháp: NCC, ngày, dòng hàng (**SP + loại nhôm**, ĐVT, SL, đơn giá autofill từ loại)
- Cửa sổ tạo, sửa và xem chi tiết phiếu nhập có nhãn **Nhà cung cấp** hoặc **Nội bộ** trên tiêu đề. Phiếu nhập nội bộ được tạo tự động từ phiếu xuất nội bộ của Phân phối.
- Ô **Người nhập** cho chọn Hải hoặc Thuấn. Khi trả tiền phiếu nhập, ô **Người trả tiền** gợi ý hai tên này và cho nhập tên khác, còn ô **Người nhận tiền** nhập tên tự do.
- Phiếu bán tự điền **Người nhận** theo tên khách mua khi ô này còn trống hoặc vẫn là giá trị tự điền; người dùng sửa Người nhận riêng thì thay tên khách không ghi đè phần đã sửa.
- Phiếu bán mới mặc định ngày hôm nay nếu nằm trong tháng đang mở. Khi nhập kỳ cũ, app ưu tiên ngày của phiếu bán vừa tạo trong cùng kỳ trên màn hình, rồi đến ngày bán gần nhất của phiếu đã xác nhận trong danh sách đang xem. Nếu chưa có ngày phù hợp, app dùng ngày đầu khoảng làm việc. Có thể sửa ngày trên phiếu trước khi lưu.
- Ngày phiếu có thể chọn lùi về năm 2025 để tự nhập dữ liệu lịch sử; kỳ Xưởng đã chốt sẽ khóa phiếu thuộc khoảng ngày đó.
- Phiếu mới lấy ngày hôm nay nếu nằm trong khoảng làm việc đang xem, kể cả ngày cuối khoảng. Khi xem tháng cũ, ngày mặc định là ngày đầu khoảng đang xem.
- Khi chọn năm ở Tổng quan, danh sách và chi tiết chỉ mở phiếu nằm trong kỳ đã chọn dù chưa bấm Khóa năm. Form không cho chọn ngày trước ngày bắt đầu hoặc sau ngày kết thúc đã lưu; năm đã chốt không nhận phiếu mới.
- Khi chọn tháng ở Tổng quan, danh sách và form phiếu chỉ dùng ngày trong phần giao của tháng và kỳ năm. Chỉ tháng đã bấm **Mở tháng** mới cho tạo, sửa, xác nhận, hủy hoặc đảo phiếu; lần thu/trả nợ mới được ghi ở tháng đang mở.
- Khi tạo phiếu nhập mới có đơn giá khác giá nhập chuẩn của loại, trước khi lưu phiếu sẽ hỏi có ghi mốc giá nhập mới từ ngày phiếu không; popup hiện giá cũ và ngày bắt đầu áp dụng, giá mới và ngày hiệu lực. Chọn “Chỉ lưu phiếu” thì giữ giá chuẩn hiện tại và chỉ lưu đơn giá thực tế trên phiếu. Một loại có nhiều đơn giá khác nhau trong cùng phiếu phải cập nhật giá chuẩn thủ công.
- Đơn vị: Phân phối chỉ **tấn**; Xưởng nhập **tạ** hoặc **tấn**
- Xác nhận → cập nhật tồn/giá vốn theo `(product, category)`; không sửa dòng sau xác nhận
- Thanh toán (có **popup xác nhận** trước khi ghi) / đảo phiếu (reversal, mỗi phiếu đảo một lần)
- Trang Nhập kho hiển thị doanh nghiệp còn nợ nhà cung cấp từ phiếu nhập trong kỳ đang xem, không phụ thuộc ô tìm kiếm hoặc bộ lọc trạng thái; phiếu nội bộ không tạo nợ.
- Trang Nhập kho hiển thị thêm tồn sổ **hiện tại** theo từng loại nhôm của bên đang xem, độc lập với kỳ và bộ lọc danh sách phiếu; Phân phối dùng tấn, Xưởng dùng kg.
- Ô tổng khối lượng đã giao trên Tồn kho hiện thêm kg theo từng loại: Phân phối tính tích lũy hiện tại, Xưởng theo kỳ kiểm kê đang chọn.
- Khi chọn tháng ở Tổng quan, Tồn kho hiện thêm số kg giao trong tháng (phần giao với kỳ năm) theo từng loại. Tồn sổ hiện tại và số lũy kế/kiểm kê năm vẫn giữ nhãn riêng, không được hiểu là tồn cuối tháng lịch sử.
- Xuất nội bộ Phân phối → Xưởng vẫn lưu phiếu bán và phiếu nhập cặp, trừ tồn Phân phối, cộng khối lượng đã giao; đơn giá gợi ý dùng giá nhập của loại nhôm, cho phép sửa theo giá vốn thực tế trên phiếu. Phiếu nội bộ không cộng doanh thu/lãi bán hàng ở Tổng quan và Báo cáo.
- Phiếu xuất nội bộ hiển thị Nội bộ ở ô khách hàng, không yêu cầu tạo hồ sơ khách. Phiếu nhập cặp dùng ngày phiếu xuất và chỉ hiện trong phạm vi đang lọc ở Xưởng.
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
- Ô **Người xuất** trên phiếu bán của cả Phân phối và Xưởng gợi ý Hải, Thuấn; vẫn nhập được tên khác.
- Mỗi dòng: chọn **sản phẩm** và **loại nhôm** độc lập. Đổi sản phẩm giữ loại đã chọn trên dòng. Đơn giá gợi ý lấy từ loại nhôm.
- **Cách bán**: chỉ theo cân. Xưởng nhập kg/tạ/tấn thành phẩm đã giao (quy đổi kg nhôm, không gồm phụ kiện); Phân phối bán tấn. Phiếu bán xưởng mới không trừ tồn; Phân phối trừ tồn ngay khi xác nhận phiếu bán (thiếu tồn sẽ báo lỗi). Đơn giá lấy từ loại nhôm và có thể sửa trên phiếu
- Xưởng phải gắn khách mua trước khi xác nhận để cộng sản lượng và công nợ theo người. Phiếu xác nhận là hàng đã giao, không tạo đơn đặt hàng hoặc số còn phải giao
- Khi tạo hoặc sửa phiếu bán Xưởng, ô chọn đơn vị nằm ngang hàng với ô khối lượng trên màn hình rộng. Giá nhập tham chiếu được lưu nội bộ và ẩn trên biểu mẫu lẫn chi tiết phiếu bán.
- Phiếu m² đã xác nhận vẫn xem/in/đảo theo dữ liệu cũ. Phiếu nháp m² phải chuyển sang cân và nhập lại đơn giá trước khi xác nhận
- Phân phối **Nội bộ**: khớp SP theo mã **và** loại theo **tên** ở xưởng
- Snapshot: categoryId/tên loại, billingUnit, dài/rộng, quantityBase
- Chi tiết phiếu bán ẩn mã nhôm, giá vốn thực tế, giá vốn chuẩn và chênh lệch giá nhập.
- Ghi thu (có **popup xác nhận**), đảo
- Người thu tiền khi ghi thu gợi ý **Hải** hoặc **Thuấn**, vẫn cho nhập tên khác và in đúng tên đã chọn.
- **In PDF** trên bán hàng của cả Phân phối và Xưởng là một nút: xem trước hóa đơn rồi in / tải PDF (cùng cách với danh sách nợ). Hóa đơn có người bán, tên khách hàng, sản phẩm, loại nhôm, số lượng, đơn vị, đơn giá, thành tiền, và một VietQR
- **Nháp / hủy / đảo / thùng rác** — cùng quy tắc với nhập kho (mục luồng bên dưới)

### `/ton-kho` — Tồn kho

Xưởng hiển thị tổng tiền hàng đã nhập tích lũy, gồm giá trị chuyển nội bộ theo phiếu nhập và giá trị nhập ngoài. Thẻ tiền nhập trong kỳ dùng khoảng ngày kỳ kiểm kê và hiện dấu gạch nếu báo cáo chưa có dữ liệu.

| | |
|--|--|
| File | `features/stock/StockPage.tsx` |
| Ai dùng | Mọi role (đọc) |
| API | stock balances / movements |

**Chức năng**
- Bảng tồn theo **SP + loại nhôm** + giá trị tồn
- Thẻ Phân phối hiện tổng tiền hàng đã nhập từ phiếu còn hiệu lực, kg nhập/giao, tồn sổ và kết quả vụn/hao hụt kiểm kê gần nhất. Thẻ Xưởng hiện tiền và kg nhập trong kỳ chọn, kg giao, tổng vụn đã bán cộng vụn còn theo kiểm kê, hao hụt và giá trị cuối kỳ. Tiền hàng đã nhập không được gọi là giá trị tồn.
- Xưởng hiện thêm tổng kg đã nhập kho tích lũy (gồm nội bộ và nhập ngoài) và tồn sổ hiện tại, độc lập với ngày đầu kỳ kiểm kê. Thẻ “nhập trong kỳ” chỉ cộng phiếu có ngày thuộc kỳ; phiếu nội bộ trước kỳ vẫn có trong tồn sổ hiện tại. Lịch sử biến động gắn nhãn **Nội bộ** trên phiếu chuyển.
- Phân phối hiển thị tấn. Xưởng dùng trang Quyết toán xưởng và kiểm kê cuối năm
- Phân phối có **Kiểm kê / Hao hụt**: nhập kg nguyên liệu còn trong kho và kg vụn theo từng SP + loại. Popup liệt kê cả cặp hiện tồn 0 để có thể ghi nhận lần cân cho mọi cặp từng phát sinh kho. Khi lưu, chỉ các dòng đã nhập được điều chỉnh để tồn sổ khớp số cân; hao hụt thuần là phần thiếu trừ vụn.
- Click dòng → biến động tồn

### Luồng phiếu kho (nháp / hủy / đảo → thùng rác)

Khi đóng form phiếu nhập/bán đã thay đổi hoặc chuyển trang trong ứng dụng, chọn **Tiếp tục nhập** để giữ form mở hoặc **Lưu nháp và rời** để nhập tiếp sau. Phiếu đủ thông tin vào danh sách **Nháp** của sổ. Phiếu chưa đủ thông tin nằm trong mục **Phiếu nháp trên thiết bị này** và có thể mở lại để sửa rồi lưu.

Mục phiếu nháp trên thiết bị luôn hiện toàn bộ nháp của sổ/tài khoản, kể cả nháp ở kỳ khác. Nháp khác kỳ đang chọn không mở sửa được cho tới khi chọn đúng kỳ; có nút xóa từng bản và xóa tất cả với xác nhận. Nháp chỉ trên thiết bị không nằm trong sao lưu database/R2 và sẽ mất khi xóa dữ liệu ứng dụng hoặc mất máy. Hãy hoàn thành và lưu phiếu vào database trước khi chuyển máy; bản nháp đủ điều kiện lưu vào database vẫn có trong backup.

Lịch sử chi tiền trên phiếu nhập hiển thị hình thức thanh toán, người trả và người nhận ở các cột riêng.

Popup ghi chi và ghi thu bố trí hai ô mỗi hàng trên màn hình rộng và một ô mỗi hàng trên màn hình hẹp. Khi số tiền nhập lớn hơn 0 và nhỏ hơn số dư, dưới ô số tiền hiện “Thanh toán 1 phần”. Nút “Thanh toán hết số còn lại” điền số dư vào ô số tiền để người dùng kiểm tra trước khi ghi nhận.

Áp dụng `/nhap-kho` và `/ban-hang`. Backend: `StockDocumentService.moveToTrash` chỉ chấp nhận phiếu **CANCELLED** hoặc phiếu **CONFIRMED đã có phiếu đảo** (`reversedBy`).

Ô ngày phiếu nhập/bán xác nhận lựa chọn ngay khi bấm ngày trong lịch, không đòi bấm thêm OK trong popup giờ. Người dùng vẫn có thể chỉnh giờ riêng.

| Trạng thái khi mở chi tiết | Nút chính | Ghi chú |
|----------------------------|-----------|---------|
| **Nháp** (`DRAFT`) | In PDF, Sửa, **Hủy nháp**, Xác nhận | **Không** có “Chuyển thùng rác”. Muốn dọn → Hủy nháp trước |
| **Đã hủy** (`CANCELLED`) | In PDF, **Chuyển thùng rác** | Ẩn khỏi danh sách chính |
| **Đã xác nhận**, chưa đảo | In PDF, Sửa trong 24 giờ (trừ chuyển nội bộ), Thu/Chi (nếu còn nợ), Đảo phiếu… | Không vào thùng rác |
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

### Bổ sung giao diện Tổng tồn kho

Phân phối hiển thị tổng giá trị tồn sổ, số kg đã nhập, đã giao và tồn sổ, có thể kiểm kê thực tế từng cặp sản phẩm/loại; số tổng không bị thay đổi khi tìm kiếm trong bảng. Xưởng hiển thị tóm tắt nhập, giao, phế liệu đã bán và hao hụt ước tính theo báo cáo quyết toán của năm hiện tại (không trừ tồn từng lần giao); có liên kết sang Quyết toán Xưởng. Chỉ tiêu phế liệu hiện là **đã bán**, không đại diện cho toàn bộ phế liệu chưa bán.

Khi kiểm kê Phân phối, người vận hành có thể khai số kg vụn nằm trong phần thiếu so với tồn sổ. Hao hụt thuần bằng phần thiếu trừ vụn. Cả lần cân không lệch tồn cũng lưu dấu vết; các số vụn/hao hụt ở trang Tồn kho là **bản chụp lần kiểm kê gần nhất theo từng SP/loại**, không phải tổng phát sinh trong năm. Chốt kỳ từ chối nếu còn sản phẩm/loại chưa kiểm kê, tồn đã phát sinh giao dịch sau cân hoặc có biến động sau ngày kết thúc kỳ (không thể dùng số cân hiện tại làm tồn quá khứ).

Xưởng: trang Tồn kho mặc định dùng năm làm việc đang chọn, có thể đổi năm để xem báo cáo kiểm kê; đồng thời hiển thị bảng tồn sổ hiện tại theo từng sản phẩm/loại nhôm (bao gồm hàng nhập nội bộ). Bảng tồn sổ không lọc theo năm; hàng giao khách không tự trừ tồn. Vụn đã bán, vụn chưa bán, tồn cuối kỳ và hao hụt lấy từ báo cáo/kiểm kê Xưởng theo năm được chọn.

Kiểm kê Phân phối gửi ngày nghiệp vụ theo tháng đang chọn. Tháng quá khứ dùng cuối ngày cuối khoảng đang xem, tháng hiện tại dùng thời điểm hiện tại. Backend kiểm tra tháng mở trước khi tạo điều chỉnh kho.

## Tồn cuối chuyển sang đầu kỳ theo từng loại

GET /api/v1/dashboard/opening-inventory?year=...&month=... trả nguyên liệu đầu kỳ theo từng loại, ngày bắt đầu và ngày chốt nguồn. Bỏ month để xem đầu kỳ năm. Tồn đầu kỳ là bản đọc của lần chốt kết thúc ngay ngày trước đó; không tạo phiếu nhập, không ghi stock movement và không cộng kho lại khi reload. Nếu cùng ngày có bản chốt tháng và năm, lấy bản năm một lần. Bản tháng còn giữ vụn theo loại; vụn năm vẫn theo tổng kiểm kê hiện có, không tự chia vào loại.

V42 thêm year_end_inventory_categories, lưu kg và tên từng loại nguyên liệu trong cùng transaction chốt năm. Bản chốt cũ chỉ dùng chi tiết/historical movements khi tổng khớp; thiếu dữ liệu phải hiện thông báo cần đối chiếu, không tự chia tổng kg hoặc coi thiếu dữ liệu là 0. Không tự chạy migration vào database thật để test.


## Chép phiếu từ sổ giấy

Phiếu nhập/bán mới mặc định Ghi từ app. Chọn Ghi từ sổ để nhập lại phiếu giấy. App vẫn tạo mã phiếu. Ngày phiếu và ngày thanh toán dùng DD/MM/YYYY. Ô giá vốn nhập tay chỉ hiện và được gửi khi lưu dòng bán ngoài từ sổ. Ô dùng đơn vị bán (kg, tạ hoặc tấn), phân cách hàng nghìn bằng dấu phẩy và tự quy đổi về đ/kg. Phiếu ghi từ app dùng giá nhập tham chiếu khi có dữ liệu. Giá vốn nhập tay không sửa kho hoặc công thức chốt năm. Trong chi tiết phiếu, Sửa ngày cập nhật cùng khoản thanh toán, không yêu cầu lý do.

Khi chốt sổ tháng Phân phối qua màn Chốt sổ tháng, cùng số cân cập nhật tồn dùng để bán và được gom theo loại trong bản chốt. Số tồn dùng để bán được chuyển tiếp theo biến động kho.


Nhập kho Xưởng có Lịch sử nhập nội bộ để tra cứu cả phiếu ngoài kỳ đang chọn. Màn chi tiết mở từ lịch sử chỉ xem.
