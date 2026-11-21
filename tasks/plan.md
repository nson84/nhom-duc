# Kế hoạch: chuyển sang dữ liệu phục hồi trong app và bắt đầu sổ từ giấy

Ngày lập: 21/11/2026. Đây là kế hoạch, chưa triển khai tính năng. Phần mềm phục vụ một người trên một máy Windows, MySQL local và R2.

## Quyết định phạm vi

1. **Phục hồi trong app:** sau khi dựng và kiểm tra bản R2 trên `aluminum_restore_*`, người dùng có thể chọn dùng database đó trong app, khởi động lại và quay về database trước nếu cần. Không ghi đè hoặc xóa database cũ.
2. **Chuyển từ giấy:** ưu tiên chốt kỳ đang ghi trên giấy, chọn một ngày bắt đầu kỳ mới và nhập các số đầu kỳ đã đối chiếu. Tiếp tục nhập giao dịch mới bằng màn phiếu hiện có. Nhập lại toàn bộ giấy cũ chỉ khi cần báo cáo lịch sử hoặc thiếu chứng từ để định giá/đối chiếu.
3. Phiên đầu của chuyển từ giấy là nhập tay có hướng dẫn và đối chiếu. Nhận dạng ảnh giấy/OCR là giai đoạn sau, luôn phải được người dùng kiểm tra trước khi ghi DB.

## Bằng chứng và giới hạn hiện tại

- `StagedRestoreService` đã tải/giải mã, dựng DB riêng và tạo `verified-recovery.json`; `start-recovered-backend.ps1` hiện phải chạy ngoài app. Nút restore trực tiếp trả 503.
- Tauri tự khởi động backend mặc định hoặc dùng backend có sẵn ở cổng 8080. Việc chọn profile phục hồi phải xảy ra **trước** khi backend mặc định khởi động.
- Phiếu nhập/bán đã cho chọn ngày quá khứ khi tháng/kỳ còn mở. Kỳ đã khóa từ chối ghi; không nới quy tắc này để nhập giấy cũ.
- Tồn đầu kỳ hiện đọc từ kỳ chốt liền trước, không có màn nhập lớp tồn đầu kỳ theo loại khi chưa có lịch sử nhập. Báo cáo công nợ chuyển kỳ hiện dựa trên phiếu cũ.
- Bản backup không chứa bản nháp chỉ lưu trong trình duyệt. Chỉ dữ liệu đã ghi DB mới được R2 bảo vệ.

## Luồng phục hồi mong muốn

R2 → chọn `.ndbak` → nhập khóa phục hồi + quyền tạo DB thử → dựng DB riêng → đối chiếu số liệu/ảnh → **Dùng bản này** → app kiểm tra profile và DB lần nữa → tạo bản backup trạng thái hiện tại → dừng backend do app sở hữu → lưu lựa chọn profile bằng ghi nguyên tử → khởi động backend với DB/ảnh đã xác minh → kiểm tra `/ready` và số liệu mẫu → báo thành công. Nếu bất kỳ bước nào lỗi, app quay về cấu hình cũ và hiện lỗi cụ thể. Có nút **Quay về dữ liệu trước**.

### Quy tắc an toàn phục hồi

- Chỉ chấp nhận profile do bước dựng thử tạo, đường dẫn DB loopback `aluminum_restore_*`, ảnh đúng thư mục staging, schema và số dòng vẫn khớp. Không tin riêng trường `verified: true` trong JSON.
- Không tự đóng/giết backend do tiến trình khác sở hữu. Nếu cổng bị chiếm, hiển thị hướng dẫn đóng tiến trình đó và giữ nguyên lựa chọn cũ.
- Mật khẩu MySQL không ghi vào JSON, log hay Git. Nếu cần dùng lại khi mở app, lưu trong Windows Credential Manager; khi thiếu hoặc sai, hỏi lại trong app.
- Giữ database và ảnh cũ. Không bật backup tự động cho DB phục hồi trước khi người dùng xác nhận đây là DB vận hành. Sau khi xác nhận, kiểm tra khóa phục hồi mới và R2; phân biệt rõ bản backup của từng DB để tránh chọn nhầm.
- Khôi phục một bản cũ không tự gộp các giao dịch đã phát sinh sau thời điểm backup. Cần cảnh báo chênh lệch thời gian và chọn nguồn vận hành chính thức.

## Luồng chuyển sổ giấy mong muốn

### Mốc chuyển sổ

Chốt kỳ giấy tại ngày T. Ghi lại theo **Xưởng** và **Phân phối**: tồn kg và giá trị theo từng loại, công nợ từng khách/NCC, số tiền đã thu/trả và chứng từ còn mở, tiền/chi phí chưa ghi, hàng đang làm và phế liệu nếu có. Hai bên đối chiếu tổng giấy trước khi nhập app. Dữ liệu phát sinh từ T+1 nhập bằng các màn hiện có.

### Dữ liệu đầu kỳ

- Tồn đầu kỳ cần nguồn riêng theo shop, sản phẩm/loại, kg, giá trị/đơn giá và ngày hiệu lực; không tạo phiếu nhập giả để tăng tồn. Kiểm tra điều kiện định giá và chốt kỳ.
- Nợ đầu kỳ cần nguồn riêng theo khách/NCC, số còn nợ, ngày và tham chiếu sổ giấy; thanh toán sau T giảm đúng nợ mà không tạo doanh thu/chi phí mới hoặc gắn sai phiếu.
- Xác định có cần số dư tiền mặt/ngân hàng, ứng lương, lương phải trả, hàng dở dang và vụn hay không trước khi mở rộng schema. Giá trị chưa rõ phải hiện **chưa đối chiếu**, không tự coi bằng 0.
- Mỗi lô nhập có mã tham chiếu giấy, người nhập, thời điểm, trạng thái nháp/đã xác nhận và cảnh báo trùng. Chỉ bản đã xác nhận mới ảnh hưởng báo cáo và được backup.

### Cách dùng sau chuyển sổ

Nhập chứng từ mới vào app hằng ngày; có thể giữ giấy song song một kỳ để đối chiếu cuối ngày/tuần. Chỉ đóng giấy khi tồn, nợ, thu/chi và số phiếu khớp. Không nhập lặp cùng giao dịch ở cả tính năng đầu kỳ và phiếu phát sinh.

## Thứ tự triển khai

1. Hoàn thiện chuyển DB phục hồi trong app, rollback và diễn tập trên Windows sạch.
2. Chốt mẫu đối chiếu giấy, ngày T và phạm vi số dư đầu kỳ. Không sửa DB thật trong bước khảo sát.
3. Làm từng lát dọc: tồn đầu kỳ → nợ đầu kỳ → màn hướng dẫn/đối chiếu → báo cáo/chốt kỳ → backup/restore rehearsal.
4. Chỉ sau khi đối chiếu thực tế đạt mới cân nhắc nhập lịch sử hàng loạt hoặc OCR.

## Tiêu chí hoàn tất chung

- Mất máy: cài trên Windows khác, cấu hình R2, dựng bản, chọn dùng trong app, khởi động lại, thấy đúng phiếu/tồn/nợ/ảnh; quay lại bản trước được; không mất DB cũ.
- Chuyển giấy: tổng đầu kỳ theo từng shop khớp biên bản giấy; một giao dịch sau T chỉ tính một lần; báo cáo và chốt tháng/năm không tự tạo lãi hoặc tồn giả; backup R2 rồi dựng thử vẫn giữ dữ liệu đầu kỳ và nguồn giấy.
- Lỗi mạng/R2, sai khóa, sai mật khẩu MySQL, thiếu ảnh, cổng 8080 bận và app tắt giữa quá trình đều dừng an toàn, không đổi database đang dùng.

## Câu hỏi cần chốt trước khi làm phần giấy

- Ngày T dự kiến và kỳ giấy hiện tại đã chốt đến đâu?
- Giấy gồm những loại nào: nhập/bán, thu/chi, nợ, tồn, nhân sự/lương, hàng dở dang/vụn?
- Có cần xem báo cáo các kỳ giấy cũ trong app, hay chỉ cần số đầu kỳ đúng để làm tiếp?
- Có mẫu giấy thật để đối chiếu trường dữ liệu và quy tắc tính không? Có thể dùng mẫu đã che thông tin nhạy cảm.
