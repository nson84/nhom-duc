package com.aluminum.manager.attendance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public final class AttendanceDtos {

	private AttendanceDtos() {
	}

	public record PayRateDto(
			Long id,
			Long employeeId,
			LocalDate effectiveFrom,
			LocalDate effectiveTo,
			BigDecimal dailyRate,
			BigDecimal standardHoursPerDay,
			BigDecimal overtimeWeekdayRate,
			BigDecimal overtimeRestdayRate,
			BigDecimal overtimeHolidayRate
	) {
	}

	public record PayRateRequest(
			@NotNull(message = "Chọn ngày áp dụng") LocalDate effectiveFrom,
			LocalDate effectiveTo,
			@NotNull(message = "Nhập mức công ngày")
			@DecimalMin(value = "0", message = "Mức công ngày không được âm") BigDecimal dailyRate,
			@NotNull(message = "Nhập giờ chuẩn mỗi ngày")
			@DecimalMin(value = "0.5", message = "Giờ chuẩn phải lớn hơn 0") BigDecimal standardHoursPerDay,
			@DecimalMin(value = "0", message = "Đơn giá tăng ca không được âm") BigDecimal overtimeWeekdayRate,
			@DecimalMin(value = "0", message = "Đơn giá tăng ca không được âm") BigDecimal overtimeRestdayRate,
			@DecimalMin(value = "0", message = "Đơn giá tăng ca không được âm") BigDecimal overtimeHolidayRate
	) {
	}

	public record EmployeeDto(
			Long id,
			String employeeCode,
			String fullName,
			String phone,
			String address,
			LocalDate hireDate,
			LocalDate terminationDate,
			boolean active,
			String notes,
			PayRateDto currentPayRate
	) {
	}

	public record EmployeeRequest(
			@NotBlank(message = "Nhập họ tên nhân viên") @Size(max = 150) String fullName,
			@NotBlank(message = "Nhập số điện thoại") @Size(max = 30) String phone,
			@Size(max = 300) String address,
			LocalDate hireDate,
			LocalDate terminationDate,
			Boolean active,
			String notes,
			@DecimalMin(value = "0", message = "Mức công ngày không được âm") BigDecimal initialDailyRate,
			@DecimalMin(value = "0", message = "Đơn giá tăng ca không được âm") BigDecimal initialOvertimeWeekdayRate,
			@DecimalMin(value = "0", message = "Đơn giá tăng ca không được âm") BigDecimal initialOvertimeRestdayRate,
			@DecimalMin(value = "0", message = "Đơn giá tăng ca không được âm") BigDecimal initialOvertimeHolidayRate
	) {
	}

	public record HolidayDto(
			Long id,
			LocalDate holidayDate,
			String name,
			String holidayType,
			boolean paid,
			String notes,
			BigDecimal dayPay,
			BigDecimal bonusAmount,
			List<Long> receivedEmployeeIds
	) {
	}

	public record HolidayRequest(
			@NotNull(message = "Chọn ngày nghỉ lễ") LocalDate holidayDate,
			@NotBlank(message = "Nhập tên ngày lễ") @Size(max = 150) String name,
			@NotBlank(message = "Chọn loại ngày lễ") String holidayType,
			Boolean paid,
			@Size(max = 500) String notes,
			@DecimalMin(value = "0", message = "Lương ngày nghỉ không được âm") BigDecimal dayPay,
			@DecimalMin(value = "0", message = "Tiền thưởng không được âm") BigDecimal bonusAmount,
			List<@NotNull Long> receivedEmployeeIds
	) {
	}

	public record DayKindRequest(
			@NotNull(message = "Chọn ngày") LocalDate holidayDate,
			String holidayType,
			@DecimalMin(value = "0", message = "Lương ngày nghỉ không được âm") BigDecimal dayPay
	) {
	}

	public record RollCallItem(
			@NotNull(message = "Chọn nhân viên") Long employeeId,
			boolean morningPresent,
			boolean afternoonPresent,
			@DecimalMin("0") @jakarta.validation.constraints.DecimalMax("24")
			@jakarta.validation.constraints.Digits(integer = 2, fraction = 3) BigDecimal overtimeHours
	) {
	}

	public record RollCallRequest(
			@NotNull(message = "Chọn ngày điểm danh") LocalDate workDate,
			@NotEmpty(message = "Chưa tích có hoặc vắng") List<@Valid RollCallItem> items
	) {
	}

	public record AttendancePolicyDto(
			Long id,
			LocalDate effectiveFrom,
			LocalDate effectiveTo,
			LocalTime standardStartTime,
			LocalTime standardEndTime,
			int breakMinutes,
			int lateGraceMinutes,
			int earlyLeaveGraceMinutes,
			int roundingMinutes,
			BigDecimal halfDayThresholdHours,
			BigDecimal fullDayCreditedDays,
			BigDecimal halfDayCreditedDays,
			boolean overnightShiftAllowed,
			String notes
	) {
	}

	public record AttendancePolicyRequest(
			@NotNull(message = "Chọn ngày áp dụng") LocalDate effectiveFrom,
			@NotNull(message = "Nhập giờ bắt đầu") LocalTime standardStartTime,
			@NotNull(message = "Nhập giờ kết thúc") LocalTime standardEndTime,
			@Min(value = 0, message = "Phút nghỉ không âm") int breakMinutes,
			@Min(value = 0, message = "Phút đi muộn cho phép không âm") int lateGraceMinutes,
			@Min(value = 0, message = "Phút về sớm cho phép không âm") int earlyLeaveGraceMinutes,
			@Min(value = 1, message = "Làm tròn tối thiểu 1 phút") int roundingMinutes,
			@NotNull @DecimalMin(value = "0") BigDecimal halfDayThresholdHours,
			@NotNull @DecimalMin(value = "0") BigDecimal fullDayCreditedDays,
			@NotNull @DecimalMin(value = "0") BigDecimal halfDayCreditedDays,
			boolean overnightShiftAllowed,
			@Size(max = 500) String notes
	) {
	}

	public record OvertimeDto(
			Long id,
			Long attendanceId,
			String overtimeType,
			LocalDateTime startAt,
			LocalDateTime endAt,
			BigDecimal overtimeHours,
			BigDecimal hourlyRateSnapshot,
			BigDecimal amount,
			boolean approved,
			String notes
	) {
	}

	public record OvertimeRequest(
			LocalDateTime startAt,
			LocalDateTime endAt,
			@NotNull(message = "Nhập số giờ tăng ca")
			@DecimalMin(value = "0", inclusive = false, message = "Số giờ tăng ca phải lớn hơn 0") BigDecimal overtimeHours,
			Boolean approved,
			@Size(max = 500) String notes
	) {
	}

	public record AttendanceDayDto(
			Long id,
			Long employeeId,
			LocalDate workDate,
			String status,
			boolean morningPresent,
			boolean afternoonPresent,
			LocalDateTime clockIn,
			LocalDateTime clockOut,
			BigDecimal workedHours,
			BigDecimal creditedDays,
			int lateMinutes,
			int earlyLeaveMinutes,
			String notes,
			BigDecimal dailyRateSnapshot,
			BigDecimal bonusAmount,
			String bonusNotes,
			boolean locked,
			List<OvertimeDto> overtimes
	) {
	}

	public record AttendanceUpsertRequest(
			@NotBlank(message = "Chọn trạng thái chấm công") String status,
			Boolean morningPresent,
			Boolean afternoonPresent,
			LocalDateTime clockIn,
			LocalDateTime clockOut,
			@Size(max = 500) String notes
	) {
	}

	public record BulkAttendanceItem(
			@NotNull Long employeeId,
			@NotNull LocalDate workDate,
			@NotBlank String status,
			LocalDateTime clockIn,
			LocalDateTime clockOut,
			@Size(max = 500) String notes
	) {
	}

	public record BulkAttendanceRequest(
			@NotEmpty(message = "Chọn ít nhất một dòng chấm công") List<@Valid BulkAttendanceItem> items
	) {
	}

	public record EmployeeMonthlyRowDto(
			Long employeeId,
			String employeeCode,
			String fullName,
			boolean active,
			BigDecimal currentDailyRate,
			BigDecimal totalCreditedDays,
			BigDecimal totalWorkedHours,
			int totalLateMinutes,
			int totalEarlyLeaveMinutes,
			BigDecimal approvedOvertimeHours,
			BigDecimal approvedOvertimePay,
			List<AttendanceDayDto> days
	) {
	}

	public record MonthlyAttendanceBoardDto(
			int year,
			int month,
			int daysInMonth,
			boolean periodClosed,
			AttendancePolicyDto policy,
			List<HolidayDto> holidays,
			List<EmployeeMonthlyRowDto> employees,
			List<LocalDate> lockedDates
	) {
	}

	public record PayrollDetailDto(
			Long id,
			Long employeeId,
			String employeeCode,
			String fullName,
			BigDecimal creditedDays,
			BigDecimal basePay,
			BigDecimal overtimePay,
			BigDecimal bonusPay,
			BigDecimal allowances,
			BigDecimal deductions,
			BigDecimal totalPay,
			BigDecimal advanceAmount,
			BigDecimal overtimeHours,
			BigDecimal holidayBonusPay,
			BigDecimal holidayBonusReceived
	) {
	}

	public record PayrollPeriodDto(
			Long id,
			int year,
			int month,
			String status,
			LocalDateTime closedAt,
			LocalDateTime reopenedAt,
			String reopenReason,
			String notes,
			BigDecimal totalBasePay,
			BigDecimal totalOvertimePay,
			BigDecimal totalAllowances,
			BigDecimal totalDeductions,
			BigDecimal grandTotalPay,
			BigDecimal paidAmount,
			List<PayrollDetailDto> details
	) {
	}

	public record PayrollPeriodRequest(
			@Min(2000) @Max(2100) int year,
			@Min(1) @Max(12) int month,
			@Size(max = 500) String notes
	) {
	}

	public record PayrollAdjustDetailRequest(
			@NotNull @DecimalMin(value = "0", message = "Phụ cấp không được âm") BigDecimal allowances,
			@NotNull @DecimalMin(value = "0", message = "Tiền ứng không được âm") BigDecimal advanceAmount
	) {
	}

	public record AttendanceBonusRequest(
			@NotNull(message = "Nhập tiền thưởng")
			@DecimalMin(value = "0", message = "Tiền thưởng không được âm") BigDecimal amount,
			@Size(max = 500) String notes
	) {
	}

	public record ReopenPayrollRequest(
			@NotBlank(message = "Nhập lý do mở lại kỳ công") @Size(max = 500) String reason
	) {
	}

	public record PayPayrollRequest(
			@NotNull(message = "Nhập số tiền chi lương")
			@DecimalMin(value = "0", inclusive = false, message = "Số tiền phải lớn hơn 0") BigDecimal amount,
			@Size(max = 500) String description,
			@Size(max = 80) String idempotencyKey
	) {
	}
}
