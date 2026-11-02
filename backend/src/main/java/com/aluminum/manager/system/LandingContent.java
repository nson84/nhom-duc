package com.aluminum.manager.system;

import java.util.List;

/**
 * Chữ hiển thị trên trang giới thiệu công khai.
 *
 * <p>Admin sửa từng phần qua API nội bộ; endpoint công khai chỉ đọc bản đã lưu.
 */
public record LandingContent(
		String heroTitle,
		String heroSubtitle,
		String ctaHeader,
		String ctaPrimary,
		String categoriesTitle,
		String contactTitle,
		String contactDescription,
		String privacyNote,
		String formSubmitLabel,
		String formSuccessTitle,
		String formSuccessDescription,
		String formAgainLabel,
		List<Strength> strengths) {

	public record Strength(String title, String text) {
	}

	public static LandingContent defaults() {
		return new LandingContent(
				"Nhôm cửa, nhôm hộp — báo giá trong 15 phút",
				"Cửa hàng cung cấp nhôm loại 1, loại 2, phụ kiện và khuôn cửa cho thợ và công trình. "
						+ "Để lại số điện thoại, chúng tôi gọi lại xác nhận khối lượng và chốt giá ngay.",
				"Nhận báo giá",
				"Để lại số điện thoại",
				"Mặt hàng đang cung cấp",
				"Nhận báo giá",
				"Điền thông tin bên cạnh. Cửa hàng sẽ gọi lại trong giờ làm việc để xác nhận khối lượng, "
						+ "kích thước và báo giá.",
				"Thông tin của bạn chỉ dùng để liên hệ tư vấn, không chia sẻ cho bên thứ ba.",
				"Gửi thông tin",
				"Đã nhận thông tin của bạn",
				"Cửa hàng sẽ gọi lại sớm. Nếu cần gấp, hãy gọi trực tiếp số điện thoại bên cạnh.",
				"Gửi thêm",
				List.of(
						new Strength("Nhôm đúng loại", "Đủ loại 1, loại 2, phụ kiện cửa, khuôn cửa theo yêu cầu."),
						new Strength("Hàng có nguồn gốc", "Nhập trực tiếp, giấy tờ đầy đủ, bảo hành rõ ràng."),
						new Strength(
								"Cắt theo kích thước",
								"Cắt, đột lỗ, hoàn thiện theo bản vẽ. Sai kích thước không tính tiền."),
						new Strength(
								"Báo giá trong 15 phút",
								"Gọi lại xác nhận khối lượng và báo giá chốt ngay khi nhận thông tin.")));
	}
}
