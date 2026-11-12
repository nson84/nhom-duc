package com.aluminum.manager.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aluminum.manager.factory.FactoryDtos.RawLayerDto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class FactoryLayerAllocatorTest {
	private static RawLayerDto source(long id, String kg, String price) {
		return new RawLayerDto(id, "PN" + id, LocalDateTime.of(2026, 1, (int) id, 0, 0),
				new BigDecimal(kg), new BigDecimal(price), BigDecimal.ZERO);
	}

	@Test
	void remainingTenTonsUsesEightFromLatestAndTwoFromPrevious() {
		List<RawLayerDto> rows = FactoryLayerAllocator.allocate(new BigDecimal("10000"),
				List.of(source(10, "8000", "9000"), source(9, "5000", "8000")));
		assertEquals(new BigDecimal("8000"), rows.get(0).quantityKg());
		assertEquals(new BigDecimal("2000"), rows.get(1).quantityKg());
		assertEquals(new BigDecimal("88000000.0000"), rows.stream()
				.map(RawLayerDto::valueAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
	}

	@Test
	void remainingAmountCanSpanThreePurchasesAndRejectMissingHistory() {
		List<RawLayerDto> sources = List.of(source(10, "1000", "9000"),
				source(9, "2000", "8000"), source(8, "3000", "7000"));
		List<RawLayerDto> rows = FactoryLayerAllocator.allocate(new BigDecimal("4000"), sources);
		assertEquals(3, rows.size());
		assertEquals(new BigDecimal("1000"), rows.get(2).quantityKg());
		assertThrows(IllegalArgumentException.class,
				() -> FactoryLayerAllocator.allocate(new BigDecimal("6001"), sources));
	}
}
