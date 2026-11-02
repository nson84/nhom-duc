package com.aluminum.manager.security;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CurrentAdmin {

	private CurrentAdmin() {
	}

	public static AuthenticatedAdmin get() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedAdmin admin)) {
			throw new BusinessException(ErrorCode.UNAUTHORIZED, "Bạn cần đăng nhập", null);
		}
		return admin;
	}
}
