package com.aluminum.manager.dashboard;

import com.aluminum.manager.common.ApiResponse;
import com.aluminum.manager.security.CurrentAdmin;
import com.aluminum.manager.system.AuditService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

	private final DashboardService dashboardService;
	private final YearEndBalanceService yearEndBalances;
	private final AuditService audit;

	public DashboardController(DashboardService dashboardService, YearEndBalanceService yearEndBalances, AuditService audit) {
		this.dashboardService = dashboardService;
		this.yearEndBalances = yearEndBalances;
		this.audit = audit;
	}

	@PostMapping("/year-reopen")
	@Transactional
	public ApiResponse<Boolean> reopenYear(@RequestParam int year) {
		var previous = yearEndBalances.reopenYear(year);
		audit.record(CurrentAdmin.get().id(), "REOPEN_YEAR", "year_end_balances", String.valueOf(year), previous.toString());
		return ApiResponse.ok(true, "Đã mở lại kiểm kê năm");
	}

	@GetMapping("/year-end")
	public ApiResponse<YearEndBalanceService.Balance> yearEnd(@RequestParam int year) {
		return ApiResponse.ok(yearEndBalances.get(year), null);
	}

	@GetMapping("/distribution-year-report")
	public ApiResponse<DashboardService.DistributionYearReport> distributionYearReport(@RequestParam int year) {
		return ApiResponse.ok(dashboardService.distributionYearReport(year), null);
	}

	@GetMapping("/year-period")
	public ApiResponse<YearEndBalanceService.Period> yearPeriod(@RequestParam int year) {
		return ApiResponse.ok(yearEndBalances.period(year), null);
	}

	@GetMapping("/year-periods")
	public ApiResponse<List<YearEndBalanceService.Period>> yearPeriods() {
		return ApiResponse.ok(yearEndBalances.listPeriods(), null);
	}

	@GetMapping("/month-close")
	public ApiResponse<Boolean> monthClosed(@RequestParam String month) {
		return ApiResponse.ok(yearEndBalances.isMonthClosed(YearMonth.parse(month)), null);
	}

	@GetMapping("/month-status")
	public ApiResponse<String> monthStatus(@RequestParam String month) {
		return ApiResponse.ok(yearEndBalances.monthStatus(YearMonth.parse(month)), null);
	}

	@PostMapping("/month-open")
	public ApiResponse<String> openMonth(@RequestParam int year, @RequestParam String month) {
		return ApiResponse.ok(yearEndBalances.openMonth(year, YearMonth.parse(month)), "Đã mở tháng");
	}

	@PostMapping("/month-close")
	public ApiResponse<Boolean> closeMonth(@RequestParam int year, @RequestParam String month) {
		return ApiResponse.ok(yearEndBalances.closeMonth(year, YearMonth.parse(month)), "Đã chốt tháng");
	}

	@PostMapping("/month-reopen")
	public ApiResponse<Boolean> reopenMonth(@RequestParam int year, @RequestParam String month) {
		return ApiResponse.ok(yearEndBalances.reopenMonth(year, YearMonth.parse(month)), "Đã mở lại tháng");
	}

	@PostMapping("/year-period")
	public ApiResponse<YearEndBalanceService.Period> saveYearPeriod(@RequestParam int year,
			@RequestParam LocalDate from, @RequestParam LocalDate to) {
		return ApiResponse.ok(yearEndBalances.saveDraftPeriod(year, from, to), "Đã lưu ngày kết thúc dự kiến");
	}

	@PostMapping("/year-period/start")
	public ApiResponse<YearEndBalanceService.Period> saveYearStart(@RequestParam int year, @RequestParam LocalDate from,
			@RequestParam String name) {
		return ApiResponse.ok(yearEndBalances.saveStart(year, from, name), "Đã bắt đầu kỳ");
	}

	@org.springframework.web.bind.annotation.DeleteMapping("/year-period")
	public ApiResponse<YearEndBalanceService.Period> deleteYearPeriod(@RequestParam int year, @RequestParam LocalDate from) {
		return ApiResponse.ok(yearEndBalances.deleteUnusedPeriod(year, from), "Đã xóa kỳ chưa sử dụng");
	}

	@GetMapping("/summary")
	public ApiResponse<DashboardSummary> summary(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ApiResponse.ok(dashboardService.summary(from, to), null);
	}

	@PostMapping("/distribution-close")
	public ApiResponse<DashboardSummary.Closing> closeDistribution(@RequestParam LocalDate from, @RequestParam LocalDate to,
			@RequestParam(defaultValue = "false") boolean annual, @RequestParam(required = false) Integer year) {
		return ApiResponse.ok(dashboardService.closeDistribution(from, to, annual, year), "Đã chốt hạch toán Phân phối");
	}

	@GetMapping("/charts")
	public ApiResponse<List<DashboardPoint>> charts(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ApiResponse.ok(dashboardService.charts(from, to), null);
	}
}
