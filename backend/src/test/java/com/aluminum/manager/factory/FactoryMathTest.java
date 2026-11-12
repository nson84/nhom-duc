package com.aluminum.manager.factory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class FactoryMathTest {
	private static BigDecimal n(String value) {
		return new BigDecimal(value);
	}

	@Test
	void multipleScrapSalesReduceOnlyTheUnexplainedLoss() {
		BigDecimal opening = n("0");
		BigDecimal purchased = n("4000");
		BigDecimal finishedSold = n("3200");
		BigDecimal scrapSold = n("50").add(n("80")).add(n("70"));
		BigDecimal closing = n("500");

		assertEquals(n("800"), FactoryMath.discrepancy(opening, purchased, finishedSold));
		assertEquals(n("100"), FactoryMath.estimatedLoss(
				opening, purchased, finishedSold, scrapSold, closing));
	}

	@Test
	void carriedStockIsNotExpensedTwiceNextYear() {
		BigDecimal purchases = n("100000000").add(n("240000000"));
		BigDecimal closing = n("42500000");
		assertEquals(n("297500000"), FactoryMath.materialCost(n("0"), purchases, closing));
		assertEquals(n("102500000"), FactoryMath.profit(
				n("500000000"), n("0"), n("297500000"), n("100000000")));
		assertEquals(n("42500000"), FactoryMath.materialCost(closing, n("0"), n("0")));
	}

	@Test
	void scrapRevenueIsAddedOnce() {
		assertEquals(n("106500000"), FactoryMath.profit(
				n("500000000"), n("4000000"), n("297500000"), n("100000000")));
	}
}
