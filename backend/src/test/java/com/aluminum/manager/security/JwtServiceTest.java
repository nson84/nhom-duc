package com.aluminum.manager.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

	private final JwtService jwtService = new JwtService("local-dev-only-change-before-packaging-32b", 3600);

	@Test
	void issuesATokenThatRoundTrips() {
		String token = jwtService.issue(7, "admin", "ADMIN", null);
		JwtService.JwtClaims claims = jwtService.parse(token);
		assertEquals(7, claims.userId());
		assertEquals("admin", claims.username());
		assertEquals("ADMIN", claims.role());
	}

	@Test
	void rejectsATamperedToken() {
		String token = jwtService.issue(7, "admin", "ADMIN", null);
		String tampered = token.substring(0, token.length() - 2) + "aa";
		BusinessException ex = assertThrows(BusinessException.class, () -> jwtService.parse(tampered));
		assertEquals(ErrorCode.UNAUTHORIZED, ex.getCode());
	}

	@Test
	void rejectsARevokedToken() {
		String token = jwtService.issue(7, "admin", "ADMIN", null);
		jwtService.revoke(token);
		assertThrows(BusinessException.class, () -> jwtService.parse(token));
	}

	@Test
	void rejectsAnExpiredToken() {
		JwtService expired = new JwtService("local-dev-only-change-before-packaging-32b", 0);
		String token = expired.issue(7, "admin", "ADMIN", null);
		assertThrows(BusinessException.class, () -> expired.parse(token));
	}
}
