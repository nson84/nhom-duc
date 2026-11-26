package com.aluminum.manager.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FirstRunPasswordRequest(
		@NotBlank(message = "Chọn tài khoản")
		String username,
		@NotBlank(message = "Nhập mật khẩu mới")
		@Size(min = 6, max = 128, message = "Mật khẩu mới phải từ 6 ký tự")
		String newPassword,
		boolean applyToAllPending) {
}
