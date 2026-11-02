package com.aluminum.manager.config;

import com.aluminum.manager.auth.AdminUser;
import com.aluminum.manager.auth.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final AdminUserRepository adminUsers;
	private final PasswordEncoder passwordEncoder;
	private final String username;
	private final String initialPassword;

	public AdminBootstrap(
			AdminUserRepository adminUsers,
			PasswordEncoder passwordEncoder,
			@Value("${aluminum.admin.username}") String username,
			@Value("${aluminum.admin.initial-password}") String initialPassword) {
		this.adminUsers = adminUsers;
		this.passwordEncoder = passwordEncoder;
		this.username = username;
		this.initialPassword = initialPassword;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (adminUsers.count() > 0) {
			return;
		}
		if (username == null || !username.matches("[A-Za-z0-9._-]{3,80}")) {
			throw new IllegalStateException("Tên đăng nhập Admin ban đầu không hợp lệ");
		}
		if (initialPassword == null || initialPassword.length() < 8) {
			throw new IllegalStateException("Mật khẩu Admin ban đầu phải từ 8 ký tự");
		}
		adminUsers.save(new AdminUser(username, passwordEncoder.encode(initialPassword)));
		log.warn("Đã tạo tài khoản Admin '{}'. Đăng nhập lần đầu sẽ bắt buộc đổi mật khẩu.", username);
	}
}
