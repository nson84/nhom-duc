package com.aluminum.manager.auth;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.security.AuthenticatedAdmin;
import com.aluminum.manager.security.CurrentAdmin;
import com.aluminum.manager.security.JwtService;
import com.aluminum.manager.system.AuditService;
import java.time.LocalDateTime;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

	private static final String DUMMY_PASSWORD_HASH =
			"$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

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
	public AdminProfile me() {
		AuthenticatedAdmin current = CurrentAdmin.get();
		AdminUser user = adminUsers.findById(current.id())
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Bạn cần đăng nhập", null));
		return new AdminProfile(user.getId(), user.getUsername(), user.isMustChangePassword());
	}

	@Transactional
	public LoginResponse changePassword(ChangePasswordRequest request, String rawToken) {
		AuthenticatedAdmin current = CurrentAdmin.get();
		AdminUser user = adminUsers.findById(current.id())
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Bạn cần đăng nhập", null));
		if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS, "Mật khẩu hiện tại không đúng", null);
		}
		if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
			throw new BusinessException(
					ErrorCode.VALIDATION_ERROR,
					"Mật khẩu mới phải khác mật khẩu hiện tại",
					null);
		}
		user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
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
		String token = jwtService.issue(user.getId(), user.getUsername());
		return new LoginResponse(
				token,
				"Bearer",
				jwtService.ttlSeconds(),
				user.isMustChangePassword(),
				user.getUsername());
	}
}
