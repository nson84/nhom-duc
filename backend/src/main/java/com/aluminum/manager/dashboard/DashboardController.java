package com.aluminum.manager.dashboard;

import com.aluminum.manager.common.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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

	public DashboardController(DashboardService dashboardService, YearEndBalanceService yearEndBalances) {
		this.dashboardService = dashboardService;
		this.yearEndBalances = yearEndBalances;
	}

	@GetMapping("/year-end")
	public ApiResponse<YearEndBalanceService.Balance> yearEnd(@RequestParam int year) {
		return ApiResponse.ok(yearEndBalances.get(year), null);
	}

	@GetMapping("/year-period")
	public ApiResponse<YearEndBalanceService.Period> yearPeriod(@RequestParam int year) {
		return ApiResponse.ok(yearEndBalances.period(year), null);
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
