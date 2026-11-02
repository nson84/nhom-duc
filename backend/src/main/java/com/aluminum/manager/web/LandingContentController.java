package com.aluminum.manager.web;

import com.aluminum.manager.common.ApiResponse;
import com.aluminum.manager.system.LandingContent;
import com.aluminum.manager.system.LandingContentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/landing-content")
public class LandingContentController {

	private final LandingContentService landingContentService;

	public LandingContentController(LandingContentService landingContentService) {
		this.landingContentService = landingContentService;
	}

	@GetMapping
	public ApiResponse<LandingContent> get() {
		return ApiResponse.ok(landingContentService.get(), null);
	}

	@PutMapping
	public ApiResponse<LandingContent> update(@RequestBody LandingContent request) {
		return ApiResponse.ok(landingContentService.update(request), "Đã lưu nội dung trang giới thiệu");
	}
}
