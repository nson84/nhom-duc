package com.aluminum.manager.web;

import com.aluminum.manager.common.ApiResponse;
import com.aluminum.manager.web.LandingDtos.LeadCreatedResponse;
import com.aluminum.manager.web.LandingDtos.LeadDto;
import com.aluminum.manager.web.LandingDtos.LeadRequest;
import com.aluminum.manager.web.LandingDtos.LeadSummary;
import com.aluminum.manager.web.LandingDtos.LeadUpdateRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class LeadController {

	private final LeadService service;

	public LeadController(LeadService service) {
		this.service = service;
	}

	/** Công khai: khách trên landing page gửi thông tin. Không yêu cầu đăng nhập. */
	@PostMapping("/public/leads")
	public ApiResponse<LeadCreatedResponse> submit(
			@Valid @RequestBody LeadRequest request, HttpServletRequest http) {
		return ApiResponse.ok(
				service.submit(request, clientIp(http), http.getHeader("User-Agent")),
				"Đã nhận thông tin. Chúng tôi sẽ liên hệ lại sớm.");
	}

	/** Chỉ Admin đăng nhập mới xem được danh sách khách để lại thông tin. */
	@GetMapping("/leads")
	public ApiResponse<List<LeadDto>> list(
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String q) {
		return ApiResponse.ok(service.list(status, q), null);
	}

	@GetMapping("/leads/summary")
	public ApiResponse<LeadSummary> summary() {
		return ApiResponse.ok(service.summary(), null);
	}

	@PutMapping("/leads/{id}")
	public ApiResponse<LeadDto> update(@PathVariable long id, @Valid @RequestBody LeadUpdateRequest request) {
		return ApiResponse.ok(service.update(id, request), "Đã cập nhật");
	}

	/**
	 * Lấy IP thật khi chạy sau Nginx. Chỉ tin header do proxy đặt; nếu chạy trực tiếp thì dùng địa chỉ socket.
	 */
	private static String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded != null && !forwarded.isBlank()) {
			int comma = forwarded.indexOf(',');
			return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
		}
		return request.getRemoteAddr();
	}
}
