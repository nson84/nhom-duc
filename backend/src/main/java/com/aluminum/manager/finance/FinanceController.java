package com.aluminum.manager.finance;

import com.aluminum.manager.common.ApiResponse;
import com.aluminum.manager.finance.FinanceDtos.CreateTransactionRequest;
import com.aluminum.manager.finance.FinanceDtos.FinanceSummaryDto;
import com.aluminum.manager.finance.FinanceDtos.InventoryReportRowDto;
import com.aluminum.manager.finance.FinanceDtos.PayrollReportRowDto;
import com.aluminum.manager.finance.FinanceDtos.ProductSalesRowDto;
import com.aluminum.manager.finance.FinanceDtos.ProfitReportDto;
import com.aluminum.manager.finance.FinanceDtos.PurchasePriceVarianceRowDto;
import com.aluminum.manager.finance.FinanceDtos.TransactionDto;
import com.aluminum.manager.finance.FinanceDtos.VoidTransactionRequest;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class FinanceController {

	private final FinanceService service;

	public FinanceController(FinanceService service) {
		this.service = service;
	}

	@GetMapping("/finance/transactions")
	public ApiResponse<List<TransactionDto>> transactions(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String category,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String q) {
		return ApiResponse.ok(service.listTransactions(from, to, type, category, status, q), null);
	}

	@PostMapping("/finance/transactions")
	public ApiResponse<TransactionDto> create(@Valid @RequestBody CreateTransactionRequest request) {
		return ApiResponse.ok(service.createTransaction(request), "Đã ghi nhận phiếu thu/chi");
	}

	@PostMapping("/finance/transactions/{id}/void")
	public ApiResponse<TransactionDto> voidTransaction(
			@PathVariable long id, @Valid @RequestBody VoidTransactionRequest request) {
		return ApiResponse.ok(service.voidTransaction(id, request.reason()), "Đã hủy phiếu thu/chi");
	}

	@GetMapping("/finance/summary")
	public ApiResponse<FinanceSummaryDto> summary(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ApiResponse.ok(service.summary(from, to), null);
	}

	@GetMapping({"/reports/profit", "/reports/revenue"})
	public ApiResponse<ProfitReportDto> profit(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ApiResponse.ok(service.profitReport(from, to), null);
	}

	@GetMapping("/reports/sales-by-product")
	public ApiResponse<List<ProductSalesRowDto>> salesByProduct(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long productId,
			@RequestParam(required = false) Long categoryId) {
		return ApiResponse.ok(service.salesByProduct(from, to, productId, categoryId), null);
	}

	@GetMapping("/reports/inventory")
	public ApiResponse<List<InventoryReportRowDto>> inventory(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long categoryId) {
		return ApiResponse.ok(service.inventoryReport(from, to, categoryId), null);
	}

	@GetMapping("/reports/price-variance")
	public ApiResponse<List<PurchasePriceVarianceRowDto>> priceVariance(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long categoryId) {
		return ApiResponse.ok(service.purchasePriceVariance(from, to, categoryId), null);
	}

	@GetMapping("/reports/payroll")
	public ApiResponse<List<PayrollReportRowDto>> payroll(@RequestParam(required = false) Integer year) {
		return ApiResponse.ok(service.payrollReport(year), null);
	}

	@GetMapping("/reports/export")
	public ResponseEntity<byte[]> export(
			@RequestParam(defaultValue = "profit") String type,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long productId,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) Integer year) {
		String csv = service.exportCsv(type, from, to, productId, categoryId, year);
		String filename = "bao-cao-" + type + ".csv";
		return ResponseEntity.ok()
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
				.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
				.body(csv.getBytes(StandardCharsets.UTF_8));
	}
}
