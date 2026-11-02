package com.aluminum.manager.system;

import com.aluminum.manager.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

	private final SettingsService settingsService;

	public SettingsController(SettingsService settingsService) {
		this.settingsService = settingsService;
	}

	@GetMapping
	public ApiResponse<ShopSettings> get() {
		return ApiResponse.ok(settingsService.get(), null);
	}

	@PutMapping
	public ApiResponse<ShopSettings> update(@Valid @RequestBody ShopSettings request) {
		return ApiResponse.ok(settingsService.update(request), "Đã lưu cấu hình");
	}
}
