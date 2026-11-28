# Khung giao diện dùng chung

AppShell cung cấp menu đúng role, đổi giao diện, chuông nhắc giá, avatar và điều hướng. Admin có Sao lưu/Cài đặt và tài khoản QR trong menu avatar; Xưởng có nhân sự; Phân phối không có nhân sự. Dưới1200px dùng Drawer; nội dung hỗ trợ cuộn bảng/modal theo từng màn.

Trên màn hình có thanh menu bên trái, khối tên cửa hàng giữ chiều cao 64 px bằng thanh đầu trang. Danh sách menu cuộn riêng khi thiếu chỗ.

Menu tài khoản hiển thị bản frontend VITE_BUILD_REVISION, backend version/revision và schemaVersion từ ReadyGate. Thiếu SHA thì development/unversioned, không tự nhận bản mới nhất. Metadata không chứa mật khẩu, JWT hoặc R2 credentials.


Xưởng và Phân phối có nhóm Kiểm kê & chốt sổ với hai mục Chốt sổ tháng và Chốt kỳ. Menu in sổ hiển thị tên Sổ cuối kỳ.
