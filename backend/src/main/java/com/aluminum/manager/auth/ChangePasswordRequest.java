package com.aluminum.manager.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
		@NotBlank(message = "Nhập mật khẩu hiện tại")
		String currentPassword,
		@NotBlank(message = "Nhập mật khẩu mới")
		@Size(min = 6, max = 200, message = "Mật khẩu mới phải từ 6 ký tự")
		String newPassword
) {
}
