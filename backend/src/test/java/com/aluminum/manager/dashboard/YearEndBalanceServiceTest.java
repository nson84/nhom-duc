package com.aluminum.manager.dashboard;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.shop.ShopAccess;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class YearEndBalanceServiceTest {
    @Test
    void wrongStartSuggestsExactDayAfterPreviousClose() {
        var failure = assertThrows(BusinessException.class, () -> YearEndBalanceService.validatePeriod(
                LocalDate.of(2026, 2, 23), LocalDate.of(2026, 3, 1), LocalDate.of(2026, 2, 27), null));
        assertTrue(failure.getMessage().contains("27/02/2026"));
        assertTrue(failure.getMessage().contains("28/02/2026"));
        var rollover = assertThrows(BusinessException.class, () -> YearEndBalanceService.validatePeriod(
                LocalDate.of(2026, 1, 2), LocalDate.of(2026, 2, 1), LocalDate.of(2025, 12, 31), null));
        assertTrue(rollover.getMessage().contains("01/01/2026"));
    }

    @Test
    void closingAgainCannotOverwriteHistoricalSnapshot() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class, invocation -> {
            if (invocation.getMethod().getName().equals("queryForObject")) {
                if (invocation.getArgument(1) == Integer.class) return Integer.valueOf(1);
                return Long.valueOf(2);
            }
            return null;
        });
        var service = new YearEndBalanceService(jdbc, mock(ShopAccess.class));
        var failure = assertThrows(BusinessException.class, () -> service.save(2025,
                LocalDate.of(2025, 2, 18), LocalDate.of(2025, 2, 28),
                java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO));
        assertTrue(failure.getMessage().contains("Kỳ đã chốt"));
        org.mockito.Mockito.verify(jdbc, org.mockito.Mockito.never())
                .update(anyString(), any(Object[].class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void cannotSaveEndBeforeStart() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ShopAccess shops = mock(ShopAccess.class);
        when(shops.currentId()).thenReturn(2L);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());
        var service = new YearEndBalanceService(jdbc, shops);
        BusinessException failure = assertThrows(BusinessException.class, () -> service.saveDraftPeriod(
                2025, LocalDate.of(2025, 2, 1), LocalDate.of(2026, 1, 31)));
        assertTrue(failure.getMessage().contains("ngày bắt đầu"));
    }

    @Test
    void customTetPeriodCanCrossCalendarYear() {
        assertDoesNotThrow(() -> YearEndBalanceService.validateNamedPeriod(2025,
                LocalDate.of(2025, 1, 29), LocalDate.of(2026, 2, 16)));
        assertThrows(BusinessException.class, () -> YearEndBalanceService.validateNamedPeriod(2025,
                LocalDate.of(2024, 1, 1), LocalDate.of(2026, 2, 16)));
        assertThrows(BusinessException.class, () -> YearEndBalanceService.validateNamedPeriod(2025,
                LocalDate.of(2025, 2, 1), LocalDate.of(2026, 4, 1)));
    }

    @Test
    void nextNamedYearStartsAfterTetClosing() {
        var period = YearEndBalanceService.upcomingPeriod(2026, LocalDate.of(2027, 2, 5));
        assertEquals(LocalDate.of(2027, 2, 6), period.periodFrom());
        assertEquals(LocalDate.of(2028, 2, 5), period.periodTo());
    }

    @Test
    void tetClosingCarriesIntoNextPeriodWithoutCalendarGap() {
        LocalDate tetClose = LocalDate.of(2027, 2, 5);
        assertDoesNotThrow(() -> YearEndBalanceService.validatePeriod(
                LocalDate.of(2026, 2, 17), tetClose, null, LocalDate.of(2027, 2, 6)));
        assertDoesNotThrow(() -> YearEndBalanceService.validatePeriod(
                LocalDate.of(2027, 2, 6), LocalDate.of(2028, 1, 25), tetClose, null));
        assertThrows(BusinessException.class, () -> YearEndBalanceService.validatePeriod(
                LocalDate.of(2027, 2, 7), LocalDate.of(2028, 1, 25), tetClose, null));
    }
}
