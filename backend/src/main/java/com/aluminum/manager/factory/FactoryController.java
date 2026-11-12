package com.aluminum.manager.factory;

import com.aluminum.manager.common.ApiResponse;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.factory.FactoryDtos.CustomerYearDto;
import com.aluminum.manager.factory.FactoryDtos.RawCountDto;
import com.aluminum.manager.factory.FactoryDtos.RawCountRequest;
import com.aluminum.manager.factory.FactoryDtos.PurchaseCostOption;
import com.aluminum.manager.factory.FactoryDtos.SaleCostReport;
import com.aluminum.manager.factory.FactoryDtos.ScrapPaymentRequest;
import com.aluminum.manager.factory.FactoryDtos.ScrapSaleDto;
import com.aluminum.manager.factory.FactoryDtos.ScrapSaleRequest;
import com.aluminum.manager.factory.FactoryDtos.YearCountDto;
import com.aluminum.manager.factory.FactoryDtos.YearCountRequest;
import com.aluminum.manager.factory.FactoryDtos.YearReportDto;
import com.aluminum.manager.factory.FactoryDtos.SettlementReportDto;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/factory")
public class FactoryController {
	private final FactoryService service;

	public FactoryController(FactoryService service) {
		this.service = service;
	}

	@GetMapping("/years/{year}/report")
	public ApiResponse<YearReportDto> report(@PathVariable int year) {
		return ApiResponse.ok(service.yearReport(year), null);
	}

	@GetMapping("/years/{year}/count")
	public ApiResponse<YearCountDto> count(@PathVariable int year) {
		return ApiResponse.ok(service.yearCount(year), null);
	}

	@GetMapping("/years/{year}/raw-counts")
	public ApiResponse<List<RawCountDto>> rawCounts(@PathVariable int year) {
		return ApiResponse.ok(service.rawCounts(year), null);
	}

	@PutMapping("/years/{year}/raw-counts")
	public ApiResponse<List<RawCountDto>> saveRawCounts(@PathVariable int year,
			@Valid @RequestBody RawCountRequest request) {
		return ApiResponse.ok(service.saveRawCounts(year, request), "Đã tính tồn nguyên liệu theo phiếu nhập");
	}

	@GetMapping("/purchase-costs")
	public ApiResponse<List<PurchaseCostOption>> purchaseCosts() {
		return ApiResponse.ok(service.purchaseCosts(), null);
	}

	@GetMapping("/years/{year}/sale-costs")
	public ApiResponse<SaleCostReport> saleCosts(@PathVariable int year,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) BigDecimal costPerKg) {
		return ApiResponse.ok(service.saleCosts(year, categoryId, costPerKg), null);
	}

	@PutMapping("/sale-items/{itemId}/purchase-reference")
	public ApiResponse<SaleCostReport> attachSaleCost(@PathVariable long itemId,
			@RequestBody Map<String, Long> body) {
			Long sourceId = body.get("purchaseItemId");
			if (sourceId == null) throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Chọn phiếu nhập tham chiếu", null);
			return ApiResponse.ok(service.attachSaleCost(itemId, sourceId), "Đã gắn giá nhập tham chiếu");
	}

	@PutMapping("/years/{year}/count")
	public ApiResponse<YearCountDto> saveCount(@PathVariable int year,
			@Valid @RequestBody YearCountRequest request) {
		return ApiResponse.ok(service.saveYearCount(year, request), "Đã lưu kiểm kê");
	}

	@PostMapping("/years/{year}/close")
	public ApiResponse<YearCountDto> close(@PathVariable int year) {
		return ApiResponse.ok(service.closeYear(year), "Đã chốt năm");
	}

	@GetMapping("/years/{year}/scrap-sales")
	public ApiResponse<List<ScrapSaleDto>> scrapSales(@PathVariable int year) {
		return ApiResponse.ok(service.scrapSales(year), null);
	}

	@PutMapping("/years/{year}/inventory-count")
	public ApiResponse<YearCountDto> saveInventory(@PathVariable int year,
			@Valid @RequestBody FactoryDtos.InventorySaveRequest request) {
		return ApiResponse.ok(service.saveInventory(year, request, false), "Đã lưu toàn bộ kiểm kê");
	}

	@PostMapping("/years/{year}/finalize")
	public ApiResponse<YearCountDto> finalizeInventory(@PathVariable int year,
			@Valid @RequestBody FactoryDtos.InventorySaveRequest request) {
		return ApiResponse.ok(service.saveInventory(year, request, true), "Đã lưu và chốt kiểm kê");
	}

	@PostMapping("/scrap-sales")
	public ApiResponse<ScrapSaleDto> createScrap(@Valid @RequestBody ScrapSaleRequest request) {
		return ApiResponse.ok(service.createScrapSale(request), "Đã lưu phiếu bán phế liệu");
	}

	@PostMapping("/scrap-sales/{id}/payments")
	public ApiResponse<ScrapSaleDto> payScrap(@PathVariable long id,
			@Valid @RequestBody ScrapPaymentRequest request) {
		return ApiResponse.ok(service.payScrap(id, request), "Đã ghi thu tiền phế liệu");
	}

	@PostMapping("/scrap-sales/{id}/void")
	public ApiResponse<ScrapSaleDto> voidScrap(@PathVariable long id,
			@RequestBody Map<String, String> body) {
		return ApiResponse.ok(service.voidScrap(id, body.get("reason")), "Đã hủy phiếu phế liệu");
	}

	@GetMapping("/customers/{customerId}/years/{year}")
	public ApiResponse<CustomerYearDto> customerYear(@PathVariable long customerId, @PathVariable int year) {
		return ApiResponse.ok(service.customerYear(customerId, year), null);
	}

	@GetMapping("/years/{year}/settlement")
	public ApiResponse<SettlementReportDto> settlement(@PathVariable int year,
			@RequestParam(required = false) Integer month,
			@RequestParam(required = false) Integer monthYear) {
		return ApiResponse.ok(service.settlement(year, month, monthYear), null);
	}
}
