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

## Bổ sung: thống kê hai sổ từ đầu năm đến hiện tại

**Trạng thái:** kế hoạch, chưa triển khai. Hạng mục này độc lập với việc phục hồi R2. Mặc định khoảng thống kê là **01/01 của năm dương lịch đang xem → ngày hiện tại** theo múi giờ nghiệp vụ `Asia/Ho_Chi_Minh` (tại ngày lập kế hoạch: 01/01/2026–24/11/2026). Khi dùng sau này, ngày kết thúc tự tăng theo ngày thực tế; không cố định năm 2026. Người dùng có thể chọn ngày kết thúc sớm hơn để đối chiếu. Chỉ đọc chứng từ đang có trong database đang dùng, không nhập sổ giấy.

### Màn hình và phạm vi

- Thêm mục **Thống kê từ đầu năm** trong Báo cáo, cho tài khoản Xưởng và Phân phối; mỗi tài khoản chỉ thấy sổ của mình. Hiển thị tên sổ, khoảng ngày, thời điểm làm mới và trạng thái dữ liệu đã chốt hay tạm tính. Không gộp hai sổ vào doanh thu chung vì chuyển nội bộ có thể bị đếm hai lần.
- Các thẻ tổng hợp: số phiếu nhập/bán đã xác nhận, kg nhập và kg bán/giao, doanh thu phiếu bán, tiền thực thu trong khoảng, tiền chi thực tế trong khoảng, phải thu khách và phải trả nhà cung cấp tại cuối ngày kết thúc. Tách số phát sinh trong kỳ khỏi số dư tại mốc kết thúc; ghi rõ đơn vị kg/tấn và đồng.
- Bảng theo tháng từ tháng 01 tới tháng hiện tại: số phiếu, kg nhập/bán, doanh thu, thực thu, chi hoạt động, chi trả nhập hàng và biến động công nợ. Tháng hiện tại là tháng chưa đủ ngày; tổng các tháng phải khớp tổng phát sinh của toàn khoảng.
- Xưởng có thêm thành phẩm giao, phế liệu bán, công thợ/ứng lương theo tháng và hao hụt/lãi quản trị **chỉ khi dữ liệu kiểm kê, giá trị tồn và tiền công đủ điều kiện**. Phân phối có chuyển nội bộ ra, tồn theo loại và giá trị tồn nếu có nguồn định giá hợp lệ; không hiện công thợ. Các ô thiếu cơ sở tính hiển thị “Chưa đủ dữ liệu”, không thay bằng 0.
- Có liên kết từ số tổng tới danh sách phiếu hoặc mục Sổ cuối năm tương ứng để kiểm tra nguồn. Bảng chi tiết tải phân trang; không đưa toàn bộ giao dịch lên trình duyệt một lần.

### Quy tắc tính và đối chiếu

1. Chọn phiếu theo **ngày chứng từ**, chỉ tính phiếu đã xác nhận và chưa bị đảo/hủy; không tính nháp. Thu/chi chọn theo **ngày giao dịch tiền**, gồm cả khoản trả cho phiếu cũ; không cộng thanh toán đã liên kết hai lần. Các lần trả nợ sau ngày kết thúc không đi vào số dư tại mốc đó.
2. Chuyển nội bộ là xuất của Phân phối và nhập nội bộ của Xưởng; hiện riêng, không cộng vào doanh thu bán ngoài hoặc nợ nhà cung cấp. Chi trả hàng nhập tách khỏi chi phí hoạt động và tiền công.
3. Số dư công nợ cuối ngày = số dư đầu ngày 01/01 + phát sinh tăng − các lần thanh toán tới hết ngày kết thúc, kể cả chứng từ từ kỳ trước. Không suy ra số dư đầu kỳ bằng 0. Kiểm tra với lịch sử thanh toán và bản chốt gần nhất; nếu dữ liệu lịch sử thiếu, báo thiếu cơ sở đối chiếu.
4. Kỳ kinh doanh có thể bắt đầu/kết thúc lệch năm dương lịch. Bộ lọc này vẫn là **năm dương lịch từ 01/01**, không thay đổi bộ lọc kỳ kinh doanh hiện có; hiển thị cảnh báo khi khoảng thống kê cắt ngang kỳ chốt. Sổ cuối năm vẫn in theo kỳ đã chọn của từng cửa hàng.
5. Dùng kiểu thập phân hiện có ở backend cho tiền/kg, thống nhất quy đổi và làm tròn tại lớp nghiệp vụ. API chỉ đọc, bắt buộc lọc shop theo quyền server; kiểm tra `from <= to <= hôm nay`, giới hạn tối đa một năm dương lịch và không nhận shop tùy ý từ tài khoản vận hành.

### Lát triển khai

1. **Chốt hợp đồng số liệu:** rà từng nguồn phiếu, thanh toán, thu chi, kiểm kê, công thợ và bản chốt; lập bảng ánh xạ công thức với query hiện có trong dashboard/finance/annual-book. Ghi rõ số nào là dòng tiền, số nào là doanh thu hoặc số dư. Cập nhật `docs/requirements.md` và `docs/api-specification.md` trước khi code.
2. **Backend đọc tổng hợp:** thêm API báo cáo theo shop và khoảng ngày, trả tổng, từng tháng, trạng thái đầy đủ dữ liệu và mốc chốt. Dùng truy vấn gộp tại database; không tái sử dụng tổng “đã thu của phiếu trong kỳ” của Dashboard làm dòng tiền trong kỳ. Không cần migration nếu nguồn dữ liệu hiện có đủ.
3. **Giao diện:** bộ lọc mặc định từ đầu năm, thẻ tổng và bảng tháng trong Báo cáo; trạng thái tải/rỗng/lỗi, giải thích công thức và liên kết đối chiếu. Giữ bố cục, phân trang, theme và quyền hiện hành.
4. **Đối soát:** fixture DB thử riêng gồm hai sổ, phiếu cũ trả trong năm, phiếu năm nay trả sau mốc, chuyển nội bộ, đảo phiếu, tháng trống, kỳ chốt qua 31/12 và dữ liệu thiếu định giá. So từng chỉ tiêu với phiếu/thu chi/Sổ cuối năm; kiểm tra quyền chéo shop, biên 01/01 và cuối ngày, năm nhuận, tải trên tập dữ liệu lớn. Không chạy thử trên database vận hành.

### Nghiệm thu

- Mở Báo cáo ngày bất kỳ trong năm: thấy số từ 01/01 tới ngày đó đúng sổ và đúng múi giờ; đổi ngày kết thúc tính lại, tải lại trang vẫn dùng mặc định rõ ràng.
- Tổng tháng khớp tổng phát sinh; doanh thu, dòng tiền và công nợ không lẫn nhau; chuyển nội bộ không thành doanh thu ngoài; công nợ phản ánh đúng thanh toán của phiếu từ năm trước.
- Số chưa đủ dữ liệu có lý do cụ thể. Tài khoản Xưởng/Phân phối không đọc được số của sổ kia qua UI hoặc API; Admin giữ quyền hiện hành.
