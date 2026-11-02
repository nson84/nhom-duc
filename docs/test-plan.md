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
| ATT-01 | Tháng 2 năm nhuận có 29 ngày |
| ATT-02 | Tháng 2 năm thường có 28 ngày |
| ATT-03 | Đủ công lấy hệ số trong cấu hình |
| ATT-04 | Nửa công lấy hệ số nửa ngày trong cấu hình |
| ATT-05 | 08:00–17:00 trừ 60 phút nghỉ bằng 8 giờ |
| ATT-06 | Ca 22:00–06:00 được tính sang ngày hôm sau khi cấu hình cho phép |
| ATT-07 | Tăng ca chưa duyệt không cộng vào tiền |

## Cần MySQL trước khi phát hành

INV-01, INV-02, INV-04, INV-08 và toàn bộ luồng xác nhận phiếu trong transaction. ATT-08 đến ATT-12. FIN-01 đến FIN-08. APP-01 đến APP-10, trong đó cài đặt Windows cần Rust và MySQL trên máy sạch.
