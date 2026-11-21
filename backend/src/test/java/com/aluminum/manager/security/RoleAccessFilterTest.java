package com.aluminum.manager.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aluminum.manager.auth.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class RoleAccessFilterTest {
	@AfterEach
	void clear() {
		SecurityContextHolder.clearContext();
	}

	private int status(UserRole role, String method, String path, String query) throws Exception {
		var principal = new AuthenticatedAdmin(1L, role.name(), false,
				role, role == UserRole.ADMIN ? null : role.name());
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null));
		var request = new MockHttpServletRequest(method, path);
		if (query != null) request.setParameter("type", query);
		var response = new MockHttpServletResponse();
		new RoleAccessFilter().doFilter(request, response, new MockFilterChain());
		return response.getStatus();
	}

	private int status(String method, String path, String query) throws Exception {
		return status(UserRole.DISTRIBUTION, method, path, query);
	}

	@Test
	void backupRoutesAreAdminOnlyForEveryMethod() throws Exception {
		for (String method : new String[]{"GET", "POST", "PUT", "PATCH", "DELETE"}) {
			for (String path : new String[]{"/api/v1/backups", "/api/v1/backups/status", "/api/v1/backups/1/restore",
					"/api/v1/backups/cloud/sync", "/api/v1/backups/cloud/prepare",
					"/api/v1/backups/recovery-key", "/api/v1/backups/recovery-key/import"}) {
				assertEquals(200, status(UserRole.ADMIN, method, path, null), method + " " + path);
				assertEquals(403, status(UserRole.DISTRIBUTION, method, path, null), method + " " + path);
				assertEquals(403, status(UserRole.MANUFACTURING, method, path, null), method + " " + path);
			}
		}
		assertEquals(200, status(UserRole.DISTRIBUTION, "GET", "/api/v1/backups-extra", null));
	}

	@Test
	void distributionCannotOpenEmployeeOrPayrollRoutes() throws Exception {
		assertEquals(403, status("GET", "/api/v1/employees", null));
		assertEquals(403, status("GET", "/api/v1/reports/payroll", null));
		assertEquals(403, status("GET", "/api/v1/reports/export", "payroll"));
	}

	@Test
	void distributionCanStillEnterOperatingExpenses() throws Exception {
		assertEquals(200, status("POST", "/api/v1/finance/transactions", null));
	}

	@Test
	void adminUsesBackupSettingsQrAndAuth() throws Exception {
		for (String path : new String[]{"/api/v1/admin/combined/years/2025", "/api/v1/annual-book/2025",
				"/api/v1/reports/profit", "/api/v1/finance/transactions", "/api/v1/bank-accounts-extra"}) {
			assertEquals(403, status(UserRole.ADMIN, "GET", path, null), path);
		}
		for (String method : new String[]{"GET", "POST", "PUT", "DELETE"}) {
			assertEquals(200, status(UserRole.ADMIN, method, "/api/v1/bank-accounts", null));
			assertEquals(200, status(UserRole.ADMIN, method, "/api/v1/bank-accounts/1", null));
		}
		assertEquals(200, status(UserRole.ADMIN, "GET", "/api/v1/settings", null));
		assertEquals(200, status(UserRole.ADMIN, "PUT", "/api/v1/settings", null));
		assertEquals(200, status(UserRole.ADMIN, "POST", "/api/v1/auth/change-password", null));
	}
}
