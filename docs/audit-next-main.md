# Audit tiếp theo tại main b361221

HEAD bắt đầu: b361221c9bd76845b02330beb258287f7be38d95; working tree sạch. Lượt này chỉ sửa workspace, không commit/merge/push. Không thay migration V1–V43, phương pháp định giá hoặc công thức tài chính; không chạy thử trên aluminum_app. MySQL riêng13307, database UUID aluminum_test_*/aluminum_restore_*, APPDATA và ảnh riêng. Backend được build/test trong .runtime/audit-next/backend để không kích hoạt DevTools backend thật.

## Phân loại và lỗi tái hiện

| Hạng mục | Trạng thái | Bằng chứng / xử lý |
|---|---|---|
| FK tác giả trong backup | DESIGN_DECISION | Có4 FK nghiệp vụ sang admin_users: payroll_periods.closed_by_admin_id, payroll_periods.reopened_by_admin_id, attendance_day_locks.locked_by_admin_id, customer_leads.contacted_by_admin_id. audit_logs.admin_user_id cũng tham chiếu nhưng cả bảng audit_logs là local và đang bị loại khỏi archive. |
| Chốt năm/snapshot và ghi phiếu | BUG_CONFIRMED | Ba ca hai transaction dùng latch: year-draft, year-payment, month-snapshot. Trước sửa close hoàn tất dù writer đã kiểm tra ngày nhưng chưa ghi. Sau sửa writer giữ khóa đọc shop đến commit, các điểm chốt lấy khóa ghi trước khi đọc tổng. |
| ReadyGate nhận nhầm dịch vụ | BUG_CONFIRMED |3 test đỏ khi JSON trả status UP nhưng application khác/thiếu hoặc database DOWN. system-api.ts bổ sung identity aluminum-manager và database UP;5 test khởi động đạt. Không đổi API hoặc redesign màn hình. |
| V42→V43 | ALREADY_FIXED, đã kiểm thử lịch sử |6 fixture thật trên schema42, sau đó Flyway nâng43. Không ứng, có thưởng/phụ cấp/khấu trừ, kỳ đã chốt/đã trả nâng được; tổng cũ chưa trừ ứng bị từ chối và dữ liệu/CHECK cũ nguyên vẹn. Không sửa V43. |
| Backup nhỏ/public workflow | ALREADY_FIXED, kiểm thử đạt |BackupBusinessMysqlIT dùng createManualBackup/verifiedArchive, SHA256, ảnh PNG, cả hai sổ, trả một phần/đủ, chuyển nội bộ có cặp, công/ứng/trả, phế liệu, raw layers, snapshot tháng/năm. Đối chiếu đầy đủ rows của26 bảng, ảnh và timestamps. |
| Backup gần/vượt giới hạn | ALREADY_FIXED, đã chạy workload |33.280.000 ký tự payload/520 dòng khôi phục đủ;34.560.000 ký tự bị từ chối. Log FAILED, bản SUCCESS cũ còn nguyên, cloud mock chỉ nhận lời gọi của bản hợp lệ. Không đổi định dạng backup. |
| Hai request snapshot/đảo nội bộ | ALREADY_FIXED, kiểm thử đạt |Hai transaction thực sự, latch bắt đầu chung, timeout giới hạn; kiểm tra số bản chốt, trạng thái tháng, số phiếu đảo, tồn hai sổ và không phát sinh dòng tiền. |
| Restore trực tiếp production | ALREADY_FIXED |BackupController vẫn trả RESTORE_UNAVAILABLE, không gọi phục hồi database hoạt động. |
| Desktop NSIS/Java thiếu/máy sạch | NOT_TESTED |Không có Cargo trong PATH/Windows VM để nghiệm thu executable và installer. Không thay kiến trúc hoặc đóng gói thêm Java/MySQL. |

## Vì sao chưa tự sửa phục hồi tác giả

Thông tin phải giữ: ID tác giả gốc, thời điểm chốt/mở lại/khóa ngày/liên hệ, nội dung nghiệp vụ, FK nhất quán. Tài khoản đăng nhập, mật khẩu và phiên của máy mới là dữ liệu riêng máy. Một ID số giống nhau ở hai máy không chứng minh cùng người. audit_logs hiện cũng không được đưa vào backup nên chưa thể tuyên bố khôi phục đầy đủ lịch sử thao tác.

| Phương án | Ưu điểm | Nhược điểm / tương thích | Quyết định |
|---|---|---|---|
| Đưa nguyên admin_users và phiên vào backup | FK và ID gốc giữ được |Mang hash/quyền/phiên cũ sang máy mới, có thể đè hoặc nhập nhầm tài khoản |Không tự triển khai, cần chính sách bảo mật được duyệt |
| NULL, xóa FK hoặc ID giả |Import qua được |Mất tác giả hoặc gán sai người, phá toàn vẹn lịch sử |Không chấp nhận |
| Lưu định danh tác giả lịch sử riêng với metadata và ánh xạ được kiểm tra |Giữ lịch sử mà không mang mật khẩu/phiên; tách đăng nhập máy mới |Cần schema/liên kết mới, quy tắc xử lý ID va chạm và tương thích archive cũ |Đề xuất xem xét; dừng triển khai theo yêu cầu |
| Nhập tác giả thành tài khoản vô hiệu hóa, không nhập mật khẩu/phiên |Có thể giữ ID và FK cũ |Vẫn thay chính sách tài khoản; ID trùng máy mới phải được xử lý minh bạch |Cần phê duyệt, không tự làm |

Kết luận: recovery end-to-end vẫn **NOT_VERIFIED**. Test kỳ công đóng phải tiếp tục từ chối FK, không được sửa test thành bỏ qua tác giả. Test máy mới có tài khoản khác cùngID phải không tự gắn tác giả cũ vào tài khoản đó. Không thay mật khẩu, role, tài khoản hoặc liên kết lịch sử để báo đạt.

## Nâng cấp V43 và báo cáo số liệu lệch

Chạy scripts/v43-preflight.sql trên bản sao V42: chỉ SELECT, trả ID dòng/nhân viên/kỳ, trạng thái kỳ, các thành phần, tổng đã lưu, kết quả theo công thức hiện hành, chênh lệch và tiền đã trả của toàn kỳ (không gán số trả theo từng nhân viên).

Fixture advance: dòng101, tổng cũ450.000, công thức hiện hành400.000, lệch50.000. Fixture closedPaidAdvance: dòng101, tổng cũ480.000, công thức430.000, lệch50.000. V43 từ chối cả hai, không UPDATE lương. Một ALTER nguyên tử giữ CHECK cũ khi thất bại; Flyway có thể vẫn ghi entry43 thất bại trong schema history. Đây không phải rollback toàn bộ metadata Flyway. Không tự repair hoặc bỏ CHECK; dựng lại bản sao trước mỗi diễn tập và đối chiếu trước khi nâng cấp thật.

## Dữ liệu lớn

Lượt workload đầu: tạo/kiểm tra/khôi phục gần giới hạn rồi thử vượt giới hạn trong15.558ms. Tổng peak của các vùng heap918.538.200 bytes, đo bằng MemoryPoolMXBean; các peak có thể ở các thời điểm khác nhau, không phải phép đo RSS hay peak heap đồng thời. Toàn ca gồm migrate25,39s. Kết quả phụ thuộc máy/JVM. Chưa chứng nhận máy ít RAM; dump vẫn trong RAM, chưa streaming. Không tự thay định dạng archive. Không xóa bản sao cũ; R2 chỉ dùng mock, không token thật.

## Lệnh và bằng chứng

Logs cục bộ: .runtime/audit-next/migration-upgrade.log, closing-races-red.log, closing-backup-green.log, regression.log, frontend-build.log. Frontend regression: node --test tests/system-readiness.test.mjs (đỏ3 ca trước sửa, xanh5 ca sau). Maven dùng ALUMINUM_TEST_DB_URL loopback13307/aluminum_backup_test và tài khoản thử; các test tự tạo UUID database, không ghi database mang tên nghiệp vụ.

## Giới hạn cần nghiệm thu riêng

Không coi unit/build là nghiệm thu UI/PDF/NSIS. Cần kiểm tra đủ role, modal nhiều dòng,5 viewport, sáng/tối, chữ dài; PDF hóa đơn/phiếu nhập/công nợ/lương/sổ nhiều trang và VietQR. Restore trên máyB và rollback/chuyển kết nối chưa được nghiệm thu. Không bật endpoint restore, không chuyển DB đang dùng.

Không có quyết định tự động sửa lương lịch sử, mang tài khoản cũ sang máy mới, hoặc chỉnh công thức/quy trình chốt. Tất cả các mục chưa đủ điều kiện giữ NOT_TESTED/NOT_VERIFIED, không đổi thành đạt chỉ vì build thành công.

## Kết quả cuối

- Backend115 ca khác nhau đạt:75 unit và40 MySQL integration,0 failures/errors/skips. Lượt đầy đủ mvnw.cmd '-Dtest=*Test,*MysqlIT' package đạt114 ca trước khi thêm ca snapshot cũ. Sau khi test mới đỏ và sửa guard, chạy mvnw.cmd '-Dtest=*Test,StockLifecycleMysqlIT' package đạt105 ca (75unit+30lifecycle). Các suite backup/staging/upgrade không bị thay đổi sau lượt đầy đủ; XML cuối tổng hợp115 ca đã đạt.
- Lỗi snapshot cũ đã tái hiện riêng: transaction tạo consistent snapshot trước close, sau close commit vẫn thu được vào ngày năm cũ. Guard bổ sung FOR SHARE đọc hiện hành year_end_balances; test từ chối khoản thu này, không tạo payment và giữ nợ chốt152m. Không đổi chính sách thu nợ kỳ sau: thu ngày trong kỳ mới đang mở vẫn được phép, đã có regression.
- Frontend node --test tests/system-readiness.test.mjs đạt5/5; npm run build đạt, còn cảnh báo chunk lớn. JAR mới đóng gói trong .runtime/audit-next/backend/target, không ghi đè target/JAR của backend thật.
- UI riêng: frontend5174 gọi backend18080 trên database aluminum_test_UUID13307 với APPDATA riêng, ảnh PNG giả và40 phiếu mỗi sổ. Backend UI dùng JAR b361221 có cùng DTO; code khóa mới được chứng minh qua integration test source hiện tại. Không áp dụng migration ở UI hoặc database thật (SPRING_FLYWAY_ENABLED=false cho UI test).
- Phân phối10 trang ×5viewport ×2theme=100 lượt; Xưởng7 trang đặc thù/chính ×5viewport ×2theme=70 lượt. Không phát hiện tràn ngang toàn trang trong170 lượt này. Có bảng bán30 dòng/trang,40 bản ghi, tên khách/sản phẩm tiếng Việt dài. Admin Sao lưu đo thêm5 viewport; modal cấu hình lịch và modal bán10 dòng ở5 viewport đều còn thấy footer/nút xác nhận.
- Các trang Xưởng đo riêng: Nhân viên, Chấm công, Tiền công, Quyết toán xưởng, Bán hàng, Nhập kho, Tồn kho. Các trang Phân phối đo: Tổng quan, Sản phẩm, Nhập kho, Bán hàng, Tồn kho, Khách mua, Công nợ, Thu chi, Báo cáo, Sổ cuối năm. Đây là coverage theo ma trận đã thực hiện, không tuyên bố mọi popup/mọi dữ liệu/mọi role đều đạt. Modal nhập nhiều dòng, bản in lương/VietQR và các phần chưa đo vẫn cần nghiệm thu.
- Preview sổ40 hóa đơn hiển thị tiếng Việt, từng lần trả ngày đúng, trạng thái đủ/một phần/nợ và tổng4.000.000đ, đã trả950.000đ, còn3.050.000đ khớp fixture. Bấm In/LưuPDF làm IAB timeout Input.dispatchMouseEvent; không thấy hộp thoại in. Đã đóng tab bị treo và mở tab thử khác để tiếp tục. Không tạo được file PDF thực tế nên PDF vẫn NOT_TESTED; không sửa code in khi chưa chứng minh lỗi ứng dụng thay vì giới hạn công cụ.
- UI có các cảnh báo deprecated của Ant Design (Drawer width, Space direction, Statistic valueStyle...). Không đổi hàng loạt API giao diện vì chưa có lỗi bố cục tái hiện. Không mở lại restore production.

## File thay đổi và rủi ro

Code: YearEndBalanceService.java (khóa đọc shop và đọc hiện hành bản chốt), DashboardService.java (khóa trước tính chốt), FactoryService.java (khóa trước chốt kiểm kê), frontend/src/features/system/system-api.ts (identity/DB readiness). Không sửa tiền hoặc công thức.

Test: StockLifecycleMysqlIT.java, BackupBusinessMysqlIT.java, PayrollMigrationMysqlIT.java mới, BackupCapacityMysqlIT.java mới, frontend/tests/system-readiness.test.mjs mới. Công cụ đối chiếu: scripts/v43-preflight.sql mới, chỉ SELECT.

Tài liệu: README.md; docs/requirements.md; docs/test-plan.md; docs/audit-next-main.md; FEATURE.md của backend/dashboard, backend/factory, backend/backup, backend/attendance và frontend/system.

Rủi ro cần biết: khóa shop có thể làm request ghi chờ snapshot/chốt đang chạy. Kiểm thử đã bảo vệ các interleaving nêu trên, chưa chứng nhận mọi thứ tự khóa chéo hai sổ hoặc mọi trường hợp tải lớn. Chưa thực hiện đối chiếu dữ liệu thật trước V43; script chỉ được cung cấp, không tự chạy vào aluminum_app. Các lựa chọn tác giả/FK/tài khoản vẫn DESIGN_DECISION; recovery toàn bộ NOT_VERIFIED; PDF thực tế và installer NOT_TESTED. Không sửa V1–V43, không reset/restore/seed DB thật, không dùng token R2 thật, không commit/merge/push.
