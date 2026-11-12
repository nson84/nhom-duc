# Nguyên tắc làm việc với dự án

Trước khi sửa code, tìm và đọc `FEATURE.md` trong từng thư mục frontend/backend liên quan, [docs/feature.md](docs/feature.md) để xác định các module liên quan và [docs/requirements.md](docs/requirements.md) để nắm quy tắc chung. Khi thay đổi liên quan quyền, dữ liệu, API hoặc giao diện dùng chung, đọc thêm các module `security`, `shop`, `common` và tài liệu tương ứng nếu có liên quan.

Sau mỗi thay đổi code, đối chiếu lại hành vi thực tế và cập nhật `FEATURE.md` của các thư mục bị ảnh hưởng trong cùng thay đổi; cập nhật `docs/requirements.md`, `docs/api-specification.md`, `docs/database-erd.md`, `docs/test-plan.md` và `README.md` khi quy tắc, API, dữ liệu, kiểm thử hoặc cách chạy tương ứng thay đổi. Nếu hành vi không đổi, kiểm tra và giữ tài liệu chính xác. Khi thêm/xóa màn hình, module hoặc route, tạo/cập nhật tài liệu trong thư mục và mục lục `docs/feature.md`.

Tài liệu ghi hành vi đã triển khai, quyền, giới hạn và liên kết code liên quan; phân biệt rõ phần dự kiến hoặc chưa hỗ trợ. Không khẳng định chức năng hoạt động chỉ vì có trong tài liệu: đối chiếu code và kiểm thử phù hợp. Không xóa dữ liệu hoặc thay đổi của người dùng khi cập nhật tài liệu.
