package com.aluminum.manager.auth;

import com.aluminum.manager.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/login")
	public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		return ApiResponse.ok(authService.login(request), "Đăng nhập thành công");
	}

	@PostMapping("/logout")
	public ApiResponse<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
		authService.logout(bearer(authorization));
		return ApiResponse.ok(null, "Đã đăng xuất");
	}

	@GetMapping("/me")
	public ApiResponse<AdminProfile> me() {
		return ApiResponse.ok(authService.me(), null);
	}

	@PutMapping("/password")
	public ApiResponse<LoginResponse> changePassword(
			@Valid @RequestBody ChangePasswordRequest request,
			@RequestHeader(value = "Authorization", required = false) String authorization) {
		return ApiResponse.ok(authService.changePassword(request, bearer(authorization)), "Đã đổi mật khẩu");
	}

	private static String bearer(String authorization) {
		if (authorization == null || !authorization.startsWith("Bearer ")) {
			return null;
		}
		return authorization.substring(7).trim();
	}
}
