# Chức năng attendance

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

### `/nhan-vien` — Nhân viên

| | |
|--|--|
| File | `features/attendance/EmployeesPage.tsx` |
| Ai dùng | **Xưởng only** |
| API | `/api/v1/employees`, pay rates |

**Chức năng**
- CRUD nhân viên (họ tên, SĐT, ngày vào, đang/ngừng làm). Ngày vào làm chỉ hiện trong biểu mẫu.
- Lịch sử mức công ngày và một đơn giá tăng ca mỗi giờ theo hiệu lực, áp dụng cho mọi ngày.
- Có thể xóa mức công đặt trước khi mức đó chưa có hiệu lực và chưa được dùng trong chấm công.

### `/cham-cong` — Chấm công

| | |
|--|--|
| File | `features/attendance/AttendancePage.tsx` |
| Ai dùng | **Xưởng only** |
| API | attendance board, overtime, holidays, policies |

Trong **Lịch nghỉ lễ**, chọn ngày trong năm đang xem, nhập mức thưởng mỗi người và tích từng nhân viên đã nhận trực tiếp. Những người chưa tích được cộng thưởng vào kỳ tiền công của tháng có ngày lễ.

**Chức năng**
- Bảng tháng: bấm số ngày để mở phiếu từ 00:01 đến hết 23:59 của chính ngày đó. Tìm theo tên hoặc mã nhân viên. Sáng và chiều mỗi buổi 0,5 công. Tăng ca có checkbox và ô số giờ lẻ riêng, bỏ tích đặt số giờ về 0. Tổng công và tổng giờ tăng ca hiển thị riêng. Lưu xong khóa phiếu, mở lại trong ngày phải xác nhận.
- Dưới mỗi ngày có ký hiệu loại nghỉ: nghỉ cửa hàng, nghỉ lễ quốc gia, nghỉ lễ công giáo. Mỗi ngày nghỉ đặt được lương riêng; lương đó thay công ngày khi tính tiền
- Ô ngày xem/sửa buổi sáng, chiều khi phiếu điểm danh chưa khóa. Tăng ca và tiền thưởng riêng của từng nhân viên vẫn sửa được sau khi lưu điểm danh, cho đến khi kỳ công chốt. Tăng ca chỉ tính lương khi đã duyệt; thưởng theo ngày cộng riêng vào kỳ lương
- Lịch nghỉ lễ; quy tắc chấm (qua đêm, làm tròn…) — cấu hình, không hard-code
- Kỳ công đã chốt thì không sửa điểm danh, loại ngày, hay tăng ca

### `/tien-cong` — Tiền công

Khi mở trang hoặc đổi tháng, kỳ đang mở tự tính lại theo dữ liệu chấm công mới nhất. Kỳ đã chốt dùng bảng kê đã lưu.

Nhập tổng tiền lương đã ứng của từng người trong kỳ mở. Hệ thống trừ khi tính thực lĩnh và kiểm tra số ứng trước khi chốt. Sau khi chốt, mở **Xem bảng kê** theo tên để xem công, giờ tăng ca đã duyệt, thưởng lễ, tiền ứng và tổng nhận.

| | |
|--|--|
| File | `features/attendance/PayrollPage.tsx` |
| Ai dùng | **Xưởng only** |
| API | payroll periods calculate/close/pay/reopen/adjust |

**Chức năng**
- Kỳ theo tháng: tạo/lấy kỳ, tính lại từ chấm công
- Điều chỉnh từng dòng trước khi chốt; tiền thưởng từ chấm công hiển thị cột riêng, ngoài phụ cấp và tăng ca
- Chốt kỳ → không đổi theo hồ sơ mới; mở lại có điều kiện
- Thanh toán (một phần / đủ); tạo phiếu chi lương gắn xưởng

## Bổ sung theo code hiện tại

Chỉ vai trò Xưởng được thao tác nhân sự; Phân phối không có nhân viên/chấm công/lương.
