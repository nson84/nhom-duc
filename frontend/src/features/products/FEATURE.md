# Chức năng products

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

### `/san-pham` — Sản phẩm

| | |
|--|--|
| File | `features/products/ProductsPage.tsx` |
| Ai dùng | Mọi role; Admin chỉ xem |
| API | catalog products/categories/purchase-prices |

**Chức năng**
- Danh sách SP theo shop: tìm mã/tên, lọc đang/ngừng KD
- Thêm/sửa SP (Admin không ghi): **mã, tên, ảnh, trạng thái** (không gắn giá / loại cố định)
- Ảnh lưu khi bấm lưu sản phẩm. Đồng bộ danh mục chép ảnh sang Xưởng cho cả sản phẩm mới và sản phẩm đã có cùng mã.
- Đơn vị quy đổi kg/tạ/tấn tự tạo nội bộ
- **Loại nhôm** (modal): tên, trạng thái + **giá nhập / tấn**, **giá bán / kg, / tạ, / tấn**; lịch sử giá nhập theo loại (đ/kg)
- Mọi SP dùng được mọi loại trong shop — chọn cặp SP+loại lúc lập phiếu
- Tồn theo cặp **SP + loại nhôm** (kg) — **tồn riêng từng shop**, không chia sẻ
- **Đồng bộ danh mục** (nút trên trang): đẩy SP + loại sang shop kia (khớp mã SP / tên loại). Loại mới chỉ copy giá nhập tham khảo, để giá bán Xưởng bằng 0 để người dùng đặt riêng; loại đã có cập nhật giá nhập tham khảo theo mốc có hiệu lực nhưng giữ giá bán Xưởng. Sau đồng bộ, Xưởng được nhắc kiểm tra giá bán bằng cảnh báo và viền vàng các ô giá; có thể sửa giá hoặc xác nhận giữ nguyên. Phục vụ xuất nội bộ dễ chọn cùng mã/loại — **không đồng bộ tồn**
- Thông báo yêu cầu kiểm tra giá bán sau đồng bộ hiện lưu ở trình duyệt dùng chung giữa hai sổ, không phải một trạng thái lưu trên server.
- Ô giá loại: validate khi **blur** / **Lưu**; phải bội 500 đ (…,000 hoặc …,500)
- Khi chọn cập nhật giá nhập từ phiếu, hệ thống lưu mốc mới theo ngày phiếu và giữ mốc cũ, kể cả khi hai lần đổi giá cùng ngày. Bảng lịch sử hiện ngày bắt đầu và ngày kết thúc hoặc nhãn “Đã thay trong ngày”. Chuông trên header nhắc kiểm tra giá bán / kg, / tạ, / tấn. Thông báo chưa xem có chấm xanh ở mép phải; mở menu không tự đánh dấu đã xem, bấm vào thông báo mới đánh dấu đã xem và mở đúng loại để sửa. Có thể chọn “Đã cập nhật giá bán” để gỡ nhắc. Trạng thái xem và nhắc lưu trong trình duyệt theo từng shop; hiện chưa đồng bộ giữa các thiết bị.
- Khi đăng nhập, chuông đối chiếu nhắc giá trong trình duyệt với danh mục hiện tại của sổ đang xem và tự gỡ nhắc của loại nhôm đã bị xóa hoặc đổi tên; reset database không còn để lại nhắc giá của loại cũ.

## Bổ sung theo code hiện tại

Xưởng ẩn cột tồn vì không trừ tồn theo phiếu bán. Giá nhập theo loại là giá tham khảo, tách khỏi giá nhập thực tế trên phiếu.
