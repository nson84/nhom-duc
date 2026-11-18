package com.aluminum.manager.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.aluminum.manager.dashboard.YearEndBalanceService;
import com.aluminum.manager.inventory.DocumentSequenceService;
import com.aluminum.manager.shop.ShopAccess;
import com.aluminum.manager.system.AuditService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class FinanceServiceMissingCostTest {
    private FinanceService service(boolean missingActual, boolean missingStandard) {
        JdbcTemplate jdbc = mock(JdbcTemplate.class, invocation -> {
            if (!invocation.getMethod().getName().equals("queryForObject")) return null;
            String sql = invocation.getArgument(0);
            Class<?> type = invocation.getArgument(1);
            if (type == Integer.class) return missingStandard ? 1 : 0;
            if (sql.contains("cost_total_snapshot") && !sql.contains("standard_cost_total_snapshot"))
                return missingActual ? null : new BigDecimal("60");
            if (sql.contains("standard_cost_total_snapshot")) return new BigDecimal("60");
            if (sql.contains("SUM(total_amount)") && sql.contains("'SALE'")) return new BigDecimal("100");
            return BigDecimal.ZERO;
        });
        ShopAccess shops = mock(ShopAccess.class);
        when(shops.apply(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        return new FinanceService(jdbc, mock(DocumentSequenceService.class), mock(AuditService.class),
                Clock.systemUTC(), shops, mock(YearEndBalanceService.class));
    }

    @Test
    void missingActualCostPreservesRevenueButSuppressesActualProfit() {
        var report = service(true, false).profitReport(LocalDate.of(2025, 2, 18), LocalDate.of(2025, 2, 28));
        assertEquals(0, new BigDecimal("100").compareTo(report.revenue()));
        assertNull(report.cogs());
        assertNull(report.grossProfit());
        assertNull(report.netProfit());
        assertNull(report.priceVariance());
        assertEquals(0, new BigDecimal("40").compareTo(report.standardGrossProfit()));
    }

    @Test
    void missingStandardCostSuppressesEstimatedProfit() {
        var report = service(false, true).profitReport(LocalDate.of(2025, 2, 18), LocalDate.of(2025, 2, 28));
        assertFalse(report.standardCostComplete());
        assertNull(report.standardGrossProfit());
        assertNull(report.standardNetProfit());
        assertNull(report.priceVariance());
        assertEquals(0, new BigDecimal("40").compareTo(report.grossProfit()));
    }
}
