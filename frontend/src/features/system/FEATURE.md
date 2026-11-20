# Chức năng system

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).



## Bổ sung theo code hiện tại

`system-api.ts` gọi API trạng thái sẵn sàng; không có màn hình nghiệp vụ riêng.

Chỉ cho qua ReadyGate khi success=true, status=UP, database=UP và application=aluminum-manager. Dịch vụ khác cùng cổng hoặc response thiếu identity bị từ chối. tests/system-readiness.test.mjs kiểm thử hàm polling thật với Axios và đồng hồ giả; chạy node --test tests/system-readiness.test.mjs, không thêm dependency.

20/11: lưu version/revision/schemaVersion của backend đã xác minh trong bộ nhớ để menu tài khoản hiển thị. Metadata thiếu ghi Không xác định/unversioned, không tự nhận bản mới nhất. Không lưu thông tin xác thực vào metadata này.
