package com.aluminum.manager.security;

import com.aluminum.manager.auth.UserRole;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Admin chỉ xem báo cáo/phiếu và được ghi sao lưu + cài đặt.
 * Phân phối không dùng API nhân sự/chấm công/lương.
 */
public class RoleAccessFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedAdmin admin)) {
			filterChain.doFilter(request, response);
			return;
		}
		String path = request.getRequestURI();
		String method = request.getMethod();
		UserRole role = admin.role();

		if (role == UserRole.DISTRIBUTION && (isPayrollPath(path) || path.equals("/api/v1/reports/payroll"))) {
			forbidden(response, "Tài khoản phân phối không dùng được mục nhân sự / chấm công");
			return;
		}
		if (role == UserRole.ADMIN && isPayrollPath(path)) {
			forbidden(response, "Tài khoản admin xem lương qua Báo cáo, không vào mục nhân sự");
			return;
		}
		if (!role.canWriteOperations() && isWrite(method) && !isAdminAllowedWrite(path, method)) {
			forbidden(response, "Tài khoản admin chỉ xem báo cáo, không sửa nghiệp vụ");
			return;
		}
		filterChain.doFilter(request, response);
	}

	private static boolean isWrite(String method) {
		return HttpMethod.POST.matches(method)
				|| HttpMethod.PUT.matches(method)
				|| HttpMethod.PATCH.matches(method)
				|| HttpMethod.DELETE.matches(method);
	}

	private static boolean isPayrollPath(String path) {
		return path.startsWith("/api/v1/employees")
				|| path.startsWith("/api/v1/attendance")
				|| path.startsWith("/api/v1/payroll")
				|| path.startsWith("/api/v1/holidays")
				|| path.startsWith("/api/v1/attendance-policies");
	}

	private static boolean isAdminAllowedWrite(String path, String method) {
		if (path.startsWith("/api/v1/auth/")) {
			return true;
		}
		if (path.startsWith("/api/v1/settings") || path.startsWith("/api/v1/landing-content")) {
			return true;
		}
		if (path.startsWith("/api/v1/backups")) {
			return true;
		}
		return false;
	}

	private static void forbidden(HttpServletResponse response, String message) throws IOException {
		response.setStatus(403);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType("application/json");
		response.getWriter().write(
				"{\"success\":false,\"code\":\"FORBIDDEN\",\"message\":\"" + escape(message) + "\"}");
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
