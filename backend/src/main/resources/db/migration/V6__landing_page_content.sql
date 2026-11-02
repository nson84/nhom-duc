-- Noi dung chu tren landing page (trang gioi thieu cong khai).
-- Luu tung key trong app_settings de Admin sua tung phan ma khong can doi code.
-- Chi Admin dang nhap moi ghi duoc; endpoint cong khai chi doc.

INSERT INTO app_settings (setting_key, setting_value) VALUES
('landing.hero_title', 'Nhôm cửa, nhôm hộp — báo giá trong 15 phút'),
('landing.hero_subtitle', 'Cửa hàng cung cấp nhôm loại 1, loại 2, phụ kiện và khuôn cửa cho thợ và công trình. Để lại số điện thoại, chúng tôi gọi lại xác nhận khối lượng và chốt giá ngay.'),
('landing.cta_header', 'Nhận báo giá'),
('landing.cta_primary', 'Để lại số điện thoại'),
('landing.categories_title', 'Mặt hàng đang cung cấp'),
('landing.contact_title', 'Nhận báo giá'),
('landing.contact_description', 'Điền thông tin bên cạnh. Cửa hàng sẽ gọi lại trong giờ làm việc để xác nhận khối lượng, kích thước và báo giá.'),
('landing.privacy_note', 'Thông tin của bạn chỉ dùng để liên hệ tư vấn, không chia sẻ cho bên thứ ba.'),
('landing.form_submit_label', 'Gửi thông tin'),
('landing.form_success_title', 'Đã nhận thông tin của bạn'),
('landing.form_success_description', 'Cửa hàng sẽ gọi lại sớm. Nếu cần gấp, hãy gọi trực tiếp số điện thoại bên cạnh.'),
('landing.form_again_label', 'Gửi thêm'),
('landing.strength_1_title', 'Nhôm đúng loại'),
('landing.strength_1_text', 'Đủ loại 1, loại 2, phụ kiện cửa, khuôn cửa theo yêu cầu.'),
('landing.strength_2_title', 'Hàng có nguồn gốc'),
('landing.strength_2_text', 'Nhập trực tiếp, giấy tờ đầy đủ, bảo hành rõ ràng.'),
('landing.strength_3_title', 'Cắt theo kích thước'),
('landing.strength_3_text', 'Cắt, đột lỗ, hoàn thiện theo bản vẽ. Sai kích thước không tính tiền.'),
('landing.strength_4_title', 'Báo giá trong 15 phút'),
('landing.strength_4_text', 'Gọi lại xác nhận khối lượng và báo giá chốt ngay khi nhận thông tin.')
ON DUPLICATE KEY UPDATE setting_key = setting_key;
