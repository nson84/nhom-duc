# Kế hoạch: phục hồi R2 hoàn toàn trong giao diện desktop

Ngày cập nhật: 22/11/2026. **Chỉ lập kế hoạch, chưa triển khai.** “Trên web” ở đây là giao diện React hiển thị trong ứng dụng desktop Tauri theo xác nhận của người dùng. Không yêu cầu hỗ trợ Chrome/Edge mở riêng tại localhost. Không gồm nhập sổ giấy.

## Kết quả người dùng cần thấy

Trên máy mới, mở ứng dụng → vào **Sao lưu** → nhập cấu hình R2 và khóa phục hồi → chọn bản → bấm **Khôi phục bản này** → xem tóm tắt bản đã kiểm tra → bấm **Dùng dữ liệu này**. Ứng dụng tự chuyển, tự khởi động lại backend và mở màn đăng nhập bằng dữ liệu phục hồi. Không mở PowerShell, không chạy lệnh, không tìm hoặc nhập đường dẫn tệp.

Người dùng vẫn phải **nhập các thông tin không có trong bản cài**: endpoint/bucket/R2 Access Key ID/Secret Access Key, khóa phục hồi cũ và tài khoản MySQL có quyền tạo DB thử. Nếu máy mới chưa có tài khoản ứng dụng, wizard cho đặt mật khẩu Admin mới và hiển thị cách thiết lập tài khoản Xưởng/Phân phối ngay trong giao diện.

## Phần đã có và điểm còn thiếu

- Backend đã tải bản R2, kiểm tra SHA-256, giải mã, dựng `aluminum_restore_*`, kiểm tra ảnh/FK/số dòng và sinh `verified-recovery.json`.
- Nút **Dựng DB từ R2** hiện dừng ở database thử. `start-recovered-backend.ps1` còn phải chạy thủ công để app dùng DB này.
- Tauri đã khởi động backend Java cùng app; cần mở rộng chính Tauri thành bộ điều khiển chuyển DB. Không thêm một Windows service riêng.
- Backend phục hồi dùng thư mục runtime khác. Cần làm cho đăng nhập mới, ảnh, khóa, token điều khiển backup và cấu hình R2 cùng trỏ đúng profile đang dùng; hiện một số đường dẫn còn mặc định theo `%APPDATA%/AluminumManager`.

## Luồng giao diện

1. **Chuẩn bị:** cấu hình R2, kiểm tra kết nối, nhập khóa phục hồi. Danh sách hiển thị thời điểm và kích thước bản sao; mặc định gợi ý bản mới nhất nhưng người dùng chọn bản.
2. **Khôi phục bản này:** nhập tạm tài khoản MySQL có quyền `CREATE DATABASE`. App tải và dựng vào DB riêng. Trong lúc chạy, hiện các bước tải → giải mã → dựng DB → kiểm tra ảnh/dữ liệu; không cho bấm trùng.
3. **Đối chiếu:** hiển thị nguồn R2, thời điểm backup, DB thử, số phiếu/tồn/công nợ theo hai sổ và số ảnh đã kiểm tra. Nêu rõ giao dịch sau thời điểm backup sẽ không tự xuất hiện.
4. **Dùng dữ liệu này:** hộp xác nhận cuối. Nếu đang dùng một DB khác, app tạo backup trước khi chuyển và giữ DB/ảnh cũ. Người dùng đặt hoặc xác nhận mật khẩu Admin mới trên máy mới ngay trong wizard.
5. **Tự chuyển:** Tauri dừng backend **do nó sở hữu**, chọn profile đã xác minh, khởi động Java với DB/ảnh/runtime tương ứng, kiểm tra `/ready` đúng DB/schema rồi điều hướng tới đăng nhập. Không bật lịch backup của DB phục hồi trước khi cấu hình R2 và khóa của profile mới được xác nhận.
6. **Sau chuyển:** trang Sao lưu hiển thị “Đang dùng bản phục hồi ngày…” và nút **Quay về dữ liệu trước**. Mọi lần mở app sau dùng đúng profile đã chọn.

## Cơ chế chuyển an toàn

- Backend trả về một ID cho lần dựng thử; Tauri chỉ nhận ID đó qua kênh nội bộ đã xác thực, không nhận đường dẫn tùy ý từ ô nhập. Kiểm tra lại profile, URL MySQL loopback `aluminum_restore_*`, ảnh đúng thư mục, schema và các số kiểm tra. Trường `verified: true` trong JSON không đủ làm bằng chứng.
- Mật khẩu MySQL/R2/khóa phục hồi không ghi vào log, URL hoặc JSON không mã hóa. Mật khẩu cần dùng để mở lại DB được lưu theo profile trong Windows Credential Manager hoặc hỏi lại trên giao diện. Không sao chép mật khẩu đăng nhập cũ từ backup.
- Trạng thái chuyển có ba mức bền vững: **đang dùng A → đang thử B → đã xác nhận B**. Ghi nguyên tử; chỉ xác nhận B khi backend mới sẵn sàng và trả đúng DB. Nếu app/máy tắt giữa bước thử, lần mở sau tự quay về A và hiện thông báo.
- Không tự giết backend của tiến trình khác. Nếu cổng 8080 đang do chương trình khác giữ, app dừng chuyển, giữ A và hiện hướng dẫn đóng chương trình đang chiếm cổng bằng giao diện Windows; không đưa lệnh PowerShell.
- Không xóa/đổi tên/ghi đè DB cũ. Nút quay lại dùng cùng quy trình kiểm tra và khởi động, không trộn giao dịch phát sinh trên hai DB.
- Backup của DB phục hồi phải được nhận diện riêng theo profile/khóa trên R2. Chỉ bật lịch sau khi người dùng kiểm tra R2 và xuất/lưu khóa phục hồi mới. Không coi việc dựng thử là đã đồng bộ cloud.

## Lát triển khai theo thứ tự

1. **Chuẩn hóa kết quả dựng thử:** ID, dấu kiểm tra bền vững, tóm tắt số liệu và API chỉ đọc để xác minh lại.
2. **Điều khiển backend trong Tauri:** khởi động theo profile, thư mục runtime/ảnh/token tương ứng, lưu MySQL credential, phát hiện cổng bận, kiểm tra ready đúng DB.
3. **Chuyển/rollback bền vững:** lưu trạng thái A/B nguyên tử, backup trước chuyển, tự phục hồi sau lỗi hoặc mất điện.
4. **Wizard React:** nút Khôi phục bản này → tóm tắt → Dùng dữ liệu này; tiến trình, lỗi dễ hiểu, mật khẩu Admin mới, DB đang dùng và nút quay lại.
5. **Backup sau chuyển:** phân biệt nguồn trên R2, xác nhận khóa và lịch, dựng lại bản mới trong diễn tập.

## Nghiệm thu

- Trên Windows sạch: cài app + Java/MySQL theo yêu cầu → chỉ dùng giao diện desktop để cấu hình R2, chọn bản, dựng, xác nhận và đăng nhập vào dữ liệu phục hồi. Không mở terminal hoặc nhập đường dẫn.
- Phiếu, tồn, công nợ, ảnh và tài khoản mới khớp bản được chọn. Sau khi đóng/mở app vẫn dùng DB phục hồi; backup mới trên R2 tải và dựng lại được.
- Sai khóa, thiếu ảnh, sai MySQL, R2 mất mạng, cổng bận và tắt máy giữa chuyển đều không ghi đè DB trước; giao diện cho thử lại hoặc quay về.
- Quay về DB trước hoạt động sau chuyển thành công; app chỉ rõ dữ liệu phát sinh trên DB nào.

## Ngoài phạm vi

- Không hỗ trợ thao tác chuyển DB từ Chrome/Edge chạy độc lập với ứng dụng desktop trong phiên này.
- Không nhập/gộp dữ liệu từ sổ giấy hoặc các giao dịch đã phát sinh sau bản backup.
- Không bỏ yêu cầu cất khóa phục hồi ngoài máy; mất khóa này thì không thể giải mã backup R2.
