# Kế hoạch: phục hồi dữ liệu R2 ngay trong ứng dụng

Ngày cập nhật: 21/11/2026. Đây là kế hoạch, chưa triển khai tính năng. Phạm vi chỉ gồm việc chọn và sử dụng bản DB đã phục hồi trong app; không gồm nhập liệu từ sổ giấy.

## Mục tiêu người dùng

Trên máy mới: cài ứng dụng, cấu hình R2, chọn bản backup, nhập khóa phục hồi và tài khoản MySQL cần thiết, bấm **Dựng DB từ R2**. Sau khi xem kết quả kiểm tra, bấm **Dùng bản này**. Ứng dụng tự khởi động lại với dữ liệu đã phục hồi. Người dùng không chạy PowerShell, không chép tệp và không nhập đường dẫn `verified-recovery.json`.

## Hiện trạng

- Backend đã tải, giải mã và dựng DB riêng `aluminum_restore_*`, xác minh ảnh/FK/số dòng và sinh `verified-recovery.json`.
- Hiện phải chạy `start-recovered-backend.ps1` ngoài app để dùng DB đó. Restore trực tiếp vào DB đang chạy vẫn khóa.
- Tauri hiện tự khởi động backend mặc định hoặc dùng backend có sẵn ở cổng 8080. Lựa chọn DB phải được áp dụng **trước** lúc khởi động backend.

## Giao diện đề xuất

1. Màn **Sao lưu**: sau **Dựng DB từ R2**, hiển thị bản tóm tắt cố định gồm ngày bản sao, hai sổ, số phiếu, tồn, công nợ, ảnh đã kiểm tra và tên DB thử.
2. Nút **Dùng bản này** chỉ sáng khi kiểm tra đạt. Hộp xác nhận nêu rõ bản nào sẽ trở thành dữ liệu đang dùng, thời điểm backup và DB hiện tại được giữ lại.
3. App hiển thị tiến trình: sao lưu trước chuyển → dừng backend do app sở hữu → chọn DB đã xác minh → mở backend → kiểm tra ready/dữ liệu → hoàn tất. Nếu cần mật khẩu MySQL, hỏi ngay trong app.
4. Màn Sao lưu cho biết **Đang dùng dữ liệu nào** và có nút **Quay về dữ liệu trước**.

## Cách vận hành bên trong

- Dùng đường dẫn profile vừa do app tạo; không yêu cầu người dùng chọn file bằng tay. Nếu app khởi động lại sau sự cố, nó đọc lựa chọn đã lưu an toàn rồi hỏi mật khẩu MySQL nếu cần.
- Lưu lựa chọn DB bằng ghi nguyên tử. Chỉ đánh dấu chuyển thành công sau khi backend mới trả ready và kiểm tra đúng DB/schema/ảnh.
- Mật khẩu MySQL không ghi vào profile, log hoặc Git. Có thể lưu bằng Windows Credential Manager để lần mở sau không hỏi lại; khi sai thì yêu cầu nhập lại.
- Không ghi đè, đổi tên hoặc xóa DB và ảnh cũ. Không dừng backend do tiến trình khác sở hữu; nếu cổng 8080 bị chiếm, dừng thao tác và hướng dẫn đóng tiến trình đó.
- Chỉ nhận profile từ lần dựng thử đã được xác minh; kiểm tra lại đường dẫn loopback `aluminum_restore_*`, ảnh cùng thư mục, schema và dữ liệu trước khi kích hoạt. Không tin riêng cờ `verified: true` trong JSON.
- Khi chuyển lỗi hoặc app tắt giữa chừng, lần mở sau quay về DB trước và nêu lý do; không tự mở một DB chưa được kiểm chứng.
- Sau khi chọn DB phục hồi làm nguồn vận hành, phân biệt rõ bản backup/khóa của nó với DB cũ trên R2, xác nhận cấu hình R2 và khóa phục hồi trước khi bật lịch sao lưu. Bản sao cũ có thể thiếu giao dịch sau ngày backup; cảnh báo mốc thời gian trước khi xác nhận.

## Thứ tự triển khai

1. Kiểm tra profile và trạng thái DB đã dựng, thêm dữ liệu tóm tắt để người dùng đối chiếu.
2. Cho Tauri khởi động backend từ profile được chọn và xử lý thông tin MySQL trong app.
3. Thêm nút **Dùng bản này**, tiến trình chuyển, rollback và hiển thị DB đang dùng.
4. Phân biệt backup của DB phục hồi, bật lại lịch có kiểm soát và diễn tập trên Windows sạch.

## Tiêu chí hoàn tất

- Trên Windows sạch: cấu hình bucket cũ bằng cặp R2 key mới → chọn bản → dựng thử → bấm **Dùng bản này** → app mở lại với đúng phiếu, tồn, công nợ, ảnh; không dùng PowerShell hoặc đường dẫn thủ công.
- Mất mạng, sai khóa, sai MySQL, thiếu ảnh, cổng bận hoặc app tắt giữa chuyển: DB đang dùng không bị thay bằng bản lỗi, DB cũ còn nguyên.
- Nút **Quay về dữ liệu trước** hoạt động sau một lần chuyển thành công; backend và dữ liệu được kiểm tra lại.
- Backup mới của DB phục hồi tải lên R2, tải xuống và dựng lại được mà không lẫn với các bản của DB trước.

## Ngoài phạm vi

- Không nhập/gộp chứng từ từ sổ giấy hoặc từ DB cũ vào DB phục hồi.
- Không khôi phục bằng cách ghi đè trực tiếp DB đang hoạt động.
- Không tự tạo khóa R2 hay khóa giải mã khi người dùng đã mất chúng; vẫn cần cấu hình R2 và khóa phục hồi cũ.
