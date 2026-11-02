package com.aluminum.manager.web;

import com.aluminum.manager.common.ApiResponse;
import com.aluminum.manager.system.LandingContent;
import com.aluminum.manager.system.LandingContentService;
import com.aluminum.manager.system.SettingsService;
import com.aluminum.manager.system.ShopSettings;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicShopController {

	private final SettingsService settingsService;
	private final LandingContentService landingContentService;
	private final JdbcTemplate jdbc;

	public PublicShopController(
			SettingsService settingsService,
			LandingContentService landingContentService,
			JdbcTemplate jdbc) {
		this.settingsService = settingsService;
		this.landingContentService = landingContentService;
		this.jdbc = jdbc;
	}

	/**
	 * Thông tin cửa hàng + chữ các phần trên landing page.
	 *
	 * <p>Chỉ trả dữ liệu cần cho trang giới thiệu (tên/địa chỉ/SĐT, danh mục hoạt động, nội dung
	 * chữ). Không trả cấu hình sao lưu hay số liệu kinh doanh.
	 */
	@GetMapping("/shop-info")
	public ApiResponse<ShopInfoDto> shopInfo() {
		ShopSettings settings = settingsService.get();
		return ApiResponse.ok(
				new ShopInfoDto(
						settings.shopName(),
						settings.shopAddress(),
						settings.shopPhone(),
						categories(),
						landingContentService.get()),
				null);
	}

	private List<String> categories() {
		return jdbc.queryForList("""
				SELECT name FROM product_categories WHERE is_active = 1 ORDER BY name
				""", String.class);
	}

	public record ShopInfoDto(
			String shopName,
			String shopAddress,
			String shopPhone,
			List<String> categories,
			LandingContent content) {
	}
}
