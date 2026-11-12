package com.aluminum.manager.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class JwtServiceTest {

	private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
	private final JwtService jwtService = new JwtService("local-dev-only-change-before-packaging-32b", -1, jdbc);

	@Test
	void issuesATokenThatRoundTrips() {
		String token = jwtService.issue(7, "admin", "ADMIN", null);
		JwtService.JwtClaims claims = jwtService.parse(token);
		assertEquals(7, claims.userId());
		assertEquals("admin", claims.username());
		assertEquals("ADMIN", claims.role());
		assertEquals(Long.MAX_VALUE, claims.expiresAtEpochSecond());
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
		when(jdbc.queryForObject(anyString(), eq(Boolean.class), anyString())).thenReturn(true);
		assertThrows(BusinessException.class, () -> jwtService.parse(token));
	}

	@Test
	void rejectsAnExpiredToken() {
		JwtService expired = new JwtService("local-dev-only-change-before-packaging-32b", 0, jdbc);
		String token = expired.issue(7, "admin", "ADMIN", null);
		assertThrows(BusinessException.class, () -> expired.parse(token));
	}
}
