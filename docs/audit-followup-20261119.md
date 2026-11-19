# Đối chiếu audit ngày 18/11 với code hiện tại — 19/11/2026

## Phạm vi và kết luận

Mốc audit gốc: `2f228143cf5105184610b32f3f49c21446823f46`. Mã đã merge vào main qua PR #3: `82f19139d8487e57de7a7a2cd83c1b864fff23dd`; đầu nhánh local trước lần rà soát này: `e1f5ca35ccf14b84738b6f300a33bf7bae0c0475`. Cập nhật lịch sử: các sửa được mô tả trong báo cáo này đã nằm trong main tại `b290e3cf2ee54d834c08a35267950d0fb86228bb` (bao gồm commit `53c1174` và `5dd5389`), không còn là thay đổi chưa commit. Lượt audit tiếp theo tại b290e3c ban đầu chỉ sửa workspace; sau kiểm thử người dùng đã yêu cầu đẩy lên main; xem `audit-b290-20261119.md`.

Đã xử lý thêm các lỗi tái hiện được bằng test. **Chưa nghiệm thu toàn bộ checklist phát hành**: khôi phục chuyển database/rollback, máy Windows sạch và kiểm thử UI/PDF thực tế còn chưa được chứng minh. Không mở lại restore production để che khoảng trống này.

Không reset, seed, migrate, restore hoặc xóa database đang dùng. MySQL thử là một tiến trình riêng ở `127.0.0.1:13307`, datadir `.runtime/audit-mysql-20261119`; mọi bài tích hợp tự tạo database `aluminum_test_*` hoặc `aluminum_restore_*`. Không dùng token R2 thật để chạy các bài này.

## Phát hiện và bằng chứng

| Phát hiện | Bằng chứng trong code/test | Trạng thái | Mức độ / xử lý |
|---|---|---|---|
| NULL giá vốn bị coi là 0 làm lãi ảo | `FinanceService.profitReport`, `DashboardService.summary/charts`, `FinanceServiceMissingCostTest` | ALREADY FIXED | P0: tổng lãi thực tế để null khi thiếu snapshot; không khôi phục FIFO/bình quân Phân phối |
| Báo cáo ngày đọc cột SQL không có | `FinanceService.java:377`; test `dailyReportDoesNotInventProfitWhenCostSnapshotIsMissing` đã lỗi `Column 'missing_standard' not found` trước sửa | CONFIRMED, đã sửa | P0: bổ sung cột tổng hợp thiếu giá tham khảo |
| Giá tham khảo 0 vẫn báo đầy đủ ở tổng kỳ/sản phẩm | Cùng test ghi snapshot tham khảo 0, trước sửa `standardCostComplete` vẫn true | CONFIRMED, đã sửa | P0: thống nhất 0/thiếu là chưa đủ dữ liệu cho lãi tham khảo |
| Chuyển nội bộ làm giảm lãi Phân phối dù không tạo doanh thu | `DashboardService.closeDistribution`; test `internalTransferHasNoRevenueOrDebtAndDoesNotReduceAnnualProfit` | CONFIRMED, đã sửa | P0: loại giá chuyển nội bộ khỏi phần chi phí hàng còn do Phân phối chịu; không cộng vào doanh thu |
| Sửa tăng phiếu nhập sau khi đã bán bị từ chối vì tồn trung gian | `StockDocumentService.editConfirmed`; test nhập 10t, bán 2t, sửa nhập thành 12t đã lỗi tồn không đủ trước sửa | CONFIRMED, đã sửa | P0: dựng lại phiếu và kiểm tra tồn cuối trong cùng transaction; sửa xuống mức làm tồn âm rollback toàn bộ |
| Sửa phiếu đã thu tiền / trả lặp | `successfulEditPreservesPaymentsAndOriginalConfirmationDeadline`, `paymentIdempotencyAndFailedEditPreserveStockAndDebt`, `concurrentPaymentRetriesCreateOneReceipt` | ALREADY FIXED, đã kiểm thử MySQL | P0: giữ thanh toán, hạn sửa gốc; hai request cùng khóa chỉ tạo một khoản thu |
| Đảo phiếu / cặp chuyển nội bộ | `unpaidSaleReversesExactlyOnceAndRestoresStock`, `reversingInternalPairRestoresBothInventoriesWithoutCash` | ALREADY FIXED, đã kiểm thử MySQL | P0: đảo một lần, giữ tổng tồn và không tạo dòng tiền nội bộ |
| Xưởng bán không trừ nguyên liệu, Phân phối có trừ | `manufacturingSaleKeepsRawInventoryUnchanged`, các test số tồn Phân phối | NOT A BUG | Giữ nguyên nghiệp vụ |
| Thu nợ ở kỳ sau làm đổi bản chốt cũ | `paymentInFollowingPeriodDoesNotRewriteClosedYearDebt` | ALREADY FIXED, đã kiểm thử MySQL | P0: cho thu ở tháng mới đang mở, nợ tại ngày chốt giữ nguyên, không ghi đè bản chốt |
| Snapshot sổ tháng thay đổi theo giao dịch mới | `optionalMonthlySnapshotDoesNotChangeWhenAnotherInvoiceIsAdded` | ALREADY FIXED, đã kiểm thử MySQL | P0: đọc lại JSON đã lưu; chốt sổ tùy chọn khác với khóa tháng vận hành |
| Bật lại FOREIGN_KEY_CHECKS không phát hiện dòng mồ côi | `StagedRestoreService.java:93`; test archive có thanh toán trỏ hóa đơn 999999, trước sửa prepare vẫn thành công | CONFIRMED, đã sửa | P0: kiểm tra từng ràng buộc, gồm khóa nhiều cột, sau import; trả lỗi nếu thiếu bản ghi cha |
| Quyền POSIX 0600 trên thư mục staging không cho truy cập ảnh | `R2CloudService.setOwnerOnly`, `privateDirectoryRemainsTraversableByItsOwner` | CONFIRMED qua đối chiếu quyền, đã sửa | P1: tệp 0600, thư mục 0700; ACL Windows giữ nguyên. Chưa chạy bản sửa thư mục trên Linux ở lượt này |
| SQL dump/ảnh có thể tăng RAM không giới hạn trước mã hóa | `BoundedSqlDump`, `BackupService.buildDatabaseDump`, `BoundedSqlDumpTest` | CONFIRMED qua mã, đã giới hạn | P1: tối đa 32 Mi ký tự SQL; kiểm tra trường lớn và dung lượng ảnh trước đọc. Vẫn là định dạng trong RAM, chưa phải streaming hoàn toàn |
| Cổng 8080 thuộc dịch vụ khác | `frontend/src-tauri/src/backend.rs`, `ReadyStatus` | ALREADY FIXED trong code | P1: kiểm tra identity và database UP; chưa build Rust/NSIS thật |
| CI không chạy trên nhánh mới | `.github/workflows/verify.yml`, các run thành công của PR #3 | ALREADY FIXED | P1: main/feat/**/PR main; MySQL 8 riêng, unit/integration/frontend |
| Phục hồi trực tiếp database đang dùng | `BackupController.restore` trả RESTORE_UNAVAILABLE | NOT A BUG | Giữ khóa có chủ đích đến khi diễn tập chuyển/rollback đạt |

## Nguồn và ý nghĩa chỉ tiêu

- **Doanh thu bán hàng**: tổng tiền phiếu bán ngoài đã xác nhận trong khoảng ngày lập phiếu; loại chuyển nội bộ.
- **Đã thu từ phiếu trong kỳ**: thanh toán đã ghi của các phiếu thuộc kỳ, có thể bao gồm lần khách trả sau ngày lập phiếu. Không đồng nghĩa tiền mặt nhận trong kỳ.
- **Sổ thu chi**: lọc ngày thực thu/chi của `financial_transactions.transaction_date`; thu nợ cũ xuất hiện ở kỳ nhận tiền.
- **Nợ tại ngày chốt**: phiếu ngoài trước mốc chốt trừ khoản thanh toán đến mốc chốt, lưu trong `year_end_balances`. Nợ hiện tại vẫn đối chiếu từ chứng từ nguồn.
- **Lãi tham khảo hóa đơn**: dùng nguồn giá tham khảo, thiếu/0 thì chưa đủ dữ liệu. Không lấy giá vốn NULL thành 0. Giá vốn thực tế không được tự suy diễn từ giá tham khảo.
- **Lãi quản trị Phân phối theo kỳ**: doanh thu ngoài − tồn đầu − nhập trong kỳ + giá trị chuyển nội bộ ra Xưởng + tồn cuối − chi phí hoạt động. Tồn cuối phân bổ lớp nhập mới nhất; giữ phương pháp hiện tại, không FIFO.
- **Tiền công/phụ cấp/khấu trừ**: theo tháng, như người dùng đã xác nhận; không tự chia theo ngày chốt năm. Sổ tháng và khóa tháng là hai thao tác riêng.

## Kết quả đã chạy

| Lệnh / kiểm tra | Kết quả |
|---|---|
| `backend/mvnw.cmd test -q` | 74 unit test, 0 failure, 0 error |
| `backend/mvnw.cmd '-Dtest=*MysqlIT' test -q` với MySQL thử cổng 13307 | 14 test, 0 failure, 0 error; 12 lifecycle/kỳ/snapshot + 2 staging |
| Flyway trong mỗi database thử | 41 migration được validate và migrate từ schema rỗng, đến V41 |
| `npm ci --prefer-offline --no-audit`, `npm run build` | Đạt. Ban đầu Windows khóa esbuild/Rollup do Vite đang chạy; đã dừng đúng Vite của workspace để cài lại |
| UI tự động | Không chạy được: công cụ khởi tạo báo `failed to write kernel assets ... os error 3` |
| Rust/NSIS | Chưa chạy: không có Cargo trong PATH; chưa có máy Windows sạch để diễn tập |

Cảnh báo build frontend vẫn có bundle JS lớn và chú thích trong thư viện Zod; không phải lỗi TypeScript/build.

Các test regression đã được chạy trước sửa để tái hiện lỗi cột SQL, snapshot tham khảo 0, lãi chuyển nội bộ, sửa phiếu nhập và bỏ lọt khóa ngoại. Bộ test giới hạn dump được thêm trước khi triển khai bộ đệm.

## File chỉnh và migration

- `finance/FinanceService.java`: cột SQL theo ngày, trạng thái thiếu giá tham khảo.
- `dashboard/DashboardService.java`: công thức chi phí chuyển nội bộ khi chốt Phân phối.
- `inventory/StockDocumentService.java`: sửa phiếu nhập đã giao một phần, giữ rollback và kiểm tra tồn cuối.
- `backup/StagedRestoreService.java`: kiểm tra khóa ngoại trong database staging.
- `backup/R2CloudService.java`: phân biệt quyền riêng tư tệp/thư mục POSIX.
- `backup/BackupService.java`, `backup/BoundedSqlDump.java`: chặn SQL/ảnh quá giới hạn bộ nhớ.
- Tests: `StockLifecycleMysqlIT`, `StagedRestoreMysqlIT`, `R2CloudServiceTest`, `BoundedSqlDumpTest`.
- Tài liệu FEATURE, README và hướng dẫn test/restore được cập nhật theo các quy tắc trên.

**Không thêm/sửa migration trong lượt này. Không nâng schema database thật.** V41 là migration đã có trước lượt audit này.

## Chưa được nghiệm thu / rủi ro còn lại

1. Chưa có diễn tập archive đầy đủ của cả hai sổ và ảnh trên PC khác, toàn bộ số tiền/quan hệ, chuyển database hoạt động và rollback. Test staging hiện kiểm tra fixture SQL/ảnh/khóa ngoại; không phải bằng chứng cho toàn bộ disaster recovery. Các FK tới tài khoản cục bộ không được mang sang bản backup cần được kiểm chứng trong diễn tập có payroll đã chốt; không tự bỏ FK để cho qua.
2. Chưa chạy lỗi mất điện, mất mạng R2 thật, bucket lock/retention dài hạn; không xóa bản local/cloud nào để thử. Chưa bật restore production.
3. Chưa kiểm thử trực quan toàn bộ kích thước, dark/light, A4 tiếng Việt nhiều trang hoặc E2E nhập → bán → thanh toán trong browser. Không coi build là đạt mục này.
4. Chưa xác nhận NSIS trên Windows sạch, thiếu Java/MySQL và thông báo lỗi khởi động trực quan. Mã identity có nhưng chưa build Rust.
5. Bộ lifecycle hiện chưa bao phủ mọi tổ hợp đổi sản phẩm/loại, giảm giá cấp phiếu, mọi race giữa chốt kỳ với ghi phiếu và kỳ năm cắt giữa cùng một tháng dương lịch. Cần bổ sung trước khi khẳng định mọi đường đều an toàn. Cụ thể, giá trị tồn đang đọc line_total trong khi tổng nhập có giảm giá cấp phiếu; phải thử đối chiếu phân bổ giảm giá ở cả hai sổ, chưa tự thay phương pháp giá vốn.
6. Sổ tháng tùy chọn giữ bản chụp, không tự khóa các giao dịch mới. Muốn ngăn ghi thêm phải dùng Khóa tháng. Chưa có chức năng sửa/mở lại bản chụp tháng; không tự ghi đè khi có chứng từ bổ sung.
7. SQL dump được giới hạn chứ chưa streaming hoàn toàn; dữ liệu vượt giới hạn sẽ báo lỗi backup, không được coi bản cuối đã đủ. Chưa thử tải sát giới hạn 32 Mi ký tự với workload thực.
8. Không tách lại service lớn trong lượt này vì coverage chưa đủ cho refactor rộng. Không đổi thời hạn JWT hoặc khôi phục cách tính giá vốn cũ.

## Quy trình phục hồi cần diễn tập trước khi mở khóa

Trên máy/database thử: xác minh archive/key → dựng staging mới → kiểm tra schema/FK, các tổng phiếu/thu/nợ/tồn của từng sổ và nội dung ảnh → lưu backup database/ảnh hiện tại → dừng nhận ghi và đóng kết nối → chuyển cấu hình database và thư mục ảnh cùng phiên bản ứng dụng → kiểm tra read-only và thao tác thử → nếu lỗi, quay lại cấu hình database/ảnh cũ. Không xóa database hay ảnh cũ trong quy trình. Maintenance mode, chuyển cấu hình và rollback tự động **chưa triển khai/diễn tập**, nên endpoint restore trực tiếp tiếp tục khóa.
