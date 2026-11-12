package com.aluminum.manager.factory;

import java.math.BigDecimal;

/** Cân đối khối lượng và giá trị nhôm của xưởng theo năm. */
public final class FactoryMath {
	private FactoryMath() {
	}

	public static BigDecimal discrepancy(BigDecimal openingKg, BigDecimal purchasedKg,
			BigDecimal finishedSoldKg) {
		return openingKg.add(purchasedKg).subtract(finishedSoldKg);
	}

	public static BigDecimal estimatedLoss(BigDecimal openingKg, BigDecimal purchasedKg,
			BigDecimal finishedSoldKg, BigDecimal scrapSoldKg, BigDecimal closingKg) {
		return discrepancy(openingKg, purchasedKg, finishedSoldKg)
				.subtract(scrapSoldKg).subtract(closingKg);
	}

	public static BigDecimal materialCost(BigDecimal openingValue, BigDecimal purchases,
			BigDecimal closingValue) {
		return openingValue.add(purchases).subtract(closingValue);
	}

	public static BigDecimal profit(BigDecimal finishedRevenue, BigDecimal scrapRevenue,
			BigDecimal materialCost, BigDecimal otherExpenses) {
		return finishedRevenue.add(scrapRevenue).subtract(materialCost).subtract(otherExpenses);
	}
}
