package com.aluminum.manager.inventory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import org.junit.jupiter.api.Test;

class StockConfirmationPolicyTest {

	@Test
	void onlyManufacturingSalesSkipStock() {
		assertEquals(false, StockConfirmationPolicy.skipsStock(true, "SALE"));
		assertEquals(true, StockConfirmationPolicy.skipsStock(false, "SALE"));
		assertEquals(false, StockConfirmationPolicy.skipsStock(false, "PURCHASE"));
	}

	@Test
	void secondConfirmIsRejected() {
		assertDoesNotThrow(() -> StockConfirmationPolicy.requireDraft("DRAFT"));
		BusinessException ex = assertThrows(BusinessException.class,
				() -> StockConfirmationPolicy.requireDraft("CONFIRMED"));
		assertEquals(ErrorCode.DOCUMENT_ALREADY_CONFIRMED, ex.getCode());
	}

	@Test
	void confirmedDocumentCanBeReversedOnlyOnce() {
		assertDoesNotThrow(() -> StockConfirmationPolicy.requireReversible("CONFIRMED", false));
		BusinessException ex = assertThrows(BusinessException.class,
				() -> StockConfirmationPolicy.requireReversible("CONFIRMED", true));
		assertEquals(ErrorCode.DOCUMENT_NOT_REVERSIBLE, ex.getCode());
	}
}
