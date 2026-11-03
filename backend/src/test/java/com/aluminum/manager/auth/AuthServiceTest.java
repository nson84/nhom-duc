package com.aluminum.manager.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.security.AuthenticatedAdmin;
import com.aluminum.manager.security.JwtService;
import com.aluminum.manager.system.AuditService;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

	@Mock
	private AdminUserRepository adminUsers;
	@Mock
	private PasswordEncoder passwordEncoder;
	@Mock
	private JwtService jwtService;
	@Mock
	private AuditService auditService;

	@AfterEach
	void clearSecurity() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void wrongCurrentPasswordDoesNotChangeTheHash() {
		AdminUser user = new AdminUser("admin", "stored-hash", UserRole.ADMIN, null);
		when(adminUsers.findById(1L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("sai", "stored-hash")).thenReturn(false);
		signIn();
		AuthService service = service();

		BusinessException ex = assertThrows(BusinessException.class,
				() -> service.changePassword(new ChangePasswordRequest("sai", "MatKhauMoi1"), "token"));

		assertEquals(ErrorCode.INVALID_CREDENTIALS, ex.getCode());
		verify(adminUsers, never()).save(any());
	}

	@Test
	void changingPasswordClearsTheFirstLoginFlag() {
		AdminUser user = savedUser();
		when(adminUsers.findById(1L)).thenReturn(Optional.of(user));
		when(passwordEncoder.matches("Admin@123", "stored-hash")).thenReturn(true);
		when(passwordEncoder.matches("MatKhauMoi1", "stored-hash")).thenReturn(false);
		when(passwordEncoder.encode("MatKhauMoi1")).thenReturn("new-hash");
		when(jwtService.issue(1L, "admin", "ADMIN", null)).thenReturn("new-token");
		when(jwtService.ttlSeconds()).thenReturn(3600L);
		signIn();

		LoginResponse response = service().changePassword(
				new ChangePasswordRequest("Admin@123", "MatKhauMoi1"), "old-token");

		assertFalse(user.isMustChangePassword());
		assertEquals("new-hash", user.getPasswordHash());
		assertEquals("new-token", response.token());
		verify(jwtService).revoke("old-token");
	}

	private AuthService service() {
		return new AuthService(adminUsers, passwordEncoder, jwtService, auditService);
	}

	private static AdminUser savedUser() {
		AdminUser user = new AdminUser("admin", "stored-hash", UserRole.ADMIN, null);
		try {
			Field id = AdminUser.class.getDeclaredField("id");
			id.setAccessible(true);
			id.set(user, 1L);
		} catch (ReflectiveOperationException ex) {
			throw new IllegalStateException(ex);
		}
		return user;
	}

	private static void signIn() {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
				new AuthenticatedAdmin(1L, "admin", true, UserRole.ADMIN, null),
				null,
				List.of()));
	}
}
