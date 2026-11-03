package com.aluminum.manager.security;

import com.aluminum.manager.auth.UserRole;

public record AuthenticatedAdmin(
		Long id,
		String username,
		boolean mustChangePassword,
		UserRole role,
		String shopCode
) {
}
