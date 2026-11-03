package com.aluminum.manager.config;

import com.aluminum.manager.auth.AdminUser;
import com.aluminum.manager.auth.AdminUserRepository;
import com.aluminum.manager.auth.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
	private static final String DEFAULT_PASSWORD = "123456";

	private final AdminUserRepository adminUsers;
	private final PasswordEncoder passwordEncoder;

	public AdminBootstrap(AdminUserRepository adminUsers, PasswordEncoder passwordEncoder) {
		this.adminUsers = adminUsers;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public void run(ApplicationArguments args) {
		ensure("admin", UserRole.ADMIN, null);
		ensure("phanphoi", UserRole.DISTRIBUTION, "DISTRIBUTION");
		ensure("xuong", UserRole.MANUFACTURING, "MANUFACTURING");
		log.info("Đã đồng bộ 3 tài khoản: admin / phanphoi / xuong");
	}

	private void ensure(String username, UserRole role, String shopCode) {
		AdminUser user = adminUsers.findByUsername(username).orElse(null);
		String hash = passwordEncoder.encode(DEFAULT_PASSWORD);
		if (user == null) {
			user = new AdminUser(username, hash, role, shopCode);
			user.setMustChangePassword(false);
			adminUsers.save(user);
			return;
		}
		user.setRole(role);
		user.setShopCode(shopCode);
		user.setPasswordHash(hash);
		user.setMustChangePassword(false);
		user.setActive(true);
		adminUsers.save(user);
	}
}
