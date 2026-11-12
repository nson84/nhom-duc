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

	private int status(String method, String path, String query) throws Exception {
		var principal = new AuthenticatedAdmin(1L, "phanphoi", false,
				UserRole.DISTRIBUTION, "DISTRIBUTION");
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null));
		var request = new MockHttpServletRequest(method, path);
		if (query != null) request.setParameter("type", query);
		var response = new MockHttpServletResponse();
		new RoleAccessFilter().doFilter(request, response, new MockFilterChain());
		return response.getStatus();
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
}
