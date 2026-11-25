# Chức năng attendance

Tài liệu màn hình trong thư mục này; đọc trước khi sửa và cập nhật cùng thay đổi. Xem [mục lục](../../../../docs/feature.md) và [quy tắc chung](../../../../docs/requirements.md).

Bảng nhân viên, chấm công, tiền công và bảng trong modal cuộn ngang riêng ở cửa sổ nhỏ; modal dài cuộn phần nội dung và giữ nút xác nhận trong viewport.

### `/nhan-vien` — Nhân viên

| | |
|--|--|
| File | `features/attendance/EmployeesPage.tsx` |
| Ai dùng | **Xưởng only** |
| API | `/api/v1/employees`, pay rates |

**Chức năng**
- CRUD nhân viên (họ tên, SĐT, ngày vào, đang/ngừng làm). Ngày vào làm chỉ hiện trong biểu mẫu.
- Lịch sử mức công ngày, đơn giá tăng ca mỗi giờ và mỗi buổi theo hiệu lực. Khi thêm nhân viên hoặc mức công mới có thể đặt cả hai đơn giá.
- Có thể xóa mức công đặt trước khi mức đó chưa có hiệu lực và chưa được dùng trong chấm công.
- Lịch sử chỉ đánh dấu một mức đang áp dụng. Lưu mức công tính lại kỳ công đã lập nếu tháng và kỳ công còn mở và ngày hiệu lực giao với tháng đó.
- Lịch sử hiển thị mốc mới nhất nếu có nhiều bản ghi cùng ngày và gộp các khoảng liền nhau đã có hiệu lực nếu các mức tiền và giờ chuẩn giống nhau.

### `/cham-cong` — Chấm công

Khi mở trang, tháng mặc định là tháng bắt đầu của kỳ làm việc đang chọn ở Tổng quan Xưởng. Nếu Tổng quan đã chọn một tháng, bảng mở đúng tháng đó và khóa bộ chọn tháng; chỉ hiện các ngày nằm trong kỳ năm. Chưa bấm Mở tháng thì chỉ xem, không chấm công. Khóa tháng ở Tổng quan yêu cầu kỳ công tháng đó đã chốt và khóa chỉnh sửa chấm công.

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
- Khi thêm hoặc sửa tăng ca, chọn tính theo giờ hoặc một buổi. Theo giờ nhập số giờ; theo buổi mỗi bản ghi tính đúng một buổi. Có thể nhập đơn giá riêng; bỏ trống dùng mức tương ứng đã cấu hình theo ngày công. Đơn giá, đơn vị và thành tiền được lưu trên bản ghi tăng ca. Tổng giờ trong bảng công chỉ cộng tăng ca theo giờ.
- Các ô nhập tiền trên màn chấm công, gồm đơn giá tăng ca, hiển thị dấu phẩy phân tách hàng nghìn khi gõ; giá trị gửi lên API vẫn là số tiền.
- Lịch nghỉ lễ; quy tắc chấm (qua đêm, làm tròn…) — cấu hình, không hard-code
- Kỳ công đã chốt thì không sửa điểm danh, loại ngày, hay tăng ca

### `/tien-cong` — Tiền công

Khi mở trang, tháng mặc định là tháng bắt đầu của kỳ làm việc đang chọn ở Tổng quan Xưởng. Nếu Tổng quan đã chọn một tháng, kỳ công mở đúng tháng đó và khóa bộ chọn tháng. Phải bấm Mở tháng trước khi lập/chỉnh kỳ công, chốt kỳ công trước khi bấm Khóa tháng ở Tổng quan; không thể mở lại kỳ công sau khi tháng đã khóa.

Khi mở trang hoặc đổi tháng, kỳ đang mở tự tính lại theo dữ liệu chấm công mới nhất. Kỳ đã chốt dùng bảng kê đã lưu.

Nhập tổng tiền lương đã ứng của từng người trong kỳ mở. Hệ thống trừ khi tính thực lĩnh và kiểm tra số ứng trước khi chốt. Sau khi chốt, mở **Xem bảng kê** theo tên để xem công, giờ tăng ca đã duyệt, thưởng lễ, tiền ứng và tổng nhận.

Nút **In bảng công và lương** xuất tháng đang chọn thành bản in/PDF. Bảng chi tiết lương bỏ các khoản không phát sinh và giữ dòng thực lĩnh. Lịch sử ứng giữ ghi chú đã nhập. Cuối bản in có tổng hợp tháng và số đã chi nếu có. Kỳ chưa chốt ghi rõ là tạm tính.

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

## Cập nhật rà soát 19/11/2026

Xem `docs/audit-progress-20261119.md` để biết thay đổi và giới hạn kiểm thử. Sổ tháng định giá tồn theo lớp nhập mới nhất, không FIFO; tiền công/phụ cấp/khấu trừ theo tháng. Ứng lương mới có lịch sử ngày, tiền và ghi chú, ghi chi thực tế và trừ thực lĩnh; số ứng tổng cũ không được tự gán ngày. Báo cáo thiếu giá vốn không hiển thị lợi nhuận giả bằng cách coi giá vốn là 0. Các bài MySQL phải dùng cấu hình thử riêng, không thay đổi database thật.
