package com.aluminum.manager.attendance;

import com.aluminum.manager.attendance.AttendanceCalculator.OvertimeLine;
import com.aluminum.manager.attendance.AttendanceDtos.AttendanceDayDto;
import com.aluminum.manager.attendance.AttendanceDtos.AttendancePolicyDto;
import com.aluminum.manager.attendance.AttendanceDtos.AttendancePolicyRequest;
import com.aluminum.manager.attendance.AttendanceDtos.AttendanceUpsertRequest;
import com.aluminum.manager.attendance.AttendanceDtos.BulkAttendanceItem;
import com.aluminum.manager.attendance.AttendanceDtos.BulkAttendanceRequest;
import com.aluminum.manager.attendance.AttendanceDtos.EmployeeDto;
import com.aluminum.manager.attendance.AttendanceDtos.EmployeeMonthlyRowDto;
import com.aluminum.manager.attendance.AttendanceDtos.EmployeeRequest;
import com.aluminum.manager.attendance.AttendanceDtos.HolidayDto;
import com.aluminum.manager.attendance.AttendanceDtos.HolidayRequest;
import com.aluminum.manager.attendance.AttendanceDtos.MonthlyAttendanceBoardDto;
import com.aluminum.manager.attendance.AttendanceDtos.OvertimeDto;
import com.aluminum.manager.attendance.AttendanceDtos.OvertimeRequest;
import com.aluminum.manager.attendance.AttendanceDtos.PayPayrollRequest;
import com.aluminum.manager.attendance.AttendanceDtos.PayRateDto;
import com.aluminum.manager.attendance.AttendanceDtos.PayRateRequest;
import com.aluminum.manager.attendance.AttendanceDtos.PayrollAdjustDetailRequest;
import com.aluminum.manager.attendance.AttendanceDtos.PayrollDetailDto;
import com.aluminum.manager.attendance.AttendanceDtos.PayrollPeriodDto;
import com.aluminum.manager.attendance.AttendanceDtos.PayrollPeriodRequest;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.NameValidation;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.inventory.DocumentSequenceService;
import com.aluminum.manager.shop.ShopAccess;
import com.aluminum.manager.shop.ShopContext;
import com.aluminum.manager.inventory.InventoryMath;
import com.aluminum.manager.security.CurrentAdmin;
import com.aluminum.manager.system.AuditService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceService {

	private static final Set<String> VALID_STATUSES = Set.of(
			"UNMARKED", "PRESENT", "HALF_DAY", "ABSENT", "PAID_LEAVE", "UNPAID_LEAVE", "HOLIDAY");
	private static final Set<String> OVERTIME_TYPES = Set.of("WEEKDAY", "RESTDAY", "HOLIDAY");
	private static final Set<String> HOLIDAY_TYPES = Set.of("PUBLIC_HOLIDAY", "COMPANY_DAY_OFF");

	private final JdbcTemplate jdbc;
	private final AuditService audit;
	private final DocumentSequenceService sequences;
	private final Clock clock;
	private final ShopAccess shops;

	public AttendanceService(
			JdbcTemplate jdbc, AuditService audit, DocumentSequenceService sequences, Clock clock, ShopAccess shops) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.sequences = sequences;
		this.clock = clock;
		this.shops = shops;
	}

	// ---------- Employees ----------

	@Transactional(readOnly = true)
	public List<EmployeeDto> listEmployees(String query, Boolean active) {
		if (shops.isDistribution()) {
			return List.of();
		}
		StringBuilder sql = new StringBuilder("SELECT id FROM employees WHERE shop_id = ?");
		List<Object> args = new ArrayList<>();
		args.add(shops.idOf(ShopContext.MANUFACTURING));
		if (query != null && !query.isBlank()) {
			sql.append(" AND (employee_code LIKE ? OR full_name LIKE ? OR phone LIKE ?)");
			String like = "%" + query.trim() + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (active != null) {
			sql.append(" AND is_active = ?");
			args.add(active);
		}
		sql.append(" ORDER BY is_active DESC, employee_code ASC");
		List<Long> ids = jdbc.queryForList(sql.toString(), Long.class, args.toArray());
		LocalDate today = LocalDate.now(clock);
		List<EmployeeDto> list = new ArrayList<>();
		for (Long id : ids) {
			list.add(getEmployee(id, today));
		}
		return list;
	}

	@Transactional(readOnly = true)
	public EmployeeDto getEmployee(long id) {
		return getEmployee(id, LocalDate.now(clock));
	}

	private EmployeeDto getEmployee(long id, LocalDate onDate) {
		List<EmployeeDto> rows = jdbc.query("""
				SELECT id, employee_code, full_name, phone, address, hire_date, termination_date, is_active, notes
				FROM employees WHERE id = ?
				""", (rs, i) -> new EmployeeDto(
				rs.getLong("id"),
				rs.getString("employee_code"),
				rs.getString("full_name"),
				rs.getString("phone"),
				rs.getString("address"),
				rs.getDate("hire_date") == null ? null : rs.getDate("hire_date").toLocalDate(),
				rs.getDate("termination_date") == null ? null : rs.getDate("termination_date").toLocalDate(),
				rs.getBoolean("is_active"),
				rs.getString("notes"),
				effectivePayRate(id, onDate)), id);
		if (rows.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy nhân viên", null);
		}
		return rows.get(0);
	}

	@Transactional
	public EmployeeDto createEmployee(EmployeeRequest request) {
		shops.requireManufacturing();
		NameValidation.requireNoLeadingDigit("Họ và tên", request.fullName());
		validateEmployeeDates(request.hireDate(), request.terminationDate());
		String phone = requirePhone(request.phone());
		String code = nextEmployeeCode();
		long id;
		try {
			id = insert("""
					INSERT INTO employees (employee_code, full_name, phone, address, hire_date,
					                       termination_date, is_active, notes, shop_id)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
					""",
					code,
					request.fullName().trim(),
					phone,
					blank(request.address()),
					request.hireDate() == null ? null : Date.valueOf(request.hireDate()),
					request.terminationDate() == null ? null : Date.valueOf(request.terminationDate()),
					request.active() == null || request.active(),
					blank(request.notes()),
					shops.idOf(ShopContext.MANUFACTURING));
		} catch (DuplicateKeyException ex) {
			throw new BusinessException(ErrorCode.DUPLICATE_PRODUCT_CODE, "Mã nhân viên đã tồn tại", null);
		}
		if (request.initialDailyRate() != null) {
			LocalDate start = request.hireDate() != null ? request.hireDate() : LocalDate.of(2026, 1, 1);
			addPayRate(id, new PayRateRequest(
					start,
					null,
					request.initialDailyRate(),
					BigDecimal.valueOf(8),
					orZero(request.initialOvertimeWeekdayRate()),
					orZero(request.initialOvertimeRestdayRate()),
					orZero(request.initialOvertimeHolidayRate())));
		}
		audit.record(CurrentAdmin.get().id(), "CREATE_EMPLOYEE", "employees", String.valueOf(id), code);
		return getEmployee(id);
	}

	@Transactional
	public EmployeeDto updateEmployee(long id, EmployeeRequest request) {
		NameValidation.requireNoLeadingDigit("Họ và tên", request.fullName());
		EmployeeDto existing = getEmployee(id);
		validateEmployeeDates(request.hireDate(), request.terminationDate());
		String phone = requirePhone(request.phone());
		boolean active = request.active() == null ? existing.active() : request.active();
		jdbc.update("""
				UPDATE employees
				SET full_name = ?, phone = ?, address = ?, hire_date = ?,
				    termination_date = ?, is_active = ?, notes = ?
				WHERE id = ?
				""",
				request.fullName().trim(),
				phone,
				blank(request.address()),
				request.hireDate() == null ? null : Date.valueOf(request.hireDate()),
				request.terminationDate() == null ? null : Date.valueOf(request.terminationDate()),
				active,
				blank(request.notes()),
				id);
		String action = existing.active() != active
				? (active ? "ENABLE_EMPLOYEE" : "DISABLE_EMPLOYEE")
				: "UPDATE_EMPLOYEE";
		audit.record(CurrentAdmin.get().id(), action, "employees", String.valueOf(id), existing.employeeCode());
		return getEmployee(id);
	}

	/** Mã nội bộ tự tăng: NV001, NV002, ... (không nhập trên UI). */
	private String nextEmployeeCode() {
		Integer maxNum = jdbc.queryForObject("""
				SELECT COALESCE(MAX(CAST(SUBSTRING(employee_code, 3) AS UNSIGNED)), 0)
				FROM employees
				WHERE employee_code REGEXP '^NV[0-9]+$'
				""", Integer.class);
		int next = (maxNum == null ? 0 : maxNum) + 1;
		return "NV%03d".formatted(next);
	}

	private static String requirePhone(String phone) {
		if (phone == null || phone.isBlank()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Nhập số điện thoại", null);
		}
		return phone.trim();
	}

	@Transactional
	public EmployeeDto updateEmployeeStatus(long id, boolean active) {
		EmployeeDto existing = getEmployee(id);
		jdbc.update("UPDATE employees SET is_active = ? WHERE id = ?", active, id);
		audit.record(CurrentAdmin.get().id(), active ? "ENABLE_EMPLOYEE" : "DISABLE_EMPLOYEE",
				"employees", String.valueOf(id), existing.employeeCode());
		return getEmployee(id);
	}

	private static void validateEmployeeDates(LocalDate hireDate, LocalDate terminationDate) {
		if (hireDate != null && terminationDate != null && terminationDate.isBefore(hireDate)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngày nghỉ việc không được trước ngày vào làm", null);
		}
	}

	// ---------- Pay rates ----------

	@Transactional(readOnly = true)
	public List<PayRateDto> listPayRates(long employeeId) {
		getEmployee(employeeId);
		return jdbc.query("""
				SELECT id, employee_id, effective_from, effective_to, daily_rate, standard_hours_per_day,
				       overtime_weekday_rate, overtime_restday_rate, overtime_holiday_rate
				FROM employee_pay_rates
				WHERE employee_id = ?
				ORDER BY effective_from DESC, id DESC
				""", (rs, i) -> mapPayRate(rs), employeeId);
	}

	@Transactional
	public PayRateDto addPayRate(long employeeId, PayRateRequest request) {
		getEmployee(employeeId);
		if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngày kết thúc áp dụng phải sau hoặc bằng ngày bắt đầu", null);
		}
		jdbc.update("""
				UPDATE employee_pay_rates
				SET effective_to = ?
				WHERE employee_id = ? AND effective_to IS NULL AND effective_from < ?
				""",
				Date.valueOf(request.effectiveFrom().minusDays(1)),
				employeeId,
				Date.valueOf(request.effectiveFrom()));
		long id = insert("""
				INSERT INTO employee_pay_rates (employee_id, effective_from, effective_to, daily_rate,
				    standard_hours_per_day, overtime_weekday_rate, overtime_restday_rate, overtime_holiday_rate)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)
				""",
				employeeId,
				Date.valueOf(request.effectiveFrom()),
				request.effectiveTo() == null ? null : Date.valueOf(request.effectiveTo()),
				scaleMoney(request.dailyRate()),
				request.standardHoursPerDay().setScale(2, RoundingMode.HALF_UP),
				scaleMoney(orZero(request.overtimeWeekdayRate())),
				scaleMoney(orZero(request.overtimeRestdayRate())),
				scaleMoney(orZero(request.overtimeHolidayRate())));
		audit.record(CurrentAdmin.get().id(), "ADD_PAY_RATE", "employee_pay_rates", String.valueOf(id),
				"Thiết lập mức công cho nhân viên #" + employeeId + " từ " + request.effectiveFrom());
		return jdbc.query("SELECT * FROM employee_pay_rates WHERE id = ?", (rs, i) -> mapPayRate(rs), id).get(0);
	}

	private PayRateDto effectivePayRate(long employeeId, LocalDate onDate) {
		List<PayRateDto> matched = jdbc.query("""
				SELECT * FROM employee_pay_rates
				WHERE employee_id = ?
				  AND effective_from <= ?
				  AND (effective_to IS NULL OR effective_to >= ?)
				ORDER BY effective_from DESC, id DESC
				LIMIT 1
				""", (rs, i) -> mapPayRate(rs), employeeId, Date.valueOf(onDate), Date.valueOf(onDate));
		if (!matched.isEmpty()) {
			return matched.get(0);
		}
		List<PayRateDto> latest = jdbc.query("""
				SELECT * FROM employee_pay_rates
				WHERE employee_id = ?
				ORDER BY effective_from DESC, id DESC
				LIMIT 1
				""", (rs, i) -> mapPayRate(rs), employeeId);
		return latest.isEmpty() ? null : latest.get(0);
	}

	private static PayRateDto mapPayRate(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new PayRateDto(
				rs.getLong("id"),
				rs.getLong("employee_id"),
				rs.getDate("effective_from").toLocalDate(),
				rs.getDate("effective_to") == null ? null : rs.getDate("effective_to").toLocalDate(),
				rs.getBigDecimal("daily_rate"),
				rs.getBigDecimal("standard_hours_per_day"),
				rs.getBigDecimal("overtime_weekday_rate"),
				rs.getBigDecimal("overtime_restday_rate"),
				rs.getBigDecimal("overtime_holiday_rate"));
	}

	// ---------- Holidays & Attendance Policy ----------

	@Transactional(readOnly = true)
	public List<HolidayDto> listHolidays(Integer year) {
		if (year == null) {
			return jdbc.query("SELECT * FROM holidays ORDER BY holiday_date DESC", (rs, i) -> mapHoliday(rs));
		}
		LocalDate start = LocalDate.of(year, 1, 1);
		LocalDate end = LocalDate.of(year, 12, 31);
		return jdbc.query("""
				SELECT * FROM holidays WHERE holiday_date BETWEEN ? AND ? ORDER BY holiday_date ASC
				""", (rs, i) -> mapHoliday(rs), Date.valueOf(start), Date.valueOf(end));
	}

	@Transactional
	public HolidayDto saveHoliday(Long id, HolidayRequest request) {
		NameValidation.requireNoLeadingDigit("Tên ngày lễ", request.name());
		if (!HOLIDAY_TYPES.contains(request.holidayType())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Loại ngày nghỉ lễ không hợp lệ", null);
		}
		boolean paid = request.paid() == null || request.paid();
		try {
			if (id == null) {
				long newId = insert("""
						INSERT INTO holidays (holiday_date, name, holiday_type, is_paid, notes)
						VALUES (?, ?, ?, ?, ?)
						""",
						Date.valueOf(request.holidayDate()),
						request.name().trim(),
						request.holidayType(),
						paid,
						blank(request.notes()));
				audit.record(CurrentAdmin.get().id(), "CREATE_HOLIDAY", "holidays", String.valueOf(newId), request.name());
				return jdbc.query("SELECT * FROM holidays WHERE id = ?", (rs, i) -> mapHoliday(rs), newId).get(0);
			}
			jdbc.update("""
					UPDATE holidays SET holiday_date = ?, name = ?, holiday_type = ?, is_paid = ?, notes = ? WHERE id = ?
					""",
					Date.valueOf(request.holidayDate()),
					request.name().trim(),
					request.holidayType(),
					paid,
					blank(request.notes()),
					id);
			audit.record(CurrentAdmin.get().id(), "UPDATE_HOLIDAY", "holidays", String.valueOf(id), request.name());
			return jdbc.query("SELECT * FROM holidays WHERE id = ?", (rs, i) -> mapHoliday(rs), id).get(0);
		} catch (DuplicateKeyException ex) {
			throw new BusinessException(ErrorCode.DUPLICATE_PRODUCT_CODE, "Ngày lễ này đã tồn tại", null);
		}
	}

	private static HolidayDto mapHoliday(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new HolidayDto(
				rs.getLong("id"),
				rs.getDate("holiday_date").toLocalDate(),
				rs.getString("name"),
				rs.getString("holiday_type"),
				rs.getBoolean("is_paid"),
				rs.getString("notes"));
	}

	@Transactional(readOnly = true)
	public AttendancePolicyDto currentPolicy(LocalDate onDate) {
		LocalDate target = onDate == null ? LocalDate.now(clock) : onDate;
		List<AttendancePolicyDto> matched = jdbc.query("""
				SELECT * FROM attendance_policies
				WHERE effective_from <= ? AND (effective_to IS NULL OR effective_to >= ?)
				ORDER BY effective_from DESC, id DESC
				LIMIT 1
				""", (rs, i) -> mapPolicy(rs), Date.valueOf(target), Date.valueOf(target));
		if (!matched.isEmpty()) {
			return matched.get(0);
		}
		List<AttendancePolicyDto> fallback = jdbc.query("""
				SELECT * FROM attendance_policies ORDER BY effective_from DESC, id DESC LIMIT 1
				""", (rs, i) -> mapPolicy(rs));
		if (fallback.isEmpty()) {
			throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Chưa có cấu hình chấm công", null);
		}
		return fallback.get(0);
	}

	@Transactional
	public AttendancePolicyDto savePolicy(AttendancePolicyRequest request) {
		jdbc.update("""
				UPDATE attendance_policies
				SET effective_to = ?
				WHERE effective_to IS NULL AND effective_from < ?
				""", Date.valueOf(request.effectiveFrom().minusDays(1)), Date.valueOf(request.effectiveFrom()));
		long id = insert("""
				INSERT INTO attendance_policies (
				    effective_from, standard_start_time, standard_end_time, break_minutes,
				    late_grace_minutes, early_leave_grace_minutes, rounding_minutes,
				    half_day_threshold_hours, full_day_credited_days, half_day_credited_days,
				    overnight_shift_allowed, notes)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				Date.valueOf(request.effectiveFrom()),
				Time.valueOf(request.standardStartTime()),
				Time.valueOf(request.standardEndTime()),
				request.breakMinutes(),
				request.lateGraceMinutes(),
				request.earlyLeaveGraceMinutes(),
				request.roundingMinutes(),
				request.halfDayThresholdHours().setScale(2, RoundingMode.HALF_UP),
				request.fullDayCreditedDays().setScale(2, RoundingMode.HALF_UP),
				request.halfDayCreditedDays().setScale(2, RoundingMode.HALF_UP),
				request.overnightShiftAllowed(),
				blank(request.notes()));
		audit.record(CurrentAdmin.get().id(), "UPDATE_ATTENDANCE_POLICY", "attendance_policies",
				String.valueOf(id), "Cập nhật quy tắc chấm công từ " + request.effectiveFrom());
		return jdbc.query("SELECT * FROM attendance_policies WHERE id = ?", (rs, i) -> mapPolicy(rs), id).get(0);
	}

	private static AttendancePolicyDto mapPolicy(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new AttendancePolicyDto(
				rs.getLong("id"),
				rs.getDate("effective_from").toLocalDate(),
				rs.getDate("effective_to") == null ? null : rs.getDate("effective_to").toLocalDate(),
				rs.getTime("standard_start_time").toLocalTime(),
				rs.getTime("standard_end_time").toLocalTime(),
				rs.getInt("break_minutes"),
				rs.getInt("late_grace_minutes"),
				rs.getInt("early_leave_grace_minutes"),
				rs.getInt("rounding_minutes"),
				rs.getBigDecimal("half_day_threshold_hours"),
				rs.getBigDecimal("full_day_credited_days"),
				rs.getBigDecimal("half_day_credited_days"),
				rs.getBoolean("overnight_shift_allowed"),
				rs.getString("notes"));
	}

	// ---------- Monthly attendance & Overtime ----------

	@Transactional(readOnly = true)
	public MonthlyAttendanceBoardDto monthlyBoard(int year, int month) {
		shops.requireManufacturing();
		validateYearMonth(year, month);
		int daysInMonth = AttendanceCalculator.daysInMonth(year, month);
		LocalDate start = LocalDate.of(year, month, 1);
		LocalDate end = LocalDate.of(year, month, daysInMonth);
		boolean closed = isPeriodClosed(year, month);
		AttendancePolicyDto policy = currentPolicy(end);
		List<HolidayDto> holidays = jdbc.query("""
				SELECT * FROM holidays WHERE holiday_date BETWEEN ? AND ? ORDER BY holiday_date ASC
				""", (rs, i) -> mapHoliday(rs), Date.valueOf(start), Date.valueOf(end));

		List<Long> employeeIds = jdbc.query("""
				SELECT DISTINCT e.id, e.employee_code
				FROM employees e
				LEFT JOIN attendance_records a
				  ON a.employee_id = e.id AND a.work_date BETWEEN ? AND ?
				WHERE e.is_active = 1 OR a.id IS NOT NULL
				ORDER BY e.employee_code ASC
				""", (rs, i) -> rs.getLong("id"), Date.valueOf(start), Date.valueOf(end));

		List<AttendanceDayDto> records = jdbc.query("""
				SELECT * FROM attendance_records
				WHERE work_date BETWEEN ? AND ?
				ORDER BY work_date ASC
				""", (rs, i) -> mapAttendanceDay(rs, List.of()), Date.valueOf(start), Date.valueOf(end));

		Map<Long, List<OvertimeDto>> overtimeByAttendance = new HashMap<>();
		if (!records.isEmpty()) {
			jdbc.query("""
					SELECT o.*
					FROM overtime_records o
					JOIN attendance_records a ON a.id = o.attendance_id
					WHERE a.work_date BETWEEN ? AND ?
					ORDER BY o.id ASC
					""", rs -> {
				OvertimeDto dto = mapOvertime(rs);
				overtimeByAttendance.computeIfAbsent(dto.attendanceId(), k -> new ArrayList<>()).add(dto);
			}, Date.valueOf(start), Date.valueOf(end));
		}

		Map<String, AttendanceDayDto> byEmpDate = new HashMap<>();
		for (AttendanceDayDto r : records) {
			List<OvertimeDto> ots = overtimeByAttendance.getOrDefault(r.id(), List.of());
			byEmpDate.put(r.employeeId() + ":" + r.workDate(), new AttendanceDayDto(
					r.id(), r.employeeId(), r.workDate(), r.status(), r.clockIn(), r.clockOut(),
					r.workedHours(), r.creditedDays(), r.lateMinutes(), r.earlyLeaveMinutes(),
					r.notes(), r.dailyRateSnapshot(), r.locked(), ots));
		}

		List<EmployeeMonthlyRowDto> rows = new ArrayList<>();
		for (Long empId : employeeIds) {
			EmployeeDto emp = getEmployee(empId, end);
			BigDecimal currentDaily = emp.currentPayRate() != null
					? emp.currentPayRate().dailyRate()
					: BigDecimal.ZERO;
			List<AttendanceDayDto> dayList = new ArrayList<>(daysInMonth);
			BigDecimal totalCredited = BigDecimal.ZERO;
			BigDecimal totalHours = BigDecimal.ZERO;
			int totalLate = 0;
			int totalEarly = 0;
			BigDecimal approvedOtHours = BigDecimal.ZERO;
			List<OvertimeLine> otLines = new ArrayList<>();

			for (int d = 1; d <= daysInMonth; d++) {
				LocalDate date = LocalDate.of(year, month, d);
				AttendanceDayDto cell = byEmpDate.get(empId + ":" + date);
				if (cell == null) {
					cell = new AttendanceDayDto(
							null, empId, date, "UNMARKED", null, null,
							BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, null, currentDaily, closed, List.of());
				}
				dayList.add(cell);
				totalCredited = totalCredited.add(cell.creditedDays());
				totalHours = totalHours.add(cell.workedHours());
				totalLate += cell.lateMinutes();
				totalEarly += cell.earlyLeaveMinutes();
				for (OvertimeDto ot : cell.overtimes()) {
					otLines.add(new OvertimeLine(ot.approved(), ot.amount()));
					if (ot.approved()) {
						approvedOtHours = approvedOtHours.add(ot.overtimeHours());
					}
				}
			}

			rows.add(new EmployeeMonthlyRowDto(
					emp.id(),
					emp.employeeCode(),
					emp.fullName(),
					emp.active(),
					currentDaily,
					totalCredited.setScale(2, RoundingMode.HALF_UP),
					totalHours.setScale(3, RoundingMode.HALF_UP),
					totalLate,
					totalEarly,
					approvedOtHours.setScale(3, RoundingMode.HALF_UP),
					AttendanceCalculator.approvedOvertimePay(otLines),
					dayList));
		}

		return new MonthlyAttendanceBoardDto(year, month, daysInMonth, closed, policy, holidays, rows);
	}

	@Transactional
	public AttendanceDayDto upsertAttendance(long employeeId, LocalDate date, AttendanceUpsertRequest request) {
		requireNotLocked(date);
		getEmployee(employeeId, date);
		String status = request.status().trim();
		if (!VALID_STATUSES.contains(status)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trạng thái chấm công không hợp lệ", null);
		}
		AttendancePolicyDto policy = currentPolicy(date);
		BigDecimal workedHours = BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP);
		int lateMinutes = 0;
		int earlyLeaveMinutes = 0;
		LocalDateTime clockIn = request.clockIn();
		LocalDateTime clockOut = request.clockOut();

		if (clockIn != null || clockOut != null) {
			LocalDateTime resolvedOut = AttendanceCalculator.resolveClockOut(
					clockIn, clockOut, policy.overnightShiftAllowed());
			clockOut = resolvedOut;
			workedHours = AttendanceCalculator.workedHours(
					clockIn, resolvedOut, policy.overnightShiftAllowed(),
					policy.breakMinutes(), policy.roundingMinutes());
			LocalDateTime scheduledStart = LocalDateTime.of(date, policy.standardStartTime());
			LocalDateTime scheduledEnd = LocalDateTime.of(date, policy.standardEndTime());
			if (!scheduledEnd.isAfter(scheduledStart) && policy.overnightShiftAllowed()) {
				scheduledEnd = scheduledEnd.plusDays(1);
			}
			lateMinutes = AttendanceCalculator.lateMinutes(scheduledStart, clockIn, policy.lateGraceMinutes());
			earlyLeaveMinutes = AttendanceCalculator.earlyLeaveMinutes(
					scheduledEnd, resolvedOut, policy.earlyLeaveGraceMinutes());
		}

		BigDecimal creditedDays = AttendanceCalculator.creditedDays(
				status, policy.fullDayCreditedDays(), policy.halfDayCreditedDays());
		if ("HOLIDAY".equals(status)) {
			List<Boolean> holidayPaid = jdbc.queryForList(
					"SELECT is_paid FROM holidays WHERE holiday_date = ? LIMIT 1",
					Boolean.class, Date.valueOf(date));
			if (!holidayPaid.isEmpty() && !Boolean.TRUE.equals(holidayPaid.get(0))) {
				creditedDays = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
			}
		}

		PayRateDto rate = effectivePayRate(employeeId, date);
		Long payRateId = rate != null ? rate.id() : null;
		BigDecimal dailySnapshot = rate != null ? rate.dailyRate() : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

		List<Long> existing = jdbc.queryForList(
				"SELECT id FROM attendance_records WHERE employee_id = ? AND work_date = ?",
				Long.class, employeeId, Date.valueOf(date));
		long recordId;
		if (existing.isEmpty()) {
			recordId = insert("""
					INSERT INTO attendance_records (
					    employee_id, work_date, attendance_status, clock_in, clock_out,
					    worked_hours, credited_days, late_minutes, early_leave_minutes,
					    notes, pay_rate_id, daily_rate_snapshot, is_locked)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
					""",
					employeeId,
					Date.valueOf(date),
					status,
					clockIn == null ? null : Timestamp.valueOf(clockIn),
					clockOut == null ? null : Timestamp.valueOf(clockOut),
					workedHours,
					creditedDays,
					lateMinutes,
					earlyLeaveMinutes,
					blank(request.notes()),
					payRateId,
					dailySnapshot);
		} else {
			recordId = existing.get(0);
			Boolean locked = jdbc.queryForObject(
					"SELECT is_locked FROM attendance_records WHERE id = ?", Boolean.class, recordId);
			if (Boolean.TRUE.equals(locked)) {
				throw new BusinessException(ErrorCode.PAYROLL_PERIOD_LOCKED,
						"Ngày công này đã khóa theo kỳ công, không thể sửa", null);
			}
			jdbc.update("""
					UPDATE attendance_records
					SET attendance_status = ?, clock_in = ?, clock_out = ?, worked_hours = ?,
					    credited_days = ?, late_minutes = ?, early_leave_minutes = ?, notes = ?,
					    pay_rate_id = ?, daily_rate_snapshot = ?
					WHERE id = ?
					""",
					status,
					clockIn == null ? null : Timestamp.valueOf(clockIn),
					clockOut == null ? null : Timestamp.valueOf(clockOut),
					workedHours,
					creditedDays,
					lateMinutes,
					earlyLeaveMinutes,
					blank(request.notes()),
					payRateId,
					dailySnapshot,
					recordId);
		}
		audit.record(CurrentAdmin.get().id(), "UPSERT_ATTENDANCE", "attendance_records", String.valueOf(recordId),
				"Chấm công NV #" + employeeId + " ngày " + date + ": " + status);
		return getAttendanceById(recordId);
	}

	@Transactional
	public List<AttendanceDayDto> bulkUpsert(BulkAttendanceRequest request) {
		List<AttendanceDayDto> result = new ArrayList<>();
		for (BulkAttendanceItem item : request.items()) {
			result.add(upsertAttendance(item.employeeId(), item.workDate(), new AttendanceUpsertRequest(
					item.status(), item.clockIn(), item.clockOut(), item.notes())));
		}
		return result;
	}

	@Transactional
	public AttendanceDayDto addOvertime(long attendanceId, OvertimeRequest request) {
		AttendanceDayDto day = getAttendanceById(attendanceId);
		requireNotLocked(day.workDate());
		if (day.locked()) {
			throw new BusinessException(ErrorCode.PAYROLL_PERIOD_LOCKED, "Ngày công đã khóa", null);
		}
		String type = request.overtimeType().trim();
		if (!OVERTIME_TYPES.contains(type)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Loại tăng ca không hợp lệ", null);
		}
		BigDecimal hourlyRate = resolveOvertimeHourlyRate(day.employeeId(), day.workDate(), type);
		BigDecimal hours = request.overtimeHours().setScale(3, RoundingMode.HALF_UP);
		BigDecimal amount = hours.multiply(hourlyRate).setScale(4, RoundingMode.HALF_UP);
		boolean approved = Boolean.TRUE.equals(request.approved());
		long otId = insert("""
				INSERT INTO overtime_records (
				    attendance_id, overtime_type, start_at, end_at, overtime_hours,
				    hourly_rate_snapshot, amount, is_approved, notes)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""",
				attendanceId,
				type,
				request.startAt() == null ? null : Timestamp.valueOf(request.startAt()),
				request.endAt() == null ? null : Timestamp.valueOf(request.endAt()),
				hours,
				hourlyRate,
				amount,
				approved,
				blank(request.notes()));
		audit.record(CurrentAdmin.get().id(), "ADD_OVERTIME", "overtime_records", String.valueOf(otId),
				"Thêm tăng ca " + hours + "h (" + (approved ? "Đã duyệt" : "Chưa duyệt") + ")");
		return getAttendanceById(attendanceId);
	}

	@Transactional
	public AttendanceDayDto updateOvertime(long overtimeId, OvertimeRequest request) {
		List<Long> attIds = jdbc.queryForList(
				"SELECT attendance_id FROM overtime_records WHERE id = ?", Long.class, overtimeId);
		if (attIds.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy bản ghi tăng ca", null);
		}
		long attendanceId = attIds.get(0);
		AttendanceDayDto day = getAttendanceById(attendanceId);
		requireNotLocked(day.workDate());
		if (day.locked()) {
			throw new BusinessException(ErrorCode.PAYROLL_PERIOD_LOCKED, "Ngày công đã khóa", null);
		}
		String type = request.overtimeType().trim();
		if (!OVERTIME_TYPES.contains(type)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Loại tăng ca không hợp lệ", null);
		}
		BigDecimal hourlyRate = resolveOvertimeHourlyRate(day.employeeId(), day.workDate(), type);
		BigDecimal hours = request.overtimeHours().setScale(3, RoundingMode.HALF_UP);
		BigDecimal amount = hours.multiply(hourlyRate).setScale(4, RoundingMode.HALF_UP);
		boolean approved = Boolean.TRUE.equals(request.approved());
		jdbc.update("""
				UPDATE overtime_records
				SET overtime_type = ?, start_at = ?, end_at = ?, overtime_hours = ?,
				    hourly_rate_snapshot = ?, amount = ?, is_approved = ?, notes = ?
				WHERE id = ?
				""",
				type,
				request.startAt() == null ? null : Timestamp.valueOf(request.startAt()),
				request.endAt() == null ? null : Timestamp.valueOf(request.endAt()),
				hours,
				hourlyRate,
				amount,
				approved,
				blank(request.notes()),
				overtimeId);
		audit.record(CurrentAdmin.get().id(), "UPDATE_OVERTIME", "overtime_records", String.valueOf(overtimeId),
				"Cập nhật tăng ca " + hours + "h (" + (approved ? "Đã duyệt" : "Chưa duyệt") + ")");
		return getAttendanceById(attendanceId);
	}

	private BigDecimal resolveOvertimeHourlyRate(long employeeId, LocalDate date, String type) {
		PayRateDto rate = effectivePayRate(employeeId, date);
		if (rate == null) {
			return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
		}
		BigDecimal configured = switch (type) {
			case "WEEKDAY" -> rate.overtimeWeekdayRate();
			case "RESTDAY" -> rate.overtimeRestdayRate();
			case "HOLIDAY" -> rate.overtimeHolidayRate();
			default -> BigDecimal.ZERO;
		};
		if (configured != null && configured.signum() > 0) {
			return configured.setScale(4, RoundingMode.HALF_UP);
		}
		if (rate.dailyRate().signum() > 0 && rate.standardHoursPerDay().signum() > 0) {
			return rate.dailyRate().divide(rate.standardHoursPerDay(), 4, RoundingMode.HALF_UP);
		}
		return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
	}

	private AttendanceDayDto getAttendanceById(long id) {
		List<OvertimeDto> overtimes = jdbc.query(
				"SELECT * FROM overtime_records WHERE attendance_id = ? ORDER BY id ASC",
				(rs, i) -> mapOvertime(rs), id);
		List<AttendanceDayDto> rows = jdbc.query(
				"SELECT * FROM attendance_records WHERE id = ?",
				(rs, i) -> mapAttendanceDay(rs, overtimes), id);
		if (rows.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy ngày công", null);
		}
		return rows.get(0);
	}

	private static AttendanceDayDto mapAttendanceDay(java.sql.ResultSet rs, List<OvertimeDto> overtimes)
			throws java.sql.SQLException {
		return new AttendanceDayDto(
				rs.getLong("id"),
				rs.getLong("employee_id"),
				rs.getDate("work_date").toLocalDate(),
				rs.getString("attendance_status"),
				rs.getTimestamp("clock_in") == null ? null : rs.getTimestamp("clock_in").toLocalDateTime(),
				rs.getTimestamp("clock_out") == null ? null : rs.getTimestamp("clock_out").toLocalDateTime(),
				rs.getBigDecimal("worked_hours"),
				rs.getBigDecimal("credited_days"),
				rs.getInt("late_minutes"),
				rs.getInt("early_leave_minutes"),
				rs.getString("notes"),
				rs.getBigDecimal("daily_rate_snapshot"),
				rs.getBoolean("is_locked"),
				overtimes);
	}

	private static OvertimeDto mapOvertime(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new OvertimeDto(
				rs.getLong("id"),
				rs.getLong("attendance_id"),
				rs.getString("overtime_type"),
				rs.getTimestamp("start_at") == null ? null : rs.getTimestamp("start_at").toLocalDateTime(),
				rs.getTimestamp("end_at") == null ? null : rs.getTimestamp("end_at").toLocalDateTime(),
				rs.getBigDecimal("overtime_hours"),
				rs.getBigDecimal("hourly_rate_snapshot"),
				rs.getBigDecimal("amount"),
				rs.getBoolean("is_approved"),
				rs.getString("notes"));
	}

	// ---------- Payroll ----------

	@Transactional(readOnly = true)
	public List<PayrollPeriodDto> listPayrollPeriods() {
		List<Long> ids = jdbc.queryForList(
				"SELECT id FROM payroll_periods ORDER BY period_year DESC, period_month DESC", Long.class);
		List<PayrollPeriodDto> result = new ArrayList<>();
		for (Long id : ids) {
			result.add(getPayrollPeriod(id));
		}
		return result;
	}

	@Transactional
	public PayrollPeriodDto createOrGetPeriod(PayrollPeriodRequest request) {
		validateYearMonth(request.year(), request.month());
		List<Long> existing = jdbc.queryForList(
				"SELECT id FROM payroll_periods WHERE period_year = ? AND period_month = ?",
				Long.class, request.year(), request.month());
		if (!existing.isEmpty()) {
			long id = existing.get(0);
			PayrollPeriodDto period = getPayrollPeriod(id);
			if ("OPEN".equals(period.status()) && period.details().isEmpty()) {
				return calculatePayroll(id);
			}
			return period;
		}
		long id = insert("""
				INSERT INTO payroll_periods (period_year, period_month, status, notes)
				VALUES (?, ?, 'OPEN', ?)
				""", request.year(), request.month(), blank(request.notes()));
		audit.record(CurrentAdmin.get().id(), "CREATE_PAYROLL_PERIOD", "payroll_periods", String.valueOf(id),
				"Khởi tạo kỳ công " + request.month() + "/" + request.year());
		return calculatePayroll(id);
	}

	@Transactional(readOnly = true)
	public PayrollPeriodDto getPayrollPeriod(long id) {
		List<PayrollPeriodDto> rows = jdbc.query("""
				SELECT * FROM payroll_periods WHERE id = ?
				""", (rs, i) -> {
			List<PayrollDetailDto> details = payrollDetails(id);
			BigDecimal base = sum(details, PayrollDetailDto::basePay);
			BigDecimal ot = sum(details, PayrollDetailDto::overtimePay);
			BigDecimal allow = sum(details, PayrollDetailDto::allowances);
			BigDecimal ded = sum(details, PayrollDetailDto::deductions);
			BigDecimal total = sum(details, PayrollDetailDto::totalPay);
			BigDecimal paid = scaleMoney(jdbc.queryForObject("""
					SELECT COALESCE(SUM(amount), 0)
					FROM financial_transactions
					WHERE reference_type = 'PAYROLL_PERIOD' AND reference_id = ?
					  AND category = 'PAYROLL' AND status = 'CONFIRMED'
					""", BigDecimal.class, id));
			return new PayrollPeriodDto(
					rs.getLong("id"),
					rs.getInt("period_year"),
					rs.getInt("period_month"),
					rs.getString("status"),
					rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toLocalDateTime(),
					rs.getTimestamp("reopened_at") == null ? null : rs.getTimestamp("reopened_at").toLocalDateTime(),
					rs.getString("reopen_reason"),
					rs.getString("notes"),
					base, ot, allow, ded, total, paid, details);
		}, id);
		if (rows.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy kỳ lương", null);
		}
		return rows.get(0);
	}

	@Transactional
	public PayrollPeriodDto calculatePayroll(long periodId) {
		PeriodHeader header = lockPeriod(periodId);
		if (!"OPEN".equals(header.status())) {
			throw new BusinessException(ErrorCode.PAYROLL_PERIOD_LOCKED,
					"Kỳ công đã chốt, không tự tính lại. Hãy mở lại kỳ công nếu cần tính lại", null);
		}
		LocalDate start = LocalDate.of(header.year(), header.month(), 1);
		LocalDate end = YearMonth.of(header.year(), header.month()).atEndOfMonth();

		Map<Long, BigDecimal[]> existingAdjustments = new HashMap<>();
		jdbc.query("""
				SELECT employee_id, allowances, deductions FROM payroll_details WHERE payroll_period_id = ?
				""", rs -> {
			existingAdjustments.put(rs.getLong("employee_id"),
					new BigDecimal[]{rs.getBigDecimal("allowances"), rs.getBigDecimal("deductions")});
		}, periodId);

		List<Long> employeeIds = jdbc.query("""
				SELECT DISTINCT e.id, e.employee_code
				FROM employees e
				LEFT JOIN attendance_records a
				  ON a.employee_id = e.id AND a.work_date BETWEEN ? AND ?
				WHERE e.is_active = 1 OR a.id IS NOT NULL
				ORDER BY e.employee_code ASC
				""", (rs, i) -> rs.getLong("id"), Date.valueOf(start), Date.valueOf(end));

		for (Long empId : employeeIds) {
			EmployeeDto emp = getEmployee(empId, end);
			BigDecimal fallbackRate = emp.currentPayRate() != null ? emp.currentPayRate().dailyRate() : BigDecimal.ZERO;

			BigDecimal creditedDays = scaleQty3(jdbc.queryForObject("""
					SELECT COALESCE(SUM(credited_days), 0)
					FROM attendance_records
					WHERE employee_id = ? AND work_date BETWEEN ? AND ?
					""", BigDecimal.class, empId, Date.valueOf(start), Date.valueOf(end)));

			BigDecimal basePay = scaleMoney(jdbc.queryForObject("""
					SELECT COALESCE(SUM(credited_days * CASE WHEN daily_rate_snapshot > 0 THEN daily_rate_snapshot ELSE ? END), 0)
					FROM attendance_records
					WHERE employee_id = ? AND work_date BETWEEN ? AND ?
					""", BigDecimal.class, fallbackRate, empId, Date.valueOf(start), Date.valueOf(end)));

			List<OvertimeLine> otLines = jdbc.query("""
					SELECT o.is_approved, o.amount
					FROM overtime_records o
					JOIN attendance_records a ON a.id = o.attendance_id
					WHERE a.employee_id = ? AND a.work_date BETWEEN ? AND ?
					""", (rs, i) -> new OvertimeLine(rs.getBoolean("is_approved"), rs.getBigDecimal("amount")),
					empId, Date.valueOf(start), Date.valueOf(end));
			BigDecimal overtimePay = AttendanceCalculator.approvedOvertimePay(otLines);

			BigDecimal[] adj = existingAdjustments.getOrDefault(empId,
					new BigDecimal[]{BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
							BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)});
			BigDecimal allowances = scaleMoney(adj[0]);
			BigDecimal deductions = scaleMoney(adj[1]);
			BigDecimal totalPay = basePay.add(overtimePay).add(allowances).subtract(deductions)
					.setScale(4, RoundingMode.HALF_UP);
			if (totalPay.signum() < 0) {
				totalPay = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
				deductions = basePay.add(overtimePay).add(allowances).setScale(4, RoundingMode.HALF_UP);
			}

			String snapshotJson = """
					{"employeeCode":"%s","fullName":"%s","dailyRate":%s,"creditedDays":%s,"basePay":%s,"overtimePay":%s,"allowances":%s,"deductions":%s,"totalPay":%s}
					""".formatted(
					escapeJson(emp.employeeCode()),
					escapeJson(emp.fullName()),
					fallbackRate.toPlainString(),
					creditedDays.toPlainString(),
					basePay.toPlainString(),
					overtimePay.toPlainString(),
					allowances.toPlainString(),
					deductions.toPlainString(),
					totalPay.toPlainString()).trim();

			List<Long> detailIds = jdbc.queryForList(
					"SELECT id FROM payroll_details WHERE payroll_period_id = ? AND employee_id = ?",
					Long.class, periodId, empId);
			if (detailIds.isEmpty()) {
				jdbc.update("""
						INSERT INTO payroll_details (
						    payroll_period_id, employee_id, credited_days, base_pay, overtime_pay,
						    allowances, deductions, total_pay, calculation_snapshot)
						VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
						""",
						periodId, empId, creditedDays, basePay, overtimePay, allowances, deductions, totalPay, snapshotJson);
			} else {
				jdbc.update("""
						UPDATE payroll_details
						SET credited_days = ?, base_pay = ?, overtime_pay = ?, allowances = ?,
						    deductions = ?, total_pay = ?, calculation_snapshot = ?
						WHERE id = ?
						""",
						creditedDays, basePay, overtimePay, allowances, deductions, totalPay, snapshotJson, detailIds.get(0));
			}
		}
		audit.record(CurrentAdmin.get().id(), "CALCULATE_PAYROLL", "payroll_periods", String.valueOf(periodId),
				"Tính tiền công kỳ " + header.month() + "/" + header.year());
		return getPayrollPeriod(periodId);
	}

	@Transactional
	public PayrollPeriodDto adjustPayrollDetail(
			long periodId, long detailId, PayrollAdjustDetailRequest request) {
		PeriodHeader header = lockPeriod(periodId);
		if (!"OPEN".equals(header.status())) {
			throw new BusinessException(ErrorCode.PAYROLL_PERIOD_LOCKED,
					"Kỳ công đã chốt, không chỉnh sửa phụ cấp/khấu trừ", null);
		}
		List<Map<String, Object>> rows = jdbc.queryForList("""
				SELECT id, base_pay, overtime_pay FROM payroll_details WHERE id = ? AND payroll_period_id = ?
				""", detailId, periodId);
		if (rows.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy dòng lương nhân viên", null);
		}
		BigDecimal basePay = (BigDecimal) rows.get(0).get("base_pay");
		BigDecimal overtimePay = (BigDecimal) rows.get(0).get("overtime_pay");
		BigDecimal allowances = scaleMoney(request.allowances());
		BigDecimal deductions = scaleMoney(request.deductions());
		BigDecimal totalPay = basePay.add(overtimePay).add(allowances).subtract(deductions)
				.setScale(4, RoundingMode.HALF_UP);
		if (totalPay.signum() < 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Khấu trừ vượt quá tổng thu nhập của nhân viên", null);
		}
		jdbc.update("""
				UPDATE payroll_details SET allowances = ?, deductions = ?, total_pay = ? WHERE id = ?
				""", allowances, deductions, totalPay, detailId);
		audit.record(CurrentAdmin.get().id(), "ADJUST_PAYROLL_DETAIL", "payroll_details", String.valueOf(detailId),
				"Cập nhật phụ cấp/khấu trừ kỳ " + header.month() + "/" + header.year());
		return getPayrollPeriod(periodId);
	}

	@Transactional
	public PayrollPeriodDto closePeriod(long periodId) {
		PeriodHeader header = lockPeriod(periodId);
		if ("CLOSED".equals(header.status())) {
			throw new BusinessException(ErrorCode.PAYROLL_PERIOD_LOCKED, "Kỳ công này đã được chốt", null);
		}
		calculatePayroll(periodId);
		LocalDate start = LocalDate.of(header.year(), header.month(), 1);
		LocalDate end = YearMonth.of(header.year(), header.month()).atEndOfMonth();
		LocalDateTime now = LocalDateTime.now(clock);
		long adminId = CurrentAdmin.get().id();

		jdbc.update("""
				UPDATE attendance_records SET is_locked = 1 WHERE work_date BETWEEN ? AND ?
				""", Date.valueOf(start), Date.valueOf(end));
		jdbc.update("""
				UPDATE payroll_periods
				SET status = 'CLOSED', closed_at = ?, closed_by_admin_id = ?
				WHERE id = ?
				""", Timestamp.valueOf(now), adminId, periodId);
		audit.record(adminId, "CLOSE_PAYROLL_PERIOD", "payroll_periods", String.valueOf(periodId),
				"Chốt kỳ công tháng " + header.month() + "/" + header.year());
		return getPayrollPeriod(periodId);
	}

	@Transactional
	public PayrollPeriodDto reopenPeriod(long periodId, String reason) {
		PeriodHeader header = lockPeriod(periodId);
		if (!"CLOSED".equals(header.status())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ mở lại được kỳ công đang khóa", null);
		}
		LocalDate start = LocalDate.of(header.year(), header.month(), 1);
		LocalDate end = YearMonth.of(header.year(), header.month()).atEndOfMonth();
		LocalDateTime now = LocalDateTime.now(clock);
		long adminId = CurrentAdmin.get().id();

		jdbc.update("""
				UPDATE attendance_records SET is_locked = 0 WHERE work_date BETWEEN ? AND ?
				""", Date.valueOf(start), Date.valueOf(end));
		jdbc.update("""
				UPDATE payroll_periods
				SET status = 'OPEN', reopened_at = ?, reopened_by_admin_id = ?, reopen_reason = ?
				WHERE id = ?
				""", Timestamp.valueOf(now), adminId, reason.trim(), periodId);
		audit.record(adminId, "REOPEN_PAYROLL_PERIOD", "payroll_periods", String.valueOf(periodId),
				"Mở lại kỳ công " + header.month() + "/" + header.year() + ". Lý do: " + reason.trim());
		return getPayrollPeriod(periodId);
	}

	@Transactional
	public PayrollPeriodDto payPeriod(long periodId, PayPayrollRequest request) {
		shops.requireManufacturing();
		PayrollPeriodDto period = getPayrollPeriod(periodId);
		if (!"CLOSED".equals(period.status())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Chỉ chi trả tiền công sau khi đã chốt kỳ lương", null);
		}
		String key = blank(request.idempotencyKey());
		if (key != null) {
			Integer dup = jdbc.queryForObject(
					"SELECT COUNT(*) FROM financial_transactions WHERE idempotency_key = ?", Integer.class, key);
			if (dup != null && dup > 0) {
				return getPayrollPeriod(periodId);
			}
		}
		BigDecimal remaining = period.grandTotalPay().subtract(period.paidAmount());
		BigDecimal amount = InventoryMath.snapshotMoney(request.amount());
		if (amount.compareTo(remaining) > 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Số tiền chi vượt phần lương còn lại (" + remaining.stripTrailingZeros().toPlainString() + ")", null);
		}
		LocalDateTime now = LocalDateTime.now(clock);
		String code = sequences.nextCode("FINANCE_EXPENSE", now.toLocalDate(), ShopContext.MANUFACTURING);
		String desc = blank(request.description()) != null
				? request.description().trim()
				: "Chi trả tiền công tháng " + period.month() + "/" + period.year();
		long finId = insert("""
				INSERT INTO financial_transactions (
				    transaction_code, transaction_date, transaction_type, category,
				    amount, description, reference_type, reference_id, status, idempotency_key,
				    shop_id, is_internal)
				VALUES (?, ?, 'EXPENSE', 'PAYROLL', ?, ?, 'PAYROLL_PERIOD', ?, 'CONFIRMED', ?, ?, 0)
				""", code, Timestamp.valueOf(now), amount, desc, periodId, key, shops.idOf(ShopContext.MANUFACTURING));
		audit.record(CurrentAdmin.get().id(), "PAY_PAYROLL", "payroll_periods", String.valueOf(periodId),
				"Chi lương " + code + " (" + finId + "): " + amount.stripTrailingZeros().toPlainString());
		return getPayrollPeriod(periodId);
	}

	// ---------- Helpers ----------

	private List<PayrollDetailDto> payrollDetails(long periodId) {
		return jdbc.query("""
				SELECT d.id, d.employee_id, e.employee_code, e.full_name,
				       d.credited_days, d.base_pay, d.overtime_pay, d.allowances, d.deductions, d.total_pay
				FROM payroll_details d
				JOIN employees e ON e.id = d.employee_id
				WHERE d.payroll_period_id = ?
				ORDER BY e.employee_code ASC
				""", (rs, i) -> new PayrollDetailDto(
				rs.getLong("id"),
				rs.getLong("employee_id"),
				rs.getString("employee_code"),
				rs.getString("full_name"),
				rs.getBigDecimal("credited_days"),
				rs.getBigDecimal("base_pay"),
				rs.getBigDecimal("overtime_pay"),
				rs.getBigDecimal("allowances"),
				rs.getBigDecimal("deductions"),
				rs.getBigDecimal("total_pay")), periodId);
	}

	private PeriodHeader lockPeriod(long id) {
		List<PeriodHeader> rows = jdbc.query("""
				SELECT id, period_year, period_month, status FROM payroll_periods WHERE id = ? FOR UPDATE
				""", (rs, i) -> new PeriodHeader(
				rs.getLong("id"), rs.getInt("period_year"), rs.getInt("period_month"), rs.getString("status")), id);
		if (rows.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy kỳ lương", null);
		}
		return rows.get(0);
	}

	private boolean isPeriodClosed(int year, int month) {
		List<String> status = jdbc.queryForList(
				"SELECT status FROM payroll_periods WHERE period_year = ? AND period_month = ?",
				String.class, year, month);
		return !status.isEmpty() && "CLOSED".equals(status.get(0));
	}

	private void requireNotLocked(LocalDate date) {
		if (isPeriodClosed(date.getYear(), date.getMonthValue())) {
			throw new BusinessException(ErrorCode.PAYROLL_PERIOD_LOCKED,
					"Kỳ công tháng " + date.getMonthValue() + "/" + date.getYear() + " đã chốt, không thể chỉnh sửa", null);
		}
	}

	private static void validateYearMonth(int year, int month) {
		if (year < 2000 || year > 2100 || month < 1 || month > 12) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tháng hoặc năm không hợp lệ", null);
		}
	}

	private long insert(String sql, Object... args) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			for (int i = 0; i < args.length; i++) {
				ps.setObject(i + 1, args[i]);
			}
			return ps;
		}, keys);
		return keys.getKey().longValue();
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static BigDecimal orZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private static BigDecimal scaleMoney(BigDecimal value) {
		return (value == null ? BigDecimal.ZERO : value).setScale(4, RoundingMode.HALF_UP);
	}

	private static BigDecimal scaleQty3(BigDecimal value) {
		return (value == null ? BigDecimal.ZERO : value).setScale(3, RoundingMode.HALF_UP);
	}

	private static BigDecimal sum(
			List<PayrollDetailDto> list, java.util.function.Function<PayrollDetailDto, BigDecimal> getter) {
		return list.stream().map(getter).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(4, RoundingMode.HALF_UP);
	}

	private static String escapeJson(String s) {
		return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private record PeriodHeader(long id, int year, int month, String status) {
	}
}
