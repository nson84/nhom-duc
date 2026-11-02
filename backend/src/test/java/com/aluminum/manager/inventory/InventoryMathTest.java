package com.aluminum.manager.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class InventoryMathTest {

	@Test
	void rejectsZeroConversionFactor() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> InventoryMath.toBaseQuantity(new BigDecimal("10"), BigDecimal.ZERO));
		assertEquals(ErrorCode.VALIDATION_ERROR, ex.getCode());
	}

	@Test
	void sellsTenBarsAsThirtyFiveKilograms() {
		BigDecimal base = InventoryMath.toBaseQuantity(new BigDecimal("10"), new BigDecimal("3.5"));
		assertBd("35", base);
		BigDecimal after = InventoryMath.applyDelta(new BigDecimal("350"), base.negate());
		assertBd("315", after);
	}

	@Test
	void rejectsSaleBeyondStock() {
		BusinessException ex = assertThrows(BusinessException.class,
				() -> InventoryMath.applyDelta(new BigDecimal("20"), new BigDecimal("-35")));
		assertEquals(ErrorCode.INSUFFICIENT_STOCK, ex.getCode());
	}

	@Test
	void keepsDecimalQuantities() {
		assertBd("1.250000", InventoryMath.toBaseQuantity(new BigDecimal("1.25"), BigDecimal.ONE));
	}

	@Test
	void updatesMovingAverageWhenPurchasePriceChanges() {
		BigDecimal average = InventoryMath.weightedAverageCost(
				new BigDecimal("100"),
				new BigDecimal("70000"),
				new BigDecimal("50"),
				new BigDecimal("80000"));
		assertBd("73333.333333", average);
	}

	@Test
	void lineDiscountCannotMakeTotalNegative() {
		assertBd("190000", InventoryMath.lineTotal(new BigDecimal("2"), new BigDecimal("100000"), new BigDecimal("10000")));
		assertThrows(BusinessException.class,
				() -> InventoryMath.lineTotal(new BigDecimal("1"), new BigDecimal("1000"), new BigDecimal("2000")));
	}

	@Test
	void suggestsBarPriceFromPricePerKilogram() {
		assertBd("322000", InventoryMath.suggestedUnitPrice(new BigDecimal("92000"), new BigDecimal("3.5")));
	}

	@Test
	void soldPriceSnapshotStaysAtTheConfirmedValue() {
		BigDecimal sold = InventoryMath.snapshotMoney(new BigDecimal("92000"));
		BigDecimal laterCatalogPrice = new BigDecimal("99000");
		assertBd("92000", sold);
		assertBd("99000", laterCatalogPrice);
	}

	@Test
	void reversalNegatesTheOriginalMovement() {
		assertBd("-35", InventoryMath.reversalDelta(new BigDecimal("35")));
		assertBd("35", InventoryMath.reversalDelta(new BigDecimal("-35")));
	}

	private static void assertBd(String expected, BigDecimal actual) {
		assertEquals(0, new BigDecimal(expected).compareTo(actual), actual::toPlainString);
	}
}
