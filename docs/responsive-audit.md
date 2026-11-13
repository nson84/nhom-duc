# Kiểm tra responsive (13/11/2026)

Phạm vi tĩnh: toàn bộ route trong `frontend/src/app/router.tsx`, AppShell, form, modal, bảng, toolbar, biểu đồ, pagination và bản xem trước Sổ cuối năm. Kiểm tra trình duyệt trên ba vai trò với dữ liệu hiện có; không tạo, sửa hoặc xóa dữ liệu nghiệp vụ MySQL.

| Thành phần / file | Lỗi và nguyên nhân | Kích thước thấy lỗi | Cách sửa |
| --- | --- | --- | --- |
| `components/layout/AppShell.tsx` | Sidebar 232 px chiếm nhiều diện tích ở cửa sổ laptop nhỏ | 1024×768 | Chuyển sang menu Drawer dưới 1200 px; nút menu vẫn mở/đóng được. |
| `index.css`, form phiếu trong `stock/DocumentsPage.tsx` | Modal dài hơn viewport, footer lưu nằm dưới mép dưới; quy tắc cuộn cũ chỉ áp dụng dưới 768 px và selector nội dung Ant Design 6 đã đổi | 900×600, 1024×768 | Giới hạn chiều cao container modal theo viewport, cuộn riêng thân modal, giữ header và footer nhìn thấy. |
| `components/PagedTable.tsx` và các bảng nghiệp vụ trực tiếp | Bảng nhiều cột tự ép chữ và nút vào cột hẹp | 900×600 | Áp dụng `scroll.x = max-content` cho bảng dùng chung và bảng trực tiếp; thanh cuộn nằm trong bảng. |
| `index.css`, `dashboard/DashboardPage.tsx` | Ant Row gutter âm làm vùng Tổng quan rộng hơn vùng nội dung 8 px | 900×600 | Chừa khoảng ngang tương ứng cho Row; không che nội dung bằng overflow hidden. |
| `finance/ReportsPage.tsx` | Thẻ báo cáo có nhiều bảng trước đây bị cố định theo chiều cao màn hình, bảng cuối tràn qua viền | 900×600 | Thẻ báo cáo tự giãn theo nội dung (sửa ở commit trước). |

## Kiểm thử trình duyệt

Đã mở các route Xưởng: Tổng quan, Khách mua, Sản phẩm, Nhập kho, Bán hàng, Tồn kho, Quyết toán xưởng, Nhân viên, Chấm công, Tiền công, Thu chi, Báo cáo, Sổ cuối năm. Đo chiều rộng trang và vùng nội dung ở từng viewport; không phát hiện cuộn ngang toàn trang sau sửa. Ở 900×600, bảng Tiền công cuộn trong chính bảng (nội dung 1037 px trong vùng 826 px). Form Bán hàng ở 900×600 có thân cuộn 692 px trong vùng 457 px, nút lưu ở vị trí 567 px, nằm trong màn cao 600 px. Đã nhìn trực quan Tổng quan, danh sách Bán hàng, modal Bán hàng, modal Loại nhôm và Báo cáo ở giao diện sáng; modal Bán hàng cũng được kiểm tra ở giao diện tối. Ở 1024×768, menu chuyển sang Drawer và vùng nội dung còn 992 px.

| Viewport | Xưởng | Phân phối | Admin | Cuộn ngang ngoài ý muốn |
| --- | ---: | ---: | ---: | --- |
| 1920×1080 | 13/13 | — | — | 0 |
| 1600×900 | 13/13 | — | — | 0 |
| 1366×768 | 13/13 | — | — | 0 |
| 1280×720 | 13/13 | 9/9 | 6/6 | 0 |
| 1024×768 | 13/13 | 9/9 | 6/6 | 0 |
| 900×600 | 13/13 | 9/9 | 6/6 | 0 |

Viewport hiệu dụng 1280 và 1024 đại diện cho màn 1600 ở tỉ lệ 125% và khoảng 150%. Nội dung PDF khổ A4 nằm trong iframe riêng; chưa đối chiếu bản in dài có dữ liệu thật. Các bảng với số liệu dài vẫn cần kiểm tra thêm khi có dữ liệu thực ở ba vai trò.
