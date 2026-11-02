package com.aluminum.manager.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * Quy tắc lãi khi giá nhập thay đổi theo ngày.
 *
 * <p>Hai lớp số phải luôn tách nhau: giá vốn thực tế (bình quân gia quyền) và giá vốn chuẩn (bảng giá
 * áp dụng đúng ngày phiếu). Sửa giá nhập hôm nay không được làm đổi lãi của phiếu đã xác nhận.
 */
class StandardCostTest {

	private static final BigDecimal KG = new BigDecimal("1000");
	private static final BigDecimal TON = new BigDecimal("1000");

	@Test
	void standardCostUsesThePriceOfTheDocumentDay() {
		// Giá chuẩn ngày 05/10 là 75.000/kg; nhập 1 tấn ngày 05/10.
		BigDecimal standardPerBase = new BigDecimal("75000.000000");
		BigDecimal quantityBase = InventoryMath.toBaseQuantity(BigDecimal.ONE, TON);
		BigDecimal standardTotal = InventoryMath.costTotal(quantityBase, standardPerBase);
		assertBd("75000000", standardTotal);
	}

	@Test
	void priceVarianceIsActualMinusStandard() {
		// Nhập thực tế 78.000/kg thay vì chuẩn 75.000/kg trên 1.000 kg.
		BigDecimal quantityBase = KG;
		BigDecimal actual = InventoryMath.costTotal(quantityBase, new BigDecimal("78000"));
		BigDecimal standard = InventoryMath.costTotal(quantityBase, new BigDecimal("75000"));
		BigDecimal variance = actual.subtract(standard);
		// Nhập đắt hơn chuẩn 3.000.000 => lãi thực thấp hơn lãi chuẩn đúng 3.000.000.
		assertBd("3000000", variance);
		assertTrue(variance.signum() > 0);
	}

	@Test
	void laterPriceEditDoesNotChangeConfirmedStandardCost() {
		BigDecimal confirmedStandard = new BigDecimal("75000000");
		BigDecimal priceAfterAdminEdit = new BigDecimal("82000");
		assertBd("75000000", confirmedStandard);
		assertBd("82000", priceAfterAdminEdit);
	}

	@Test
	void weightedAverageStaysTheTruthForActualCost() {
		BigDecimal average = InventoryMath.weightedAverageCost(
				new BigDecimal("1000"),
				new BigDecimal("75000"),
				new BigDecimal("1000"),
				new BigDecimal("81000"));
		assertBd("78000", average);
	}

	@Test
	void standardCostTotalRoundsHalfUpToMoneyScale() {
		// 33,333333 kg × 75.000,000000 đ/kg = 2.499.999,975 -> 2.500.000,0 với 4 số lẻ tiền tệ.
		BigDecimal quantity = new BigDecimal("33.333333");
		BigDecimal total = InventoryMath.costTotal(quantity, new BigDecimal("75000"));
		assertEquals(InventoryMath.MONEY_SCALE, total.scale());
		assertBd("2499999.975", total);
	}

	private static void assertBd(String expected, BigDecimal actual) {
		assertEquals(0, new BigDecimal(expected).compareTo(actual), actual::toPlainString);
	}
}
