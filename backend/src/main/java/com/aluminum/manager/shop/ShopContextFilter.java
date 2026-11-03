package com.aluminum.manager.shop;

import com.aluminum.manager.auth.UserRole;
import com.aluminum.manager.security.AuthenticatedAdmin;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Tài khoản phân phối/xưởng khóa cửa hàng theo role.
 * Admin chọn cửa hàng qua header hoặc xem gộp bằng scope=all.
 */
public class ShopContextFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		String headerShop = request.getHeader(ShopContext.HEADER);
		boolean scopeAll = "all".equalsIgnoreCase(request.getParameter("scope"));
		if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedAdmin admin) {
			if (admin.role() == UserRole.ADMIN) {
				ShopContext.set(headerShop);
				ShopScope.setAll(scopeAll);
			} else {
				ShopContext.set(admin.shopCode());
				ShopScope.setAll(false);
			}
		} else {
			ShopContext.set(headerShop);
			ShopScope.setAll(scopeAll);
		}
		try {
			filterChain.doFilter(request, response);
		} finally {
			ShopContext.clear();
			ShopScope.clear();
		}
	}
}
