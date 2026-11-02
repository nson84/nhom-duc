# Phạm vi MVP

Một máy Windows, một tài khoản Admin. Phần mềm quản lý sản phẩm nhôm, nhập kho, bán hàng, tồn kho, nhân viên, chấm công, tiền công, thu chi, báo cáo và sao lưu.

## Đã khóa

- Đơn vị tồn cơ sở là duy nhất. Mỗi dòng phiếu lưu số lượng bán, đơn vị, hệ số và số lượng cơ sở tại thời điểm giao dịch.
- Hệ số bằng 0 bị từ chối. Không suy đoán hệ số khi chưa có số đã kiểm.
- Tồn kho không âm.
- Phiếu nháp không đổi tồn. Phiếu đã xác nhận không sửa dòng. Sửa sai bằng phiếu đảo, mỗi phiếu chỉ đảo một lần.
- Giá vốn là bình quân gia quyền liên tục. Phiếu bán lưu giá vốn lúc xác nhận.
- Giá nhập thay đổi theo ngày: giữ lịch sử theo khoảng hiệu lực, mỗi dòng phiếu lưu snapshot giá nhập chuẩn của đúng ngày phiếu. Sửa giá nhập về sau không làm lệch chi phí chuẩn đã chốt, nên có hai lớp lãi: lãi thực tế (bình quân gia quyền) và lãi theo giá chuẩn, kèm chênh lệch giá nhập.
- Doanh thu, tiền thực thu và chi phí hoạt động là ba số khác nhau. Tiền nhập hàng chưa bán không phải chi phí kỳ.
- Tăng ca chỉ tính khi đã duyệt. Kỳ công đã chốt không đổi theo hồ sơ mới.
- Giờ qua đêm và làm tròn là cấu hình, không hard-code một công thức cho mọi cửa hàng.

## Chưa làm trong MVP

Nhiều tài khoản, đồng bộ Internet, công nợ chuyên sâu, máy chấm công, ngân hàng, hóa đơn điện tử, nhiều chi nhánh.
