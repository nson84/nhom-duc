package com.aluminum.manager.finance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FinanceServiceInvoiceMarginTest {
	private static final LocalDate DAY = LocalDate.of(2025, 6, 1);

	@Test
	void missingReferenceCostDoesNotBecomeZeroCostProfit() {
		var margin = FinanceService.invoiceMargin(1, "BH-1", DAY, "Khách A",
				new BigDecimal("100000"), null, 1);
		assertFalse(margin.costComplete());
		assertNull(margin.estimatedMaterialCost());
		assertNull(margin.estimatedGrossProfit());
	}

	@Test
	void partialReferenceCostDoesNotShowPartialProfit() {
		var margin = FinanceService.invoiceMargin(1, "BH-1", DAY, "Khách A",
				new BigDecimal("100000"), new BigDecimal("20000"), 1);
		assertFalse(margin.costComplete());
		assertNull(margin.estimatedGrossProfit());
	}

	@Test
	void completeReferenceCostCalculatesEstimatedGrossOnly() {
		var margin = FinanceService.invoiceMargin(1, "BH-1", DAY, "Khách A",
				new BigDecimal("100000"), new BigDecimal("60000"), 0);
		assertTrue(margin.costComplete());
		assertEquals(0, new BigDecimal("40000").compareTo(margin.estimatedGrossProfit()));
	}
}
