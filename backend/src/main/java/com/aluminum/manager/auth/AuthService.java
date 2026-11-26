package com.aluminum.manager.auth;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.security.AuthenticatedAdmin;
import com.aluminum.manager.security.CurrentAdmin;
import com.aluminum.manager.security.JwtService;
import com.aluminum.manager.system.AuditService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private static final String DUMMY_PASSWORD_HASH =
			"$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";
	private static final List<String> OPERATIONAL_ACCOUNTS = List.of("admin", "phanphoi", "xuong");

	private final AdminUserRepository adminUsers;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final AuditService auditService;

	public AuthService(
			AdminUserRepository adminUsers,
			PasswordEncoder passwordEncoder,
			JwtService jwtService,
			AuditService auditService) {
		this.adminUsers = adminUsers;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.auditService = auditService;
	}

	@Transactional
	public LoginResponse login(LoginRequest request) {
		AdminUser user = adminUsers.findByUsername(request.username().trim()).orElse(null);
		String hash = user == null ? DUMMY_PASSWORD_HASH : user.getPasswordHash();
		boolean matches = passwordEncoder.matches(request.password(), hash);
		if (user == null || !user.isActive() || !matches) {
			throw new BusinessException(
					ErrorCode.INVALID_CREDENTIALS,
					"Tên đăng nhập hoặc mật khẩu không đúng",
					null);
		}
		user.setLastLoginAt(LocalDateTime.now());
		adminUsers.save(user);
		auditService.record(user.getId(), "LOGIN", "admin_users", String.valueOf(user.getId()), "Đăng nhập");
		return tokenFor(user);
	}

	@Transactional(readOnly = true)
	public FirstRunStatusResponse firstRunStatus() {
		List<String> pending = new ArrayList<>();
		for (String name : OPERATIONAL_ACCOUNTS) {
			adminUsers.findByUsername(name)
					.filter(AuthService::isPendingFirstRun)
					.ifPresent(user -> pending.add(user.getUsername()));
		}
		return new FirstRunStatusResponse(pending);
	}

	@Transactional
	public LoginResponse setupFirstRunPassword(FirstRunPasswordRequest request) {
		String username = request.username().trim();
		AdminUser target = adminUsers.findByUsername(username).orElse(null);
		if (target == null || !isPendingFirstRun(target)) {
			throw new BusinessException(
					ErrorCode.INVALID_CREDENTIALS,
					"Tài khoản đã được thiết lập mật khẩu, vui lòng đăng nhập bằng mật khẩu hiện có",
					null);
		}
		String encoded = passwordEncoder.encode(request.newPassword());
		LocalDateTime now = LocalDateTime.now();
		if (request.applyToAllPending()) {
			for (String name : OPERATIONAL_ACCOUNTS) {
				if (name.equals(username)) continue;
				adminUsers.findByUsername(name)
						.filter(AuthService::isPendingFirstRun)
						.ifPresent(other -> {
							other.setPasswordHash(encoded);
							other.setMustChangePassword(false);
							other.setLastLoginAt(now);
							adminUsers.save(other);
							auditService.record(target.getId(), "SETUP_PASSWORD", "admin_users",
									String.valueOf(other.getId()), "Thiết lập mật khẩu lần đầu cho " + other.getUsername());
						});
			}
		}
		target.setPasswordHash(encoded);
		target.setMustChangePassword(false);
		target.setLastLoginAt(now);
		adminUsers.save(target);
		auditService.record(target.getId(), "SETUP_PASSWORD", "admin_users",
				String.valueOf(target.getId()), "Thiết lập mật khẩu lần đầu");
		return tokenFor(target);
	}

	private static boolean isPendingFirstRun(AdminUser user) {
		return user.isActive() && user.isMustChangePassword() && user.getLastLoginAt() == null;
	}

	@Transactional(readOnly = true)
	public AdminProfile me() {
		AuthenticatedAdmin current = CurrentAdmin.get();
		AdminUser user = adminUsers.findById(current.id())
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Bạn cần đăng nhập", null));
		return new AdminProfile(
				user.getId(),
				user.getUsername(),
				user.isMustChangePassword(),
				user.getRole().name(),
				user.getShopCode());
	}

	@Transactional
	public LoginResponse changePassword(ChangePasswordRequest request, String rawToken) {
		AuthenticatedAdmin current = CurrentAdmin.get();
		AdminUser user = adminUsers.findById(current.id())
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Bạn cần đăng nhập", null));
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Mật khẩu hiện tại không đúng", null);
		}
		if (!passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
		}
		user.setMustChangePassword(false);
		adminUsers.save(user);
		auditService.record(user.getId(), "CHANGE_PASSWORD", "admin_users", String.valueOf(user.getId()), "Đổi mật khẩu");
		if (rawToken != null && !rawToken.isBlank()) {
			jwtService.revoke(rawToken);
		}
		return tokenFor(user);
	}

	@Transactional
	public void logout(String rawToken) {
		AuthenticatedAdmin current = CurrentAdmin.get();
		if (rawToken != null && !rawToken.isBlank()) {
			try {
				jwtService.revoke(rawToken);
			} catch (BusinessException ignored) {
				// Token het han van cho dang xuat o phia giao dien.
			}
		}
		auditService.record(current.id(), "LOGOUT", "admin_users", String.valueOf(current.id()), "Đăng xuất");
	}

	private LoginResponse tokenFor(AdminUser user) {
		UserRole role = user.getRole();
		String token = jwtService.issue(user.getId(), user.getUsername(), role.name(), user.getShopCode());
		return new LoginResponse(
				token,
				"Bearer",
				jwtService.ttlSeconds(),
				user.isMustChangePassword(),
				user.getUsername(),
				role.name(),
				user.getShopCode());
	}
}
