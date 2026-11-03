package com.aluminum.manager.security;

import com.aluminum.manager.auth.AdminUser;
import com.aluminum.manager.auth.AdminUserRepository;
import com.aluminum.manager.auth.UserRole;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

	private final JwtService jwtService;
	private final AdminUserRepository adminUsers;

	public JwtAuthenticationFilter(JwtService jwtService, AdminUserRepository adminUsers) {
		this.jwtService = jwtService;
		this.adminUsers = adminUsers;
	}

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		if (HttpMethod.OPTIONS.matches(request.getMethod())) {
			filterChain.doFilter(request, response);
			return;
		}
		String header = request.getHeader("Authorization");
		if (header == null || !header.startsWith("Bearer ")) {
			filterChain.doFilter(request, response);
			return;
		}
		String token = header.substring(7).trim();
		try {
			JwtService.JwtClaims claims = jwtService.parse(token);
			AdminUser user = adminUsers.findById(claims.userId())
					.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ", null));
			if (!user.isActive()) {
				throw new BusinessException(ErrorCode.UNAUTHORIZED, "Tài khoản đã ngừng sử dụng", null);
			}
			UserRole role = user.getRole();
			AuthenticatedAdmin principal = new AuthenticatedAdmin(
					user.getId(),
					user.getUsername(),
					user.isMustChangePassword(),
					role,
					user.getShopCode());
			UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
					principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name())));
			SecurityContextHolder.getContext().setAuthentication(authentication);
			if (user.isMustChangePassword() && !allowedWhilePasswordChange(request)) {
				write(response, 403, "PASSWORD_CHANGE_REQUIRED", "Bạn cần đổi mật khẩu trước khi tiếp tục");
				return;
			}
			filterChain.doFilter(request, response);
		} catch (BusinessException ex) {
			SecurityContextHolder.clearContext();
			if (isPublic(request)) {
				filterChain.doFilter(request, response);
				return;
			}
			write(response, ex.getCode().status().value(), ex.getCode().name(), ex.getMessage());
		}
	}

	private static boolean isPublic(HttpServletRequest request) {
		String path = request.getRequestURI();
		return (HttpMethod.POST.matches(request.getMethod()) && "/api/v1/auth/login".equals(path))
				|| (HttpMethod.GET.matches(request.getMethod()) && "/api/v1/system/ready".equals(path));
	}

	private static boolean allowedWhilePasswordChange(HttpServletRequest request) {
		String path = request.getRequestURI();
		if (HttpMethod.GET.matches(request.getMethod()) && "/api/v1/auth/me".equals(path)) {
			return true;
		}
		if (HttpMethod.PUT.matches(request.getMethod()) && "/api/v1/auth/password".equals(path)) {
			return true;
		}
		return HttpMethod.POST.matches(request.getMethod()) && "/api/v1/auth/logout".equals(path);
	}

	private static void write(HttpServletResponse response, int status, String code, String message) throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType("application/json");
		String body = "{\"success\":false,\"code\":\"" + code + "\",\"message\":\"" + escape(message) + "\"}";
		response.getWriter().write(body);
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}
}
