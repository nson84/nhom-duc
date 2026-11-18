package com.aluminum.manager.inventory;

import com.aluminum.manager.common.BusinessException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockEditPolicyTest {
	private final LocalDateTime confirmed = LocalDateTime.of(2026, 11, 13, 10, 30);

	@Test
	void acceptsUntilExactly24Hours() {
		assertDoesNotThrow(() -> StockEditPolicy.requireWithin24Hours(confirmed, confirmed.plusHours(24)));
	}

	@Test
	void rejectsAfter24HoursAndInvalidTimestamps() {
		assertThrows(BusinessException.class,
				() -> StockEditPolicy.requireWithin24Hours(confirmed, confirmed.plusHours(24).plusNanos(1)));
		assertThrows(BusinessException.class,
				() -> StockEditPolicy.requireWithin24Hours(confirmed, confirmed.minusSeconds(1)));
		assertThrows(BusinessException.class,
				() -> StockEditPolicy.requireWithin24Hours(null, confirmed));
	}
}
