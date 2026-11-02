package com.aluminum.manager.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardSummary(
		LocalDate from,
		LocalDate to,
		BigDecimal revenue,
		BigDecimal cashCollected,
		BigDecimal cogs,
		BigDecimal grossProfit,
		BigDecimal operatingExpenses,
		BigDecimal inventoryValue,
		BigDecimal payroll
) {
}
