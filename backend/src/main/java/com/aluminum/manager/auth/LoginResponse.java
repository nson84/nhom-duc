package com.aluminum.manager.auth;

public record LoginResponse(
		String token,
		String tokenType,
		long expiresInSeconds,
		boolean mustChangePassword,
		String username,
		String role,
		String shopCode
) {
}
