package com.aluminum.manager.dashboard;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.shop.ShopAccess;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class DashboardClosingCountTest {
	@Test
	void backdatedMovementCreatedAfterCountInvalidatesCountEvidence() {
		JdbcTemplate jdbc = mock(JdbcTemplate.class);
		ShopAccess shops = mock(ShopAccess.class);
		when(shops.isDistribution()).thenReturn(true);
		when(shops.currentId()).thenReturn(2L);
		when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
				.thenAnswer(call -> {
					String sql = call.getArgument(0);
					if (sql.contains("distribution_inventory_count_evidence")) {
						assertTrue(sql.contains("m.created_at > c.counted_at"));
						return 1;
					}
					return 0;
				});
		var service = new DashboardService(jdbc,
				Clock.fixed(Instant.parse("2026-11-12T00:00:00Z"), ZoneOffset.UTC),
				shops, mock(YearEndBalanceService.class));
		BusinessException failure = assertThrows(BusinessException.class,
				() -> service.closeDistribution(LocalDate.of(2025, 1, 1),
						LocalDate.of(2025, 12, 31), true, 2025));
		assertTrue(failure.getMessage().contains("Chưa kiểm kê đầy đủ"));
	}
}
