# Việc triển khai: phục hồi R2 bằng giao diện desktop

Chưa triển khai. Mọi thử nghiệm dùng DB/profile riêng, không ghi DB đang vận hành.

## R1 — ID và xác minh lần dựng thử

- [ ] Sau tải/giải mã/dựng R2, trả ID bền vững, nguồn/thời gian bản sao và tóm tắt hai sổ; giữ ảnh, FK và số dòng đã kiểm tra.
- [ ] API xác minh lại ID trước khi chuyển; từ chối profile bị sửa, URL ngoài loopback và thiếu ảnh.
- [ ] Test bản hợp lệ, sai khóa, thiếu ảnh, profile giả.

## R2 — Tauri khởi động backend theo profile

- [ ] Tauri chọn profile trước khi khởi động Java; runtime, ảnh, bootstrap, R2 và token backup trỏ đúng profile.
- [ ] Nhận MySQL credential qua kênh nội bộ; lưu theo profile trong Windows Credential Manager hoặc hỏi trong UI khi mở lại.
- [ ] Kiểm tra cổng bận, quyền sở hữu tiến trình, `/ready` đúng DB/schema; không dừng backend ngoài app.

## R3 — Chuyển và quay lại an toàn

- [ ] Tạo backup của DB đang dùng trước chuyển; lưu A/B nguyên tử.
- [ ] Tauri dừng backend do app sở hữu, mở B, chỉ xác nhận B sau khi kiểm tra đạt; lỗi tự mở lại A.
- [ ] Mất điện/tắt app giữa chuyển: lần mở sau tự quay về A, không xóa B.
- [ ] Cùng cơ chế cho nút Quay về dữ liệu trước; test chuyển hai chiều.

## R4 — Wizard trong giao diện

- [ ] Chọn bản R2, nhập khóa và MySQL trong modal, bấm Khôi phục bản này; hiển thị tiến trình và ngăn thao tác trùng.
- [ ] Tóm tắt bản đã dựng; nút Dùng dữ liệu này kèm xác nhận thời điểm và DB cũ được giữ.
- [ ] Đặt mật khẩu Admin mới/hiển thị thiết lập tài khoản còn lại trong UI; sau chuyển điều hướng đăng nhập, hiển thị DB đang dùng.
- [ ] Hiển thị lỗi dễ hiểu, nút Thử lại và Quay về dữ liệu trước; không yêu cầu đường dẫn hay lệnh.

## R5 — Backup và diễn tập trên Windows sạch

- [ ] Phân biệt backup/khóa của DB trước và DB phục hồi trên R2; kiểm tra R2, khóa và lịch trước khi bật backup tự động.
- [ ] Diễn tập: máy mới → R2 → dựng → dùng → đóng/mở app → backup → dựng lại → quay về; chỉ thao tác trên giao diện.
- [ ] Test sai khóa, sai MySQL, thiếu ảnh, cổng bận, mất mạng và tắt máy trong lúc chuyển.
