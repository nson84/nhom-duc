package com.aluminum.manager.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.shop.ShopAccess;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MonthlyInventoryValuationTest {
    @Test
    void endingInventoryUsesNewestPurchaseLayers() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class, invocation -> List.of(
                new BigDecimal[]{new BigDecimal("10"), new BigDecimal("200")},
                new BigDecimal[]{new BigDecimal("20"), new BigDecimal("100")}));
        var valuation = new MonthlyInventoryValuation(jdbc, mock(ShopAccess.class));
        assertEquals(0, new BigDecimal("225").compareTo(
                valuation.rawValue(1, new BigDecimal("15"), LocalDate.of(2025, 2, 28))));
        assertThrows(BusinessException.class,
                () -> valuation.rawValue(1, new BigDecimal("31"), LocalDate.of(2025, 2, 28)));
    }

    @Test
    void internalTransferReducesMaterialCostWithoutAddingExternalRevenue() {
        BigDecimal profit = MonthlyInventoryValuation.profit(new BigDecimal("100"), new BigDecimal("20"),
                new BigDecimal("80"), new BigDecimal("30"), new BigDecimal("10"), new BigDecimal("5"));
        assertEquals(0, new BigDecimal("35").compareTo(profit));
    }
}
