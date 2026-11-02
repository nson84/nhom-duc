package com.aluminum.manager.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class DocumentCodesTest {

	@Test
	void formatsPurchaseAndSaleCodesForTheMonth() {
		YearMonth october = YearMonth.of(2026, 10);
		assertEquals("PN-202610-0001", DocumentCodes.format("PURCHASE", october, 1));
		assertEquals("PX-202610-0001", DocumentCodes.format("SALE", october, 1));
		assertEquals("DC-202610-0012", DocumentCodes.format("ADJUSTMENT_OUT", october, 12));
		assertEquals("PX-202610-10000", DocumentCodes.format("SALE", october, 10_000));
	}

	@Test
	void adjustmentTypesShareOneSequence() {
		assertEquals("ADJUSTMENT", DocumentCodes.sequenceKey("ADJUSTMENT_IN"));
		assertEquals("ADJUSTMENT", DocumentCodes.sequenceKey("ADJUSTMENT_OUT"));
		assertEquals("SALE", DocumentCodes.sequenceKey("SALE"));
	}
}
