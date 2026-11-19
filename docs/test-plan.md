# Kiểm thử đã khóa và phần còn lại

## Đang có kiểm thử đơn vị

| ID | Việc đã khóa |
| --- | --- |
| INV-03 | Hệ số quy đổi bằng 0 bị từ chối |
| INV-05 | 10 cây × 3,5 kg làm tồn giảm 35 kg |
| INV-06 | Xuất vượt tồn bị từ chối |
| INV-07 | Phiếu không còn nháp thì không xác nhận lần nữa |
| INV-09 | Giá đã chốt không đi theo giá danh mục mới |
| INV-10 | Đảo phiếu đổi dấu biến động và chỉ đảo một lần |
| INV-11 | 100 kg × 70.000 và 50 kg × 80.000 ra giá vốn 73.333,333333 |
| INV-12 | Chiết khấu làm thành tiền âm thì bị từ chối |
| INV-13 | Chi phí chuẩn lấy theo giá nhập có hiệu lực đúng ngày phiếu, không theo giá hôm nay |
| INV-14 | Sửa giá nhập về sau không đổi chi phí chuẩn của phiếu đã xác nhận |
| INV-15 | Chênh lệch giá nhập = giá vốn thực tế − giá vốn chuẩn, dương là nhập đắt hơn chuẩn |
| INV-16 | Tạo phiếu nhập lệch giá: popup xuất hiện trước khi lưu, nêu giá cũ/mới và ngày hiệu lực; chọn chỉ lưu phiếu không đổi giá chuẩn |
| INV-17 | Đổi giá hai lần cùng ngày giữ hai mốc lịch sử; mốc mới nhất là giá hiện tại |
| INV-18 | Hủy phiếu nháp rồi chuyển thùng rác và khôi phục, không gặp lỗi backend khi `trashed_at` ban đầu trống |
| ATT-01 | Tháng 2 năm nhuận có 29 ngày |
| ATT-02 | Tháng 2 năm thường có 28 ngày |
| ATT-03 | Đủ công lấy hệ số trong cấu hình |
| ATT-04 | Nửa công lấy hệ số nửa ngày trong cấu hình |
| ATT-05 | 08:00–17:00 trừ 60 phút nghỉ bằng 8 giờ |
| ATT-06 | Ca 22:00–06:00 được tính sang ngày hôm sau khi cấu hình cho phép |
| ATT-07 | Tăng ca chưa duyệt không cộng vào tiền |
| ATT-08 | Điểm danh sáng hoặc chiều hoặc cả hai đều quy đổi một công |
| FAC-01 | Hao hụt trừ số kg phế liệu đã bán và kg kiểm kê cuối năm |
| FAC-02 | Tồn 10 tấn phân bổ 8 tấn lần nhập cuối và 2 tấn lần trước; từ chối khi lịch sử không đủ |
| SEC-01 | Phân phối bị chặn nhân sự/lương và vẫn được đi vào luồng thu chi |

## Cần MySQL trước khi phát hành

Có thể nạp bộ dữ liệu 2025 theo lệnh trong `README.md` để thử chốt năm và Sổ cuối năm. Bộ dữ liệu này được kiểm tra bằng chế độ `-DryRun` trước khi ghi: 30 phiếu nhập ngoài 1.000 tấn, 100 phiếu bán Phân phối, 12 cặp phiếu chuyển nội bộ 500 tấn, 80 phiếu bán Xưởng, tồn Phân phối 1 tấn, kiểm kê Xưởng 200 kg và hao hụt ước tính 100 kg. Cần xem báo cáo 2025 của cả hai sổ, đối chiếu công nợ còn mở và khoản thu/trả nợ đầu 2026, rồi thử chốt vào ngày người dùng chọn.

INV-01, INV-02, INV-04, INV-08 và toàn bộ luồng xác nhận phiếu trong transaction. ATT-08 đến ATT-12. FIN-01 đến FIN-08. APP-01 đến APP-10, trong đó cài đặt Windows cần Rust và MySQL trên máy sạch.

Kiểm tra thêm với MySQL: chốt Xưởng lưu tồn, lãi, phải thu và phải trả theo ngày chốt do người dùng chọn; thu/trả nợ sau ngày chốt không đổi bản chụp hoặc tính lãi lần hai. Chốt Phân phối với khoảng qua 31/12 tạo bản số dư đúng tên năm; kỳ sau bắt đầu ngay ngày tiếp theo, ngày trùng/khoảng hở bị từ chối. In Sổ cuối năm phải hiện mốc của từng sổ. Phiếu Xưởng thuộc kỳ đã chốt bị khóa, phiếu sau ngày chốt được phép ghi.

Kiểm tra Sổ cuối năm với dữ liệu nhiều năm: phiếu cũ còn nợ và khoản thu/trả nợ cũ xuất hiện đúng kỳ; đối chiếu nợ tại ngày chốt và nợ hiện tại sau khi khách trả muộn; tổng kg, tiền phiếu, đã thu và còn nợ từng khách phải khớp tổng từng hóa đơn. Kiểm tra thu/chi theo ngày, tồn kho, phiếu nhập nội bộ Xưởng không tính nợ, tiền công từng người/tháng và trạng thái thưởng lễ. Phân phối không in Công thợ. Kiểm tra quyền: Xưởng/Phân phối không lấy được sổ của bên kia, Admin không truy cập được sổ. Kiểm tra bản in A4 nhiều trang, tiếng Việt và bảng dài không cắt dòng.

Kiểm tra khách tạo hồ sơ năm 2026 nhưng có phiếu bán nhập lùi ngày tháng 02/2025: Sổ cuối năm 2025 của cả Phân phối và Xưởng phải hiện khách, tiền bán và chi tiết phiếu; khách chỉ tạo năm 2026 mà chưa có phiếu trước cuối kỳ 2025 không được tự xuất hiện trong sổ 2025.

Kiểm tra bộ lọc tên năm trên Tổng quan, Báo cáo, Thu chi: khi chọn kỳ đã chốt theo Tết âm lịch, hai ngày hiển thị phải khớp bản chốt và mọi phiếu, thu chi ngoài khoảng này bị loại. Kỳ chưa chốt tiếp theo phải bắt đầu đúng ngày sau kỳ trước; Sổ cuối năm dùng cùng mốc của từng cửa hàng. Chọn khóa năm 2026, đặt tên “Kỳ 02/2026–02/2027”, lưu ngày bắt đầu 02/2026, kiểm tra doanh thu và chi phí tính từ ngày đó. Lưu riêng ngày kết thúc 02/2027; kiểm tra kỳ vẫn chưa chốt cho đến khi xác nhận Chốt năm. Thử lưu ngày kết thúc trước ngày bắt đầu, sửa kỳ đã chốt và tạo khoảng hở với kỳ trước: API phải từ chối. Đối chiếu cả Xưởng và Phân phối; không sửa phiếu thật trong bước kiểm thử này.

Kiểm tra form phiếu chi ở cả hai sổ có đúng năm lựa chọn mới; lưu từng mục và xác nhận danh sách, chi tiết, Sổ cuối năm hiển thị tên khoản chi. Xác nhận bốn khoản đầu tính vào chi phí hoạt động, khoản sửa chữa vào bảo trì, và chi trả phiếu nhập vẫn chỉ ghi trên phiếu nhập.

Kiểm tra mở trang Tiền công hoặc tải lại trang nhiều lần liên tiếp khi kỳ tháng chưa có: các yêu cầu tạo/lấy kỳ đồng thời phải cùng trả về một kỳ, không hiện lỗi ràng buộc trùng `(period_year, period_month)`.
# Kiểm thử bổ sung cho báo cáo lãi gộp hóa đơn

- Nếu một dòng thiếu giá tham khảo hoặc giá tham khảo bằng 0, lãi của hóa đơn và tổng tháng phải để trống thay vì coi giá vốn bằng 0.
- Cặp sản phẩm/loại từng có biến động kho nhưng hiện tồn 0 vẫn phải xuất hiện trong popup kiểm kê Phân phối để ghi nhận bằng chứng đã cân.

## Kiểm tra màn hình tổng hợp theo yêu cầu

- Khách mua: bốn tổng số kg giao, tiền hàng phải thu, tiền đã thu thực tế, nợ; lọc danh sách khách và mở từng hóa đơn xem dòng hàng, thanh toán.
- Tồn kho Phân phối: tổng tiền hàng nhập lấy dòng phiếu nhập còn hiệu lực, không lấy giá trị tồn sổ bằng 0; tồn kg giảm khi xác nhận bán. Xưởng: tổng vụn bằng vụn đã bán cộng vụn chưa bán trong bản kiểm kê, phiếu bán không trừ tồn.
- Đồng bộ danh mục lần hai giữ giá bán, mô tả và trạng thái riêng của Xưởng; chỉ tên/ảnh SP và giá nhập tham khảo được cập nhật. Giá bán mới vẫn cần xem hoặc xác nhận giữ nguyên.

## Kiểm thử responsive

Xem [biên bản rà soát responsive](responsive-audit.md). Quét các route chính ở 1920×1080, 1600×900, 1366×768, 1280×720, 1024×768 và 900×600; kiểm tra chiều rộng trang và vùng nội dung, thanh cuộn riêng của bảng nhiều cột, menu Drawer dưới 1200 px, modal nhập/bán thấp 600 px và footer luôn dùng được. Kiểm tra sáng/tối cho modal, trạng thái dữ liệu dài, dropdown, thông báo, bản xem trước PDF và các vai trò Admin/Phân phối/Xưởng khi có phiên tương ứng.

Trong môi trường dev, script cấp mật khẩu khởi tạo `1234567` và buộc đổi; bản production sinh mật khẩu ngẫu nhiên riêng cho tài khoản mới, form không tự điền. Khởi động lại không ghi đè hash hoặc trạng thái tài khoản đã có.

# Kiểm thử lọc và chốt tháng

- Chọn kỳ bắt đầu 18/02/2025: Tổng quan mặc định tháng 02/2025, trạng thái Chưa mở; bấm Mở tháng để ghi phiếu. Tổng quan, phiếu kho, Thu chi, Báo cáo và Khách mua chỉ lấy ngày 18–28/02; phiếu ngày 17/02 bị chặn.
- Không thể mở tháng 03/2025 trước khi khóa 02/2025. Sau khi khóa tháng 02, không thể sửa/đảo phiếu tháng 02; mở tháng 03 rồi mới tạo phiếu tháng 03. Chốt năm cần khóa tháng cuối kỳ.
- Chọn năm 2025, mở Chấm công và Tiền công: mặc định tháng bắt đầu kỳ, không tự tạo kỳ công 2026. Chọn tháng khác trong cùng kỳ khi bỏ bộ lọc tháng.
- Xưởng phải chốt kỳ công trước khi chốt tháng; Phân phối không cần kỳ công. Sau chốt, backend từ chối tạo/sửa/xác nhận/hủy/đảo phiếu, sửa chấm công và mở lại kỳ công tháng đó.
- Thanh toán nợ của phiếu tháng đã chốt được phép ghi vào tháng mới đang mở; ngày thanh toán vẫn là ngày thực tế. Chốt tháng không thay đổi số dư tồn/công nợ hoặc trạng thái chốt năm.

# Rà soát backup/restore 15/11/2026

- Chạy `backend/mvnw.cmd test`: kiểm tra phân quyền GET/POST/PUT/PATCH/DELETE của `/api/v1/backups/**` cho ba vai trò và chặn restore trực tiếp trước khi gọi service.
- Chạy `frontend/npm run build` và kiểm tra cảnh báo khôi phục tạm khóa chỉ hiện ở màn Admin.
- Kiểm tra không đăng nhập nhận 401 bằng HTTP trên backend local; không gọi restore với database làm việc.
- Cần thêm integration test MySQL trên database thử riêng, restore V2 và chuyển database có thể quay lại trước khi mở lại chức năng khôi phục.
- Bootstrap: ba tài khoản mới trên bản production nhận mật khẩu ngẫu nhiên riêng trong tệp quyền chủ máy; môi trường dev vẫn dùng `1234567`; tài khoản cũ không bị cập nhật.
# Kiểm thử backup mã hóa và R2

- `EncryptedBackupCodecTest`: round-trip Unicode, GZIP, sai khóa và archive bị sửa.
- `RecoveryKeyServiceTest`: khóa tồn tại sau khởi động lại, xuất/nhập máy mới, từ chối ghi đè.
- `R2CloudServiceTest`: khóa không trả lại từ API/cấu hình và endpoint ngoài R2 bị chặn.
- `CloudBackupServiceTest`: mock S3, upload archive + manifest, xác minh và không upload trùng.
- `StagedRestoreServiceTest`: URL database thử không trỏ vào database đang dùng.
- `StagedRestoreMysqlIT` chạy riêng khi đặt `ALUMINUM_TEST_DB_URL` (phải trỏ tên `aluminum_backup_test` trên `127.0.0.1`), `ALUMINUM_TEST_DB_USERNAME`, `ALUMINUM_TEST_DB_PASSWORD`; test tạo một database mới `aluminum_restore_*`, không xóa database nào. Không chạy mặc định nếu thiếu biến môi trường.
- Đã chạy một lần với tài khoản ứng dụng `aluminum_app`: MySQL từ chối `CREATE DATABASE` (1044). Dùng tài khoản kiểm thử có quyền tạo DB cho bài này; không dùng database đang làm việc.
- Test cần chạy riêng trước nghiệm thu trên PC B: cấu hình token R2 mới, tạo backup chứa dữ liệu cả hai sổ và ảnh, tải archive về máy khác, nhập recovery key, dựng database thử, đối chiếu phiếu/công nợ/tồn/ảnh. Chưa có test chuyển database hoạt động và rollback; API restore trực tiếp vẫn khóa.
# Bản in khách hàng và công thợ

- Ở chi tiết khách Phân phối/Xưởng, lọc ngày lập phiếu và trạng thái thanh toán, in PDF: phiếu mới trước; các lần trả ghi đúng ngày; tổng tiền/đã trả/còn nợ cuối bản bằng tổng đúng các phiếu lọc. Phiếu nội bộ cộng kg nhưng không cộng giá chuyển vào công nợ.
- Ở Tiền công Xưởng, chọn tháng rồi in: đối chiếu từng ngày sáng/chiều, số công, tăng ca đã duyệt, thưởng ngày với bảng chấm công; đối chiếu khoản lương và tổng thực lĩnh với bảng kỳ công. Kỳ chưa chốt ghi tạm tính; tiền ứng cũ không có ngày thì không tự tạo ngày giả.

## Bổ sung 19/11/2026

Chạy frontend npm run build và backend mvnw.cmd test. MySQL riêng: sao chép scripts/test-mysql-env.local.example.ps1 thành scripts/test-mysql-env.local.ps1, điền cục bộ rồi chạy scripts/test-mysql.ps1. Chưa coi các ca tích hợp đã đạt khi chưa cấu hình và chạy. Xem audit-progress-20261119.md.

## Audit bổ sung 19/11 — đã thực thi trên MySQL riêng

StockLifecycleMysqlIT có 12 ca: sửa và rollback, thanh toán lặp/đồng thời, Xưởng giữ tồn, đảo đơn/cặp nội bộ, lãi chuyển nội bộ, giá tham khảo0, nợ kỳ sau và snapshot tháng. StagedRestoreMysqlIT có 2 ca SQL/ảnh và từ chối FK mồ côi. 74 unit test và 14 integration test đạt. Chưa chạy UI/PDF/NSIS. Xem audit-followup-20261119.md để không nhầm coverage hiện tại là toàn bộ checklist.

## Chuyển tồn theo loại

StockLifecycleMysqlIT thêm4 ca theo hai sổ: chốt200kg+400kg và đọc đầu tháng/năm kế tiếp; tổng600kg, từng loại giữ nguyên, reload không tăng phiếu, bản năm được ưu tiên khi cùng chốt tháng. Bỏ qua một tháng chưa chốt phải báo không có nguồn liền trước. FlywayV42 chỉ chạy trên server thử13307.

Kết quả lượt chuyển tồn:74 unit test,18 integration test MySQL riêng,0 lỗi; frontend build đạt. Flyway42 migrate thành công trên DB thử. Chưa áp dụng V42 vào database thật, chưa kiểm thử UI trực quan.

Regression monthlyCountNeedsOnlyRawKgAndAnnualScrapCanStillBeRecordedLater: chỉ gửi kg nguyên liệu, openingValue/scrapKg/scrapValue null; lưu thành công, profit chưa đủ dữ liệu để null; không tạo bản chốt năm hoặc giả bằng chứng kiểm kê năm.

Nếu đã nhập kg vụn nhưng chưa có giá trị, endingValue và profit tháng để null; kg vẫn lưu và chuyển theo loại, không biến giá trị vụn chưa biết thành0 ở tháng sau. Đã kiểm tra74 unit test,6 ca MySQL liên quan và build frontend. Không kiểm thử UI trực quan trong lượt này.

## Audit tại b290e3c — bộ hồi quy bổ sung

Lệnh backend đầy đủ: `mvnw.cmd '-Dtest=*Test,*MysqlIT' package` với ALUMINUM_TEST_DB_URL/USERNAME/PASSWORD của server thử riêng, APPDATA riêng. Chạy trong bản sao để không kích hoạt DevTools backend đang làm việc. BackupBusinessMysqlIT so SQL/ảnh/timestamps và18 bảng chính của hai sổ; kiểm tra thiếu ảnh và giới hạn FK tác giả kỳ công. CloudBackupServiceTest có ca mất mạng/retry dùng S3 mock.

StockLifecycleMysqlIT bổ sung7 ca: thu đủ/retry, nhiều dòng, từ chối5 thao tác trong tháng khóa, chuyển tồn tháng12→tháng1 với đổi tên/ngừng dùng/cô lập shop, lương+OT+ứng+trả+khóa, race khóa tháng/ghi phiếu, hóa đơn30/10 thu30/10 và10/11. V43 khớp CHECK với thực lĩnh đã trừ ứng trong code. Không được bỏ CHECK để cho test qua hoặc sửa tiền lịch sử.

Xem audit-b290-20261119.md để biết kết quả cuối, các log tái hiện và phạm vi NOT_TESTED. Smoke responsive3 trang không thay thế UI/PDF nhiều dòng hoặc bộ cài NSIS. Recovery end-to-end vẫn NOT_VERIFIED, restore production vẫn503.

Kết quả cuối audit b290e3c:75 unit +27 MySQL integration =102 test,0 lỗi,0 skip; package JAR và frontend build đạt. Flyway43 chỉ chạy trên DB thử. Không đổi định nghĩa thu/nợ: ca30/10→10/11 xác nhận doanh thu152m tháng10, tiền thực nhận50m tháng10 và102m tháng11, đã thu từ hóa đơn tháng10 đến nay152m, nợ snapshot102m giữ nguyên, hiện tạiPAID. Không coi kết quả này là nghiệm thu PDF/NSIS hoặc recovery toàn bộ.
