package com.aluminum.manager.attendance;

import com.aluminum.manager.attendance.AttendanceCalculator.OvertimeLine;
import com.aluminum.manager.attendance.AttendanceDtos.AttendanceDayDto;
import com.aluminum.manager.attendance.AttendanceDtos.AttendanceBonusRequest;
import com.aluminum.manager.attendance.AttendanceDtos.AttendancePolicyDto;
import com.aluminum.manager.attendance.AttendanceDtos.AttendancePolicyRequest;
import com.aluminum.manager.attendance.AttendanceDtos.AttendanceUpsertRequest;
import com.aluminum.manager.attendance.AttendanceDtos.BulkAttendanceItem;
import com.aluminum.manager.attendance.AttendanceDtos.BulkAttendanceRequest;
import com.aluminum.manager.attendance.AttendanceDtos.EmployeeDto;
import com.aluminum.manager.attendance.AttendanceDtos.EmployeeMonthlyRowDto;
import com.aluminum.manager.attendance.AttendanceDtos.EmployeeRequest;
import com.aluminum.manager.attendance.AttendanceDtos.DayKindRequest;
import com.aluminum.manager.attendance.AttendanceDtos.HolidayDto;
import com.aluminum.manager.attendance.AttendanceDtos.HolidayRequest;
import com.aluminum.manager.attendance.AttendanceDtos.RollCallRequest;
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
import java.util.HashSet;
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
	private static final Set<String> HOLIDAY_TYPES = Set.of("SHOP_CLOSED", "NATIONAL_HOLIDAY", "CATHOLIC_HOLIDAY");
	private static final Map<String, String> HOLIDAY_NAMES = Map.of(
			"SHOP_CLOSED", "Nghỉ cửa hàng",
			"NATIONAL_HOLIDAY", "Nghỉ lễ quốc gia",
			"CATHOLIC_HOLIDAY", "Nghỉ lễ công giáo");

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
		shops.requireManufacturing();
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
		shops.requireManufacturing();
		List<EmployeeDto> rows = jdbc.query("""
				SELECT id, employee_code, full_name, phone, address, hire_date, termination_date, is_active, notes
				FROM employees WHERE id = ? AND shop_id = ?
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
				effectivePayRate(id, onDate)), id, shops.idOf(ShopContext.MANUFACTURING));
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
				WHERE employee_code REGEXP '^NV[0-9]+$' AND shop_id = ?
				""", Integer.class, shops.idOf(ShopContext.MANUFACTURING));
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
				ORDER BY effective_from DESC, id DESC LIMIT 1
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

	@Transactional
	public void deletePayRate(long employeeId, long rateId) {
		getEmployee(employeeId);
		List<PayRateDto> rates = jdbc.query("""
				SELECT * FROM employee_pay_rates WHERE employee_id = ?
				ORDER BY effective_from DESC, id DESC FOR UPDATE
				""", (rs, i) -> mapPayRate(rs), employeeId);
		PayRateDto rate = rates.stream().filter(row -> row.id() == rateId).findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy mức công", null));
		if (!rate.effectiveFrom().isAfter(LocalDate.now(clock)) || Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM attendance_records WHERE pay_rate_id = ?)
				""", Boolean.class, rateId))) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ xóa mức công chưa có hiệu lực và chưa được dùng", null);
		}
		jdbc.update("DELETE FROM employee_pay_rates WHERE id = ?", rateId);
		LocalDate previousEnd = rate.effectiveFrom().minusDays(1);
		LocalDate nextStart = rates.stream().filter(row -> row.id() != rateId)
				.map(PayRateDto::effectiveFrom).filter(date -> !date.isBefore(rate.effectiveFrom()))
				.min(LocalDate::compareTo).orElse(null);
		if (!rate.effectiveFrom().equals(nextStart)) {
			jdbc.update("""
					UPDATE employee_pay_rates SET effective_to = ?
					WHERE employee_id = ? AND effective_to = ? AND effective_from < ?
					ORDER BY effective_from DESC, id DESC LIMIT 1
					""", nextStart == null ? null : Date.valueOf(nextStart.minusDays(1)),
					employeeId, Date.valueOf(previousEnd), Date.valueOf(rate.effectiveFrom()));
		}
		audit.record(CurrentAdmin.get().id(), "DELETE_PAY_RATE", "employee_pay_rates", String.valueOf(rateId),
				"Xóa mức công chưa có hiệu lực của nhân viên #" + employeeId);
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
				WHERE employee_id = ? AND effective_from <= ?
				ORDER BY effective_from DESC, id DESC
				LIMIT 1
				""", (rs, i) -> mapPayRate(rs), employeeId, Date.valueOf(onDate));
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
		BigDecimal dayPay = validDayPay(request.dayPay());
		BigDecimal bonus = scaleMoney(request.bonusAmount());
		if (bonus.signum() > 0 && dayPay.signum() > 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Hãy tách tiền thưởng khỏi mức lương ngày lễ cũ", null);
		}
		requireNotLocked(request.holidayDate());
		if (id != null) {
			var dates = jdbc.query("SELECT holiday_date FROM holidays WHERE id = ? FOR UPDATE",
					(rs, row) -> rs.getDate(1).toLocalDate(), id);
			if (dates.isEmpty()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy ngày lễ", null);
			requireNotLocked(dates.get(0));
		}
		if (Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM holidays WHERE holiday_date = ? AND id <> ?)
				""", Boolean.class, Date.valueOf(request.holidayDate()), id == null ? 0L : id))) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngày này đã có trong lịch. Hãy chọn Sửa.", null);
		}
		try {
			if (id == null) {
				long newId = insert("""
						INSERT INTO holidays (holiday_date, name, holiday_type, is_paid, day_pay, notes)
						VALUES (?, ?, ?, ?, ?, ?)
						""",
						Date.valueOf(request.holidayDate()),
						request.name().trim(),
						request.holidayType(),
						paid,
						dayPay,
						blank(request.notes()));
				audit.record(CurrentAdmin.get().id(), "CREATE_HOLIDAY", "holidays", String.valueOf(newId), request.name());
				saveHolidayBonuses(newId, request, bonus);
				return jdbc.query("SELECT * FROM holidays WHERE id = ?", (rs, i) -> mapHoliday(rs), newId).get(0);
			}
			jdbc.update("""
					UPDATE holidays SET holiday_date = ?, name = ?, holiday_type = ?, is_paid = ?, day_pay = ?, notes = ? WHERE id = ?
					""",
					Date.valueOf(request.holidayDate()),
					request.name().trim(),
					request.holidayType(),
					paid,
					dayPay,
					blank(request.notes()),
					id);
			audit.record(CurrentAdmin.get().id(), "UPDATE_HOLIDAY", "holidays", String.valueOf(id), request.name());
			saveHolidayBonuses(id, request, bonus);
			return jdbc.query("SELECT * FROM holidays WHERE id = ?", (rs, i) -> mapHoliday(rs), id).get(0);
		} catch (DuplicateKeyException ex) {
			throw new BusinessException(ErrorCode.DUPLICATE_PRODUCT_CODE, "Ngày lễ này đã tồn tại", null);
		}
	}

	private void saveHolidayBonuses(long id, HolidayRequest request, BigDecimal amount) {
		if (request.bonusAmount() == null) return;
		Date date = Date.valueOf(request.holidayDate());
		List<Long> eligible = amount.signum() == 0 ? List.of() : jdbc.queryForList("""
				SELECT id FROM employees WHERE shop_id = ?
				  AND (is_active = 1 OR termination_date IS NOT NULL)
				  AND (hire_date IS NULL OR hire_date <= ?)
				  AND (termination_date IS NULL OR termination_date >= ?)
				""", Long.class, shops.currentId(), date, date);
		var received = request.receivedEmployeeIds() == null ? java.util.Set.<Long>of()
				: new java.util.HashSet<>(request.receivedEmployeeIds());
		if (amount.signum() > 0 && !eligible.containsAll(received)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Có nhân viên không thuộc danh sách thưởng ngày lễ", null);
		}
		jdbc.update("UPDATE holidays SET bonus_amount = ? WHERE id = ?", amount, id);
		jdbc.update("DELETE FROM holiday_employee_bonuses WHERE holiday_id = ?", id);
		for (long employeeId : eligible) {
			jdbc.update("INSERT INTO holiday_employee_bonuses (holiday_id, employee_id, received) VALUES (?, ?, ?)",
					id, employeeId, received.contains(employeeId));
		}
		jdbc.update("""
				UPDATE financial_transactions f
				LEFT JOIN holiday_employee_bonuses b ON b.holiday_id = f.reference_id AND b.received = 1
				  AND f.idempotency_key = CONCAT('holiday-bonus:', b.holiday_id, ':', b.employee_id)
				SET f.status = 'VOID'
				WHERE f.reference_type = 'HOLIDAY_BONUS' AND f.reference_id = ? AND b.employee_id IS NULL
				""", id);
		jdbc.update("""
				INSERT INTO financial_transactions (
				    transaction_code, transaction_date, transaction_type, category, amount, description,
				    reference_type, reference_id, status, idempotency_key, shop_id, is_internal
				)
				SELECT CONCAT('TL-', h.id, '-', e.id), ?, 'EXPENSE', 'PAYROLL', h.bonus_amount,
				       CONCAT('Chi thưởng lễ: ', h.name, ' · ', e.full_name),
				       'HOLIDAY_BONUS', h.id, 'CONFIRMED', CONCAT('holiday-bonus:', h.id, ':', e.id), e.shop_id, 0
				FROM holiday_employee_bonuses b
				JOIN holidays h ON h.id = b.holiday_id
				JOIN employees e ON e.id = b.employee_id
				WHERE h.id = ? AND b.received = 1
				ON DUPLICATE KEY UPDATE
				    transaction_date = IF(financial_transactions.status = 'VOID', VALUES(transaction_date),
				                          financial_transactions.transaction_date),
				    amount = VALUES(amount), description = VALUES(description), status = 'CONFIRMED'
				""", java.sql.Timestamp.valueOf(LocalDateTime.now(clock)), id);
	}

	@Transactional
	public HolidayDto setDayKind(DayKindRequest request) {
		shops.requireManufacturing();
		requireNotLocked(request.holidayDate());
		String type = request.holidayType() == null ? "" : request.holidayType().trim();
		if (Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM holidays WHERE holiday_date = ? AND bonus_amount > 0)
				""", Boolean.class, Date.valueOf(request.holidayDate())))) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngày có thưởng lễ. Hãy sửa trong Lịch nghỉ lễ.", null);
		}
		jdbc.update("DELETE FROM holidays WHERE holiday_date = ?", Date.valueOf(request.holidayDate()));
		if (type.isEmpty()) {
			audit.record(CurrentAdmin.get().id(), "CLEAR_DAY_KIND", "holidays", request.holidayDate().toString(),
					"Bỏ loại ngày " + request.holidayDate());
			return null;
		}
		if (!HOLIDAY_TYPES.contains(type)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Loại ngày nghỉ không hợp lệ", null);
		}
		BigDecimal dayPay = validDayPay(request.dayPay());
		String name = HOLIDAY_NAMES.get(type);
		long newId = insert("""
				INSERT INTO holidays (holiday_date, name, holiday_type, is_paid, day_pay)
				VALUES (?, ?, ?, ?, ?)
				""",
				Date.valueOf(request.holidayDate()),
				name,
				type,
				dayPay.signum() > 0,
				dayPay);
		audit.record(CurrentAdmin.get().id(), "SET_DAY_KIND", "holidays", String.valueOf(newId),
				name + " " + request.holidayDate() + " lương " + dayPay.toPlainString());
		return jdbc.query("SELECT * FROM holidays WHERE id = ?", (rs, i) -> mapHoliday(rs), newId).get(0);
	}

	private HolidayDto mapHoliday(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new HolidayDto(
				rs.getLong("id"),
				rs.getDate("holiday_date").toLocalDate(),
				rs.getString("name"),
				rs.getString("holiday_type"),
				rs.getBoolean("is_paid"),
				rs.getString("notes"),
				rs.getBigDecimal("day_pay"), rs.getBigDecimal("bonus_amount"),
				jdbc.queryForList("SELECT employee_id FROM holiday_employee_bonuses WHERE holiday_id = ? AND received = 1",
						Long.class, rs.getLong("id")));
	}

	private static BigDecimal validDayPay(BigDecimal raw) {
		BigDecimal pay = scaleMoney(raw);
		if (pay.signum() < 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Lương ngày nghỉ không được âm", null);
		}
		if (pay.remainder(new BigDecimal("500")).signum() != 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Lương ngày nghỉ phải là bội 500 đồng", null);
		}
		return pay;
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
					r.id(), r.employeeId(), r.workDate(), r.status(), r.morningPresent(), r.afternoonPresent(), r.clockIn(), r.clockOut(),
					r.workedHours(), r.creditedDays(), r.lateMinutes(), r.earlyLeaveMinutes(),
					r.notes(), r.dailyRateSnapshot(), r.bonusAmount(), r.bonusNotes(), r.locked(), ots));
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
							null, empId, date, "UNMARKED", false, false, null, null,
							BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, null, currentDaily,
							BigDecimal.ZERO, null, closed, List.of());
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

		List<LocalDate> lockedDates = jdbc.query("""
				SELECT work_date FROM attendance_day_locks
				WHERE work_date BETWEEN ? AND ?
				ORDER BY work_date ASC
				""", (rs, i) -> rs.getDate("work_date").toLocalDate(), Date.valueOf(start), Date.valueOf(end));

		return new MonthlyAttendanceBoardDto(year, month, daysInMonth, closed, policy, holidays, rows, lockedDates);
	}

	@Transactional
	public AttendanceDayDto upsertAttendance(long employeeId, LocalDate date, AttendanceUpsertRequest request) {
		requireNotLocked(date);
		requireDayUnlocked(date);
		getEmployee(employeeId, date);
		String status = request.status().trim();
		if (!VALID_STATUSES.contains(status)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Trạng thái chấm công không hợp lệ", null);
		}
		boolean morning = request.morningPresent() != null
				? request.morningPresent() : "PRESENT".equals(status) || "HALF_DAY".equals(status);
		boolean afternoon = request.afternoonPresent() != null
				? request.afternoonPresent() : "PRESENT".equals(status);
		if (request.morningPresent() != null || request.afternoonPresent() != null) {
			if (morning || afternoon) {
				status = AttendanceCalculator.rollCallStatus(morning, afternoon);
			} else if ("PRESENT".equals(status) || "HALF_DAY".equals(status)) {
				status = "ABSENT";
			}
		}
		if (!("PRESENT".equals(status) || "HALF_DAY".equals(status))) {
			morning = false;
			afternoon = false;
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
		if ("PRESENT".equals(status) || "HALF_DAY".equals(status)) {
			creditedDays = new BigDecimal("0.5").multiply(BigDecimal.valueOf((morning ? 1 : 0) + (afternoon ? 1 : 0)));
		}
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
					    employee_id, work_date, attendance_status, morning_present, afternoon_present, clock_in, clock_out,
					    worked_hours, credited_days, late_minutes, early_leave_minutes,
					    notes, pay_rate_id, daily_rate_snapshot, is_locked)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
					""",
					employeeId,
					Date.valueOf(date),
					status,
					morning,
					afternoon,
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
					SET attendance_status = ?, morning_present = ?, afternoon_present = ?, clock_in = ?, clock_out = ?, worked_hours = ?,
					    credited_days = ?, late_minutes = ?, early_leave_minutes = ?, notes = ?,
					    pay_rate_id = ?, daily_rate_snapshot = ?
					WHERE id = ?
					""",
					status,
					morning,
					afternoon,
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
					item.status(), null, null, item.clockIn(), item.clockOut(), item.notes())));
		}
		return result;
	}

	@Transactional
	public void saveRollCall(RollCallRequest request) {
		shops.requireManufacturing();
		LocalDate date = request.workDate();
		requireNotLocked(date);
		requireRollOpen(date);
		requireDayUnlocked(date);
		List<Long> active = jdbc.queryForList(
				"SELECT id FROM employees WHERE is_active = 1 AND shop_id = ?",
				Long.class, shops.currentId());
		HashSet<Long> expected = new HashSet<>(active);
		HashSet<Long> marked = new HashSet<>();
		for (var item : request.items()) {
			if (!marked.add(item.employeeId())) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Nhân viên bị tích trùng", null);
			}
		}
		if (!marked.equals(expected)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Danh sách nhân viên đã thay đổi. Hãy tải lại phiếu chấm công.", null);
		}
		for (var item : request.items()) {
			AttendanceDayDto saved = upsertAttendance(item.employeeId(), date, new AttendanceUpsertRequest(
					AttendanceCalculator.rollCallStatus(item.morningPresent(), item.afternoonPresent()),
					item.morningPresent(), item.afternoonPresent(), null, null, null));
			if (item.overtimeHours() != null) {
				String source = "Tăng ca từ phiếu điểm danh";
				var overtime = saved.overtimes().stream().filter(ot -> source.equals(ot.notes())).findFirst();
				if (item.overtimeHours().signum() == 0) {
					overtime.ifPresent(ot -> jdbc.update("DELETE FROM overtime_records WHERE id = ?", ot.id()));
				} else {
					var overtimeRequest = new OvertimeRequest(null, null, item.overtimeHours(), true, source);
					if (overtime.isEmpty()) {
						addOvertime(saved.id(), overtimeRequest);
					} else if (overtime.get().overtimeHours().compareTo(item.overtimeHours()) != 0) {
						updateOvertime(overtime.get().id(), overtimeRequest);
					}
				}
			}
		}
		try {
			jdbc.update("""
					INSERT INTO attendance_day_locks (work_date, locked_at, locked_by_admin_id)
					VALUES (?, ?, ?)
					""",
					Date.valueOf(date),
					Timestamp.valueOf(LocalDateTime.now(clock)),
					CurrentAdmin.get().id());
		} catch (DuplicateKeyException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phiếu điểm danh ngày này đã khóa", null);
		}
		audit.record(CurrentAdmin.get().id(), "LOCK_ROLL_CALL", "attendance_day_locks", date.toString(),
				"Điểm danh " + date + " (" + marked.size() + " nhân viên)");
	}

	@Transactional
	public void unlockRollCall(LocalDate date) {
		shops.requireManufacturing();
		requireNotLocked(date);
		requireRollOpen(date);
		jdbc.update("DELETE FROM attendance_day_locks WHERE work_date = ?", Date.valueOf(date));
		audit.record(CurrentAdmin.get().id(), "UNLOCK_ROLL_CALL", "attendance_day_locks", date.toString(),
				"Mở lại phiếu điểm danh " + date);
	}

	@Transactional
	public AttendanceDayDto addOvertime(long attendanceId, OvertimeRequest request) {
		AttendanceDayDto day = getAttendanceById(attendanceId);
		requireNotLocked(day.workDate());
		if (day.locked()) {
			throw new BusinessException(ErrorCode.PAYROLL_PERIOD_LOCKED, "Ngày công đã khóa", null);
		}
		BigDecimal hourlyRate = resolveOvertimeHourlyRate(day.employeeId(), day.workDate());
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
				"WEEKDAY",
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
		BigDecimal hourlyRate = jdbc.queryForObject(
				"SELECT hourly_rate_snapshot FROM overtime_records WHERE id = ?", BigDecimal.class, overtimeId);
		BigDecimal hours = request.overtimeHours().setScale(3, RoundingMode.HALF_UP);
		BigDecimal amount = hours.multiply(hourlyRate).setScale(4, RoundingMode.HALF_UP);
		boolean approved = Boolean.TRUE.equals(request.approved());
		jdbc.update("""
				UPDATE overtime_records
				SET start_at = ?, end_at = ?, overtime_hours = ?,
				    hourly_rate_snapshot = ?, amount = ?, is_approved = ?, notes = ?
				WHERE id = ?
				""",
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

	@Transactional
	public AttendanceDayDto saveBonus(long attendanceId, AttendanceBonusRequest request) {
		AttendanceDayDto day = getAttendanceById(attendanceId);
		requireNotLocked(day.workDate());
		if (day.locked()) {
			throw new BusinessException(ErrorCode.PAYROLL_PERIOD_LOCKED, "Ngày công đã khóa", null);
		}
		BigDecimal amount = scaleMoney(request.amount());
		jdbc.update("UPDATE attendance_records SET bonus_amount = ?, bonus_notes = ? WHERE id = ?",
				amount, blank(request.notes()), attendanceId);
		audit.record(CurrentAdmin.get().id(), "UPDATE_ATTENDANCE_BONUS", "attendance_records",
				String.valueOf(attendanceId), "Thưởng " + amount.toPlainString());
		return getAttendanceById(attendanceId);
	}

	private BigDecimal resolveOvertimeHourlyRate(long employeeId, LocalDate date) {
		PayRateDto rate = effectivePayRate(employeeId, date);
		if (rate == null) {
			return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
		}
		BigDecimal configured = rate.overtimeWeekdayRate();
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
				rs.getBoolean("morning_present"),
				rs.getBoolean("afternoon_present"),
				rs.getTimestamp("clock_in") == null ? null : rs.getTimestamp("clock_in").toLocalDateTime(),
				rs.getTimestamp("clock_out") == null ? null : rs.getTimestamp("clock_out").toLocalDateTime(),
				rs.getBigDecimal("worked_hours"),
				rs.getBigDecimal("credited_days"),
				rs.getInt("late_minutes"),
				rs.getInt("early_leave_minutes"),
				rs.getString("notes"),
				rs.getBigDecimal("daily_rate_snapshot"),
				rs.getBigDecimal("bonus_amount"),
				rs.getString("bonus_notes"),
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
		int created = jdbc.update("""
				INSERT INTO payroll_periods (period_year, period_month, status, notes)
				VALUES (?, ?, 'OPEN', ?)
				ON DUPLICATE KEY UPDATE id = id
				""", request.year(), request.month(), blank(request.notes()));
		long id = jdbc.queryForObject("SELECT id FROM payroll_periods WHERE period_year = ? AND period_month = ?",
				Long.class, request.year(), request.month());
		if (created == 1) {
			audit.record(CurrentAdmin.get().id(), "CREATE_PAYROLL_PERIOD", "payroll_periods", String.valueOf(id),
					"Khởi tạo kỳ công " + request.month() + "/" + request.year());
		}
		PayrollPeriodDto period = getPayrollPeriod(id);
		if (!"OPEN".equals(period.status())) return period;
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
				SELECT employee_id, allowances, deductions, advance_amount FROM payroll_details WHERE payroll_period_id = ?
				""", rs -> {
			existingAdjustments.put(rs.getLong("employee_id"),
					new BigDecimal[]{rs.getBigDecimal("allowances"), rs.getBigDecimal("deductions"), rs.getBigDecimal("advance_amount")});
		}, periodId);

		List<Long> employeeIds = jdbc.query("""
				SELECT DISTINCT e.id, e.employee_code
				FROM employees e
				LEFT JOIN attendance_records a
				  ON a.employee_id = e.id AND a.work_date BETWEEN ? AND ?
				WHERE e.is_active = 1 OR a.id IS NOT NULL
				   OR EXISTS (SELECT 1 FROM holiday_employee_bonuses b JOIN holidays h ON h.id = b.holiday_id
				              WHERE b.employee_id = e.id AND h.holiday_date BETWEEN ? AND ? AND b.received = 0)
				ORDER BY e.employee_code ASC
				""", (rs, i) -> rs.getLong("id"), Date.valueOf(start), Date.valueOf(end), Date.valueOf(start), Date.valueOf(end));

		for (Long empId : employeeIds) {
			EmployeeDto emp = getEmployee(empId, end);
			BigDecimal fallbackRate = emp.currentPayRate() != null ? emp.currentPayRate().dailyRate() : BigDecimal.ZERO;

			BigDecimal creditedDays = scaleQty3(jdbc.queryForObject("""
					SELECT COALESCE(SUM(a.credited_days), 0)
					FROM attendance_records a
					WHERE a.employee_id = ? AND a.work_date BETWEEN ? AND ?
					  AND NOT EXISTS (
					      SELECT 1 FROM holidays h
					      WHERE h.holiday_date = a.work_date AND h.day_pay > 0
					  )
					""", BigDecimal.class, empId, Date.valueOf(start), Date.valueOf(end)));

			Long restDayCount = jdbc.queryForObject("""
					SELECT COUNT(*)
					FROM holidays h
					JOIN employees e ON e.id = ?
					WHERE h.holiday_date BETWEEN ? AND ? AND h.day_pay > 0
					  AND (e.hire_date IS NULL OR h.holiday_date >= e.hire_date)
					  AND (e.termination_date IS NULL OR h.holiday_date <= e.termination_date)
					""", Long.class, empId, Date.valueOf(start), Date.valueOf(end));
			creditedDays = creditedDays.add(BigDecimal.valueOf(restDayCount == null ? 0 : restDayCount));

			BigDecimal basePay = scaleMoney(jdbc.queryForObject("""
					SELECT COALESCE(SUM(a.credited_days * CASE WHEN a.daily_rate_snapshot > 0 THEN a.daily_rate_snapshot ELSE ? END), 0)
					FROM attendance_records a
					WHERE a.employee_id = ? AND a.work_date BETWEEN ? AND ?
					  AND NOT EXISTS (
					      SELECT 1 FROM holidays h
					      WHERE h.holiday_date = a.work_date AND h.day_pay > 0
					  )
					""", BigDecimal.class, fallbackRate, empId, Date.valueOf(start), Date.valueOf(end)));
			BigDecimal restPay = scaleMoney(jdbc.queryForObject("""
					SELECT COALESCE(SUM(h.day_pay), 0)
					FROM holidays h
					JOIN employees e ON e.id = ?
					WHERE h.holiday_date BETWEEN ? AND ? AND h.day_pay > 0
					  AND (e.hire_date IS NULL OR h.holiday_date >= e.hire_date)
					  AND (e.termination_date IS NULL OR h.holiday_date <= e.termination_date)
					""", BigDecimal.class, empId, Date.valueOf(start), Date.valueOf(end)));
			basePay = basePay.add(restPay);

			List<OvertimeLine> otLines = jdbc.query("""
					SELECT o.is_approved, o.amount
					FROM overtime_records o
					JOIN attendance_records a ON a.id = o.attendance_id
					WHERE a.employee_id = ? AND a.work_date BETWEEN ? AND ?
					""", (rs, i) -> new OvertimeLine(rs.getBoolean("is_approved"), rs.getBigDecimal("amount")),
					empId, Date.valueOf(start), Date.valueOf(end));
			BigDecimal overtimePay = AttendanceCalculator.approvedOvertimePay(otLines);
			BigDecimal overtimeHours = payrollOvertimeHours(empId, start, end);
			BigDecimal holidayReceived = holidayBonusReceived(empId, start, end);
			BigDecimal bonusPay = scaleMoney(jdbc.queryForObject("""
					SELECT COALESCE(SUM(bonus_amount), 0) FROM attendance_records
					WHERE employee_id = ? AND work_date BETWEEN ? AND ?
					""", BigDecimal.class, empId, Date.valueOf(start), Date.valueOf(end)));
			BigDecimal holidayBonus = scaleMoney(jdbc.queryForObject("""
					SELECT COALESCE(SUM(h.bonus_amount), 0)
					FROM holiday_employee_bonuses b JOIN holidays h ON h.id = b.holiday_id
					WHERE b.employee_id = ? AND b.received = 0 AND h.holiday_date BETWEEN ? AND ?
					""", BigDecimal.class, empId, Date.valueOf(start), Date.valueOf(end)));
			bonusPay = bonusPay.add(holidayBonus);

			BigDecimal[] adj = existingAdjustments.getOrDefault(empId,
					new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
			BigDecimal allowances = scaleMoney(adj[0]);
			BigDecimal deductions = scaleMoney(adj[1]);
			BigDecimal advance = scaleMoney(adj[2]);
			BigDecimal totalPay = basePay.add(overtimePay).add(bonusPay).add(allowances).subtract(deductions).subtract(advance)
					.setScale(4, RoundingMode.HALF_UP);
			if (totalPay.signum() < 0) {
				totalPay = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
			}

			String snapshotJson = """
					{"employeeCode":"%s","fullName":"%s","dailyRate":%s,"creditedDays":%s,"basePay":%s,"overtimePay":%s,"bonusPay":%s,"allowances":%s,"deductions":%s,"totalPay":%s}
					""".formatted(
					escapeJson(emp.employeeCode()),
					escapeJson(emp.fullName()),
					fallbackRate.toPlainString(),
					creditedDays.toPlainString(),
					basePay.toPlainString(),
					overtimePay.toPlainString(),
					bonusPay.toPlainString(),
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
					    bonus_pay, allowances, deductions, total_pay, calculation_snapshot, holiday_bonus_pay,
					    overtime_hours, holiday_bonus_received)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
						""",
					periodId, empId, creditedDays, basePay, overtimePay, bonusPay, allowances, deductions, totalPay,
					snapshotJson, holidayBonus, overtimeHours, holidayReceived);
			} else {
				jdbc.update("""
						UPDATE payroll_details
					SET credited_days = ?, base_pay = ?, overtime_pay = ?, bonus_pay = ?, allowances = ?,
						    deductions = ?, total_pay = ?, calculation_snapshot = ?, holiday_bonus_pay = ?,
						    overtime_hours = ?, holiday_bonus_received = ?
						WHERE id = ?
						""",
					creditedDays, basePay, overtimePay, bonusPay, allowances, deductions, totalPay, snapshotJson,
					holidayBonus, overtimeHours, holidayReceived, detailIds.get(0));
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
					"Kỳ công đã chốt, không chỉnh sửa phụ cấp và tiền ứng", null);
		}
		List<Map<String, Object>> rows = jdbc.queryForList("""
				SELECT id, base_pay, overtime_pay, bonus_pay, deductions FROM payroll_details WHERE id = ? AND payroll_period_id = ?
				""", detailId, periodId);
		if (rows.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy dòng lương nhân viên", null);
		}
		BigDecimal basePay = (BigDecimal) rows.get(0).get("base_pay");
		BigDecimal overtimePay = (BigDecimal) rows.get(0).get("overtime_pay");
		BigDecimal bonusPay = (BigDecimal) rows.get(0).get("bonus_pay");
		BigDecimal allowances = scaleMoney(request.allowances());
		BigDecimal deductions = (BigDecimal) rows.get(0).get("deductions");
		BigDecimal advance = scaleMoney(request.advanceAmount());
		BigDecimal totalPay = scaleMoney(basePay.add(overtimePay).add(bonusPay).add(allowances)
				.subtract(deductions).subtract(advance).max(BigDecimal.ZERO));
		jdbc.update("""
				UPDATE payroll_details SET allowances = ?, advance_amount = ?, total_pay = ? WHERE id = ?
				""", allowances, advance, totalPay, detailId);
		audit.record(CurrentAdmin.get().id(), "ADJUST_PAYROLL_DETAIL", "payroll_details", String.valueOf(detailId),
				"Cập nhật phụ cấp và tiền ứng kỳ " + header.month() + "/" + header.year());
		return getPayrollPeriod(periodId);
	}

	@Transactional
	public PayrollPeriodDto closePeriod(long periodId) {
		PeriodHeader header = lockPeriod(periodId);
		if ("CLOSED".equals(header.status())) {
			throw new BusinessException(ErrorCode.PAYROLL_PERIOD_LOCKED, "Kỳ công này đã được chốt", null);
		}
		calculatePayroll(periodId);
		if (Boolean.TRUE.equals(jdbc.queryForObject("""
				SELECT EXISTS (SELECT 1 FROM payroll_details WHERE payroll_period_id = ?
				  AND advance_amount > 0 AND advance_amount + deductions > base_pay + overtime_pay + bonus_pay + allowances)
				""", Boolean.class, periodId))) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Có tiền ứng vượt số tiền công có thể trừ. Hãy kiểm tra trước khi chốt.", null);
		}
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
			       d.credited_days, d.base_pay, d.overtime_pay, d.bonus_pay, d.allowances, d.deductions, d.total_pay,
			       d.advance_amount, d.overtime_hours, d.holiday_bonus_pay, d.holiday_bonus_received
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
				rs.getBigDecimal("bonus_pay"),
				rs.getBigDecimal("allowances"),
				rs.getBigDecimal("deductions"),
				rs.getBigDecimal("total_pay"), rs.getBigDecimal("advance_amount"), rs.getBigDecimal("overtime_hours"),
				rs.getBigDecimal("holiday_bonus_pay"), rs.getBigDecimal("holiday_bonus_received")), periodId);
	}

	private BigDecimal payrollOvertimeHours(long employeeId, LocalDate start, LocalDate end) {
		return jdbc.queryForObject("""
				SELECT COALESCE(SUM(o.overtime_hours), 0) FROM overtime_records o
				JOIN attendance_records a ON a.id = o.attendance_id
				WHERE a.employee_id = ? AND a.work_date BETWEEN ? AND ? AND o.is_approved = 1
				""", BigDecimal.class, employeeId, Date.valueOf(start), Date.valueOf(end));
	}

	private BigDecimal holidayBonusReceived(long employeeId, LocalDate start, LocalDate end) {
		return jdbc.queryForObject("""
				SELECT COALESCE(SUM(h.bonus_amount), 0)
				FROM holiday_employee_bonuses b JOIN holidays h ON h.id = b.holiday_id
				WHERE b.employee_id = ? AND b.received = 1 AND h.holiday_date BETWEEN ? AND ?
				""", BigDecimal.class, employeeId, Date.valueOf(start), Date.valueOf(end));
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

	private void requireDayUnlocked(LocalDate date) {
		Integer locks = jdbc.queryForObject(
				"SELECT COUNT(*) FROM attendance_day_locks WHERE work_date = ?",
				Integer.class, Date.valueOf(date));
		if (locks != null && locks > 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Phiếu điểm danh ngày " + date.getDayOfMonth() + "/" + date.getMonthValue() + " đã khóa", null);
		}
	}

	private void requireRollOpen(LocalDate date) {
		LocalDateTime now = LocalDateTime.now(clock);
		if (now.isBefore(date.atTime(0, 1)) || !now.isBefore(date.plusDays(1).atStartOfDay())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Phiếu điểm danh chỉ mở từ 00:01 đến 23:59 trong ngày chấm công", null);
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
