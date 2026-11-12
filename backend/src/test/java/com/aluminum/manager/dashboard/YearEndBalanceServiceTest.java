package com.aluminum.manager.dashboard;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aluminum.manager.common.BusinessException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class YearEndBalanceServiceTest {
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
