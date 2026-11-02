package com.aluminum.manager.attendance;

import com.aluminum.manager.attendance.AttendanceDtos.AttendanceDayDto;
import com.aluminum.manager.attendance.AttendanceDtos.AttendancePolicyDto;
import com.aluminum.manager.attendance.AttendanceDtos.AttendancePolicyRequest;
import com.aluminum.manager.attendance.AttendanceDtos.AttendanceUpsertRequest;
import com.aluminum.manager.attendance.AttendanceDtos.BulkAttendanceRequest;
import com.aluminum.manager.attendance.AttendanceDtos.EmployeeDto;
import com.aluminum.manager.attendance.AttendanceDtos.EmployeeRequest;
import com.aluminum.manager.attendance.AttendanceDtos.HolidayDto;
import com.aluminum.manager.attendance.AttendanceDtos.HolidayRequest;
import com.aluminum.manager.attendance.AttendanceDtos.MonthlyAttendanceBoardDto;
import com.aluminum.manager.attendance.AttendanceDtos.OvertimeRequest;
import com.aluminum.manager.attendance.AttendanceDtos.PayPayrollRequest;
import com.aluminum.manager.attendance.AttendanceDtos.PayRateDto;
import com.aluminum.manager.attendance.AttendanceDtos.PayRateRequest;
import com.aluminum.manager.attendance.AttendanceDtos.PayrollAdjustDetailRequest;
import com.aluminum.manager.attendance.AttendanceDtos.PayrollPeriodDto;
import com.aluminum.manager.attendance.AttendanceDtos.PayrollPeriodRequest;
import com.aluminum.manager.attendance.AttendanceDtos.ReopenPayrollRequest;
import com.aluminum.manager.common.ApiResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AttendanceController {

	private final AttendanceService service;

	public AttendanceController(AttendanceService service) {
		this.service = service;
	}

	// ---------- Employees ----------

	@GetMapping("/employees")
	public ApiResponse<List<EmployeeDto>> listEmployees(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) Boolean active) {
		return ApiResponse.ok(service.listEmployees(q, active), null);
	}

	@GetMapping("/employees/{id}")
	public ApiResponse<EmployeeDto> getEmployee(@PathVariable long id) {
		return ApiResponse.ok(service.getEmployee(id), null);
	}

	@PostMapping("/employees")
	public ApiResponse<EmployeeDto> createEmployee(@Valid @RequestBody EmployeeRequest request) {
		return ApiResponse.ok(service.createEmployee(request), "Đã thêm nhân viên");
	}

	@PutMapping("/employees/{id}")
	public ApiResponse<EmployeeDto> updateEmployee(
			@PathVariable long id, @Valid @RequestBody EmployeeRequest request) {
		return ApiResponse.ok(service.updateEmployee(id, request), "Đã lưu hồ sơ nhân viên");
	}

	@PatchMapping("/employees/{id}/status")
	public ApiResponse<EmployeeDto> updateEmployeeStatus(
			@PathVariable long id, @RequestBody Map<String, Boolean> body) {
		boolean active = Boolean.TRUE.equals(body.get("active"));
		return ApiResponse.ok(service.updateEmployeeStatus(id, active), "Đã cập nhật trạng thái nhân viên");
	}

	@GetMapping("/employees/{id}/pay-rates")
	public ApiResponse<List<PayRateDto>> listPayRates(@PathVariable long id) {
		return ApiResponse.ok(service.listPayRates(id), null);
	}

	@PostMapping("/employees/{id}/pay-rates")
	public ApiResponse<PayRateDto> addPayRate(
			@PathVariable long id, @Valid @RequestBody PayRateRequest request) {
		return ApiResponse.ok(service.addPayRate(id, request), "Đã lưu mức công mới");
	}

	// ---------- Holidays & Policy ----------

	@GetMapping("/holidays")
	public ApiResponse<List<HolidayDto>> listHolidays(@RequestParam(required = false) Integer year) {
		return ApiResponse.ok(service.listHolidays(year), null);
	}

	@PostMapping("/holidays")
	public ApiResponse<HolidayDto> createHoliday(@Valid @RequestBody HolidayRequest request) {
		return ApiResponse.ok(service.saveHoliday(null, request), "Đã thêm ngày nghỉ lễ");
	}

	@PutMapping("/holidays/{id}")
	public ApiResponse<HolidayDto> updateHoliday(
			@PathVariable long id, @Valid @RequestBody HolidayRequest request) {
		return ApiResponse.ok(service.saveHoliday(id, request), "Đã cập nhật ngày nghỉ lễ");
	}

	@GetMapping("/attendance/policy")
	public ApiResponse<AttendancePolicyDto> getPolicy(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate onDate) {
		return ApiResponse.ok(service.currentPolicy(onDate), null);
	}

	@PostMapping("/attendance/policy")
	public ApiResponse<AttendancePolicyDto> savePolicy(@Valid @RequestBody AttendancePolicyRequest request) {
		return ApiResponse.ok(service.savePolicy(request), "Đã lưu cấu hình chấm công");
	}

	// ---------- Monthly Attendance & Overtime ----------

	@GetMapping("/attendance/monthly")
	public ApiResponse<MonthlyAttendanceBoardDto> monthly(
			@RequestParam int year, @RequestParam int month) {
		return ApiResponse.ok(service.monthlyBoard(year, month), null);
	}

	@PutMapping("/attendance/{employeeId}/{date}")
	public ApiResponse<AttendanceDayDto> upsertAttendance(
			@PathVariable long employeeId,
			@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@Valid @RequestBody AttendanceUpsertRequest request) {
		return ApiResponse.ok(service.upsertAttendance(employeeId, date, request), "Đã lưu chấm công");
	}

	@PostMapping("/attendance/bulk")
	public ApiResponse<List<AttendanceDayDto>> bulkAttendance(@Valid @RequestBody BulkAttendanceRequest request) {
		return ApiResponse.ok(service.bulkUpsert(request), "Đã lưu bảng chấm công");
	}

	@PostMapping("/attendance/{id}/overtime")
	public ApiResponse<AttendanceDayDto> addOvertime(
			@PathVariable long id, @Valid @RequestBody OvertimeRequest request) {
		return ApiResponse.ok(service.addOvertime(id, request), "Đã thêm tăng ca");
	}

	@PutMapping("/overtime/{id}")
	public ApiResponse<AttendanceDayDto> updateOvertime(
			@PathVariable long id, @Valid @RequestBody OvertimeRequest request) {
		return ApiResponse.ok(service.updateOvertime(id, request), "Đã cập nhật tăng ca");
	}

	// ---------- Payroll ----------

	@GetMapping("/payroll/periods")
	public ApiResponse<List<PayrollPeriodDto>> listPeriods() {
		return ApiResponse.ok(service.listPayrollPeriods(), null);
	}

	@PostMapping("/payroll/periods")
	public ApiResponse<PayrollPeriodDto> createOrGetPeriod(@Valid @RequestBody PayrollPeriodRequest request) {
		return ApiResponse.ok(service.createOrGetPeriod(request), "Đã tải kỳ tính công");
	}

	@GetMapping("/payroll/periods/{id}")
	public ApiResponse<PayrollPeriodDto> getPeriod(@PathVariable long id) {
		return ApiResponse.ok(service.getPayrollPeriod(id), null);
	}

	@PostMapping("/payroll/periods/{id}/calculate")
	public ApiResponse<PayrollPeriodDto> calculate(@PathVariable long id) {
		return ApiResponse.ok(service.calculatePayroll(id), "Đã tính lại tiền công");
	}

	@PutMapping("/payroll/periods/{id}/details/{detailId}")
	public ApiResponse<PayrollPeriodDto> adjustDetail(
			@PathVariable long id,
			@PathVariable long detailId,
			@Valid @RequestBody PayrollAdjustDetailRequest request) {
		return ApiResponse.ok(service.adjustPayrollDetail(id, detailId, request), "Đã lưu phụ cấp / khấu trừ");
	}

	@PostMapping("/payroll/periods/{id}/close")
	public ApiResponse<PayrollPeriodDto> close(@PathVariable long id) {
		return ApiResponse.ok(service.closePeriod(id), "Đã chốt kỳ công");
	}

	@PostMapping("/payroll/periods/{id}/reopen")
	public ApiResponse<PayrollPeriodDto> reopen(
			@PathVariable long id, @Valid @RequestBody ReopenPayrollRequest request) {
		return ApiResponse.ok(service.reopenPeriod(id, request.reason()), "Đã mở lại kỳ công");
	}

	@PostMapping("/payroll/periods/{id}/pay")
	public ApiResponse<PayrollPeriodDto> pay(
			@PathVariable long id, @Valid @RequestBody PayPayrollRequest request) {
		return ApiResponse.ok(service.payPeriod(id, request), "Đã ghi nhận chi lương");
	}
}
