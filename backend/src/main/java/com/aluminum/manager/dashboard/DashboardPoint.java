package com.aluminum.manager.dashboard;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DashboardPoint(
		LocalDate day,
		BigDecimal revenue,
		BigDecimal cashCollected,
		BigDecimal cogs,
		BigDecimal grossProfit
) {
}
