# Danh sách việc: phục hồi R2 trong ứng dụng

Chưa triển khai. Mỗi việc dùng DB/profile thử riêng; không thử ghi database đang dùng.

## R1 — Xác minh bản đã dựng

- [ ] Lưu kết quả dựng thử cùng nguồn R2, thời điểm backup, DB, ảnh và bản tóm tắt dữ liệu.
- [ ] Kiểm tra lại schema/ảnh/DB, từ chối profile tự sửa và URL ngoài loopback `aluminum_restore_*`.
- [ ] Test profile hợp lệ, sửa tay, thiếu ảnh, sai DB.

## R2 — Mở backend bằng DB đã chọn

- [ ] Tauri đọc lựa chọn DB trước khi khởi động backend; dùng profile do app tạo, không hỏi đường dẫn.
- [ ] Hỏi mật khẩu MySQL trong app khi cần; không lưu vào JSON/log. Nếu nhớ, dùng Windows Credential Manager.
- [ ] Chỉ coi khởi động thành công khi ready trả đúng DB/schema; cổng bận không giết backend khác.

## R3 — Một nút chuyển và quay lại

- [ ] Sau **Dựng DB từ R2**, hiện tóm tắt đối chiếu và nút **Dùng bản này**.
- [ ] Tạo backup trước chuyển; hiển thị tiến trình, lỗi cụ thể và DB đang dùng.
- [ ] Lưu lựa chọn nguyên tử, tự mở lại app; lỗi hoặc tắt ngang quay về DB trước.
- [ ] Có nút **Quay về dữ liệu trước** và xác minh sau khi quay lại.

## R4 — Backup của DB phục hồi

- [ ] Phân biệt bản backup/khóa của DB phục hồi và DB cũ trên R2.
- [ ] Kiểm tra R2 và khóa phục hồi rồi bật lịch backup cho DB đã chọn làm nguồn vận hành.
- [ ] Diễn tập trên Windows sạch: mất máy → R2 → dựng → một nút dùng → backup → quay lại; thử cả sai khóa, thiếu ảnh và mất mạng.
