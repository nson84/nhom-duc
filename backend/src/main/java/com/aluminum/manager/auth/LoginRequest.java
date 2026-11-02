package com.aluminum.manager.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
		@NotBlank(message = "Nhập tên đăng nhập")
		@Size(max = 80, message = "Tên đăng nhập quá dài")
		String username,
		@NotBlank(message = "Nhập mật khẩu")
		@Size(max = 200, message = "Mật khẩu quá dài")
		String password
) {
}
