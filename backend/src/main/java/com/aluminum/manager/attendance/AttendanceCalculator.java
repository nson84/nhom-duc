package com.aluminum.manager.attendance;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

public final class AttendanceCalculator {

	private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

	private AttendanceCalculator() {
	}

	public static int daysInMonth(int year, int month) {
		return YearMonth.of(year, month).lengthOfMonth();
	}

	public static BigDecimal creditedDays(String status, BigDecimal fullDay, BigDecimal halfDay) {
		requireNonNegative(fullDay, "Công đủ ngày không được âm");
		requireNonNegative(halfDay, "Công nửa ngày không được âm");
		return switch (status) {
			case "PRESENT", "HOLIDAY", "PAID_LEAVE" -> fullDay;
			case "HALF_DAY" -> halfDay;
			case "ABSENT", "UNPAID_LEAVE", "UNMARKED" -> BigDecimal.ZERO.setScale(2, ROUNDING);
			default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trạng thái công không hợp lệ", null);
		};
	}

	public static LocalDateTime resolveClockOut(LocalDateTime clockIn, LocalDateTime clockOut, boolean overnightAllowed) {
		if (clockIn == null || clockOut == null) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cần đủ giờ vào và giờ ra", null);
		}
		if (clockOut.isAfter(clockIn)) {
			return clockOut;
		}
		if (!overnightAllowed) {
			throw new BusinessException(
					ErrorCode.VALIDATION_ERROR,
					"Giờ ra phải sau giờ vào. Ca qua đêm chưa được bật trong cấu hình.",
					null);
		}
		LocalDateTime nextDay = clockOut.plusDays(1);
		if (!nextDay.isAfter(clockIn)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Khoảng giờ làm việc không hợp lệ", null);
		}
		return nextDay;
	}

	public static BigDecimal workedHours(
			LocalDateTime clockIn,
			LocalDateTime clockOut,
			boolean overnightAllowed,
			int breakMinutes,
			int roundingMinutes) {
		if (breakMinutes < 0 || roundingMinutes < 1) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Cấu hình giờ làm việc không hợp lệ", null);
		}
		LocalDateTime end = resolveClockOut(clockIn, clockOut, overnightAllowed);
		long minutes = Duration.between(clockIn, end).toMinutes() - breakMinutes;
		if (minutes < 0) {
			minutes = 0;
		}
		if (roundingMinutes > 1) {
			minutes = Math.round(minutes / (double) roundingMinutes) * roundingMinutes;
		}
		return BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 3, ROUNDING);
	}

	public static int lateMinutes(LocalDateTime scheduledStart, LocalDateTime actualStart, int graceMinutes) {
		long late = Duration.between(scheduledStart, actualStart).toMinutes() - graceMinutes;
		return (int) Math.max(0, late);
	}

	public static int earlyLeaveMinutes(LocalDateTime scheduledEnd, LocalDateTime actualEnd, int graceMinutes) {
		long early = Duration.between(actualEnd, scheduledEnd).toMinutes() - graceMinutes;
		return (int) Math.max(0, early);
	}

	public static BigDecimal approvedOvertimePay(List<OvertimeLine> lines) {
		BigDecimal total = BigDecimal.ZERO;
		for (OvertimeLine line : lines) {
			if (line.approved()) {
				total = total.add(line.amount());
			}
		}
		return total.setScale(4, ROUNDING);
	}

	public static boolean isLeapFebruary(LocalDate date) {
		return date.getMonthValue() == 2 && daysInMonth(date.getYear(), 2) == 29;
	}

	private static void requireNonNegative(BigDecimal value, String message) {
		if (value == null || value.signum() < 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, message, null);
		}
	}

	public record OvertimeLine(boolean approved, BigDecimal amount) {
	}
}
