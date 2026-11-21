# Danh sách việc: phục hồi trong app và chuyển sổ giấy

Các việc dưới đây chưa triển khai. Mỗi việc cần kiểm tra trên DB/test profile riêng, không thử ghi DB đang dùng.

## R1 — Ghi nhận profile phục hồi đã xác minh

- [ ] Liệt kê bản đã dựng thử với nguồn R2, thời điểm backup, DB và ảnh; kiểm tra lại profile/schema/ảnh trước khi cho chọn.
- [ ] Không chấp nhận profile tự sửa hoặc đường dẫn DB ngoài loopback `aluminum_restore_*`.
- [ ] Kiểm thử profile hợp lệ, bị sửa, thiếu ảnh, sai DB.

## R2 — Chọn DB phục hồi khi khởi động desktop

- [ ] Tauri đọc lựa chọn DB trước khi khởi động backend; mật khẩu MySQL nằm trong Windows Credential Manager hoặc được hỏi trong app.
- [ ] App khởi động backend phục hồi, kiểm tra ready/DB/schema rồi mới đánh dấu đang dùng.
- [ ] Cổng bận hoặc khởi động lỗi giữ nguyên DB trước; không giết backend khác.

## R3 — Giao diện chuyển và quay lại

- [ ] Sau Dựng DB từ R2, hiện dữ liệu cần đối chiếu và nút “Dùng bản này” với xác nhận rõ nguồn/thời gian.
- [ ] Tạo backup trước chuyển; chuyển an toàn và có nút “Quay về dữ liệu trước”.
- [ ] Kiểm thử tắt app giữa chuyển, sai mật khẩu và rollback.

## R4 — Backup của DB phục hồi

- [ ] Tách/đánh dấu nguồn các bản backup theo profile/khóa để không lẫn DB cũ và mới trên R2.
- [ ] Chỉ bật lịch sau khi người dùng xác nhận DB phục hồi là nguồn vận hành; kiểm tra upload, tải, giải mã và dựng lại.
- [ ] Diễn tập toàn bộ trên Windows sạch với R2 thử và DB riêng.

## G1 — Chốt mốc chuyển từ giấy

- [ ] Chọn ngày T, shop, danh mục giấy và nhu cầu xem lịch sử; lập bảng đối chiếu tổng theo từng shop.
- [ ] Quyết định chỉ nhập số đầu kỳ hay nhập lại các chứng từ lịch sử cần thiết.
- [ ] Không mở kỳ app mới khi số đầu kỳ chưa đối chiếu.

## G2 — Tồn đầu kỳ theo loại

- [ ] Nhập kg, giá trị và nguồn giấy theo shop/sản phẩm/loại; kiểm tra trùng và tổng.
- [ ] Tồn, giá vốn và chốt kỳ dùng đúng số đầu kỳ một lần, không tạo phiếu nhập giả.
- [ ] Kiểm thử có/không có lịch sử nhập và kỳ đã khóa.

## G3 — Công nợ đầu kỳ

- [ ] Nhập số còn nợ theo khách/NCC và tham chiếu giấy; có màn kiểm tra tổng trước khi xác nhận.
- [ ] Lần thu/trả sau T giảm đúng nợ đầu, không tăng doanh thu/chi phí của kỳ mới lần hai.
- [ ] Kiểm thử trả một phần, trả hết, ghi trùng và đảo/sửa khi kỳ chưa khóa.

## G4 — Hướng dẫn nhập giấy và đối chiếu

- [ ] Màn hướng dẫn cho biết dữ liệu nào nhập số đầu kỳ, dữ liệu nào dùng phiếu hiện có.
- [ ] Mỗi dòng có tham chiếu giấy và cảnh báo trùng; lưu nháp và xác nhận rõ ràng.
- [ ] Bảng đối chiếu tồn/nợ/thu chi theo shop; giá trị thiếu hiện chưa đối chiếu.

## G5 — Nghiệm thu chuyển sổ

- [ ] Một kỳ chạy song song giấy và app, đối chiếu phiếu/tồn/nợ theo ngày hoặc tuần.
- [ ] Backup R2, tải và dựng thử trên DB khác; các số đầu kỳ và tham chiếu giấy còn nguyên.
- [ ] Chỉ mở rộng sang nhập lịch sử hàng loạt/OCR nếu còn nhu cầu thực tế.
