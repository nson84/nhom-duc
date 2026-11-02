package com.aluminum.manager.attendance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aluminum.manager.attendance.AttendanceCalculator.OvertimeLine;
import com.aluminum.manager.common.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttendanceCalculatorTest {

	@Test
	void februaryHasTwentyNineDaysInALeapYear() {
		assertEquals(29, AttendanceCalculator.daysInMonth(2024, 2));
		assertEquals(29, AttendanceCalculator.daysInMonth(2028, 2));
	}

	@Test
	void februaryHasTwentyEightDaysInACommonYear() {
		assertEquals(28, AttendanceCalculator.daysInMonth(2026, 2));
		assertEquals(28, AttendanceCalculator.daysInMonth(2025, 2));
	}

	@Test
	void creditsConfiguredFullAndHalfDays() {
		assertBd("1", AttendanceCalculator.creditedDays("PRESENT", new BigDecimal("1"), new BigDecimal("0.5")));
		assertBd("0.5", AttendanceCalculator.creditedDays("HALF_DAY", new BigDecimal("1"), new BigDecimal("0.5")));
		assertBd("0", AttendanceCalculator.creditedDays("ABSENT", new BigDecimal("1"), new BigDecimal("0.5")));
		assertBd("1", AttendanceCalculator.creditedDays("HOLIDAY", new BigDecimal("1"), new BigDecimal("0.5")));
	}

	@Test
	void calculatesADayShiftAfterTheConfiguredBreak() {
		LocalDateTime start = LocalDateTime.of(2026, 10, 29, 8, 0);
		LocalDateTime end = LocalDateTime.of(2026, 10, 29, 17, 0);
		assertBd("8.000", AttendanceCalculator.workedHours(start, end, true, 60, 1));
	}

	@Test
	void overnightShiftEndsOnTheNextDay() {
		LocalDateTime start = LocalDateTime.of(2026, 10, 29, 22, 0);
		LocalDateTime endSameCalendarDay = LocalDateTime.of(2026, 10, 29, 6, 0);
		assertBd("8.000", AttendanceCalculator.workedHours(start, endSameCalendarDay, true, 0, 1));
		assertThrows(BusinessException.class,
				() -> AttendanceCalculator.workedHours(start, endSameCalendarDay, false, 0, 1));
	}

	@Test
	void unapprovedOvertimeIsExcludedFromPay() {
		BigDecimal pay = AttendanceCalculator.approvedOvertimePay(List.of(
				new OvertimeLine(false, new BigDecimal("150000")),
				new OvertimeLine(true, new BigDecimal("80000"))));
		assertBd("80000", pay);
	}

	private static void assertBd(String expected, BigDecimal actual) {
		assertEquals(0, new BigDecimal(expected).compareTo(actual), actual::toPlainString);
	}
}
