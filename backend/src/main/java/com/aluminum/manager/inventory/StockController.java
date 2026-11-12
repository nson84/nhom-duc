package com.aluminum.manager.inventory;

import com.aluminum.manager.common.ApiResponse;
import com.aluminum.manager.inventory.StockDtos.DocumentDto;
import com.aluminum.manager.inventory.StockDtos.DocumentRequest;
import com.aluminum.manager.inventory.StockDtos.DocumentSummary;
import com.aluminum.manager.inventory.StockDtos.MovementDto;
import com.aluminum.manager.inventory.StockDtos.PaymentRequest;
import com.aluminum.manager.inventory.StockDtos.ReverseRequest;
import com.aluminum.manager.inventory.StockDtos.StockBalanceDto;
import com.aluminum.manager.inventory.StockDtos.InventoryCountRequest;
import com.aluminum.manager.inventory.StockDtos.InventoryCountResult;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class StockController {

	private final StockDocumentService service;


	public StockController(StockDocumentService service) {
		this.service = service;
	}

	@GetMapping("/stock-documents")
	public ApiResponse<List<DocumentSummary>> list(
			@RequestParam(required = false) String type,
			@RequestParam(required = false) String filter,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) String q) {
		String bucket = filter != null && !filter.isBlank() ? filter : status;
		return ApiResponse.ok(service.list(type, bucket, from, to, q), null);
	}

	@PostMapping("/stock-documents/{id}/trash")
	public ApiResponse<DocumentDto> trash(@PathVariable long id) {
		return ApiResponse.ok(service.moveToTrash(id), "Đã chuyển vào thùng rác");
	}

	@PostMapping("/stock-documents/{id}/restore")
	public ApiResponse<DocumentDto> restore(@PathVariable long id) {
		return ApiResponse.ok(service.restoreFromTrash(id), "Đã khôi phục phiếu");
	}

	@GetMapping("/stock-documents/{id}")
	public ApiResponse<DocumentDto> get(@PathVariable long id) {
		return ApiResponse.ok(service.get(id), null);
	}

	@PostMapping("/stock-documents")
	public ApiResponse<DocumentDto> create(@Valid @RequestBody DocumentRequest request, @RequestParam(defaultValue = "false") boolean confirm) {
		if (confirm) return ApiResponse.ok(service.saveConfirmedSale(null, request), "Đã xác nhận phiếu bán");
		return ApiResponse.ok(service.createDraft(request), "Đã lưu phiếu nháp");
	}

	@PutMapping("/stock-documents/{id}")
	public ApiResponse<DocumentDto> update(@PathVariable long id, @Valid @RequestBody DocumentRequest request, @RequestParam(defaultValue = "false") boolean confirm) {
		if (confirm) return ApiResponse.ok(service.saveConfirmedSale(id, request), "Đã xác nhận phiếu bán");
		return ApiResponse.ok(service.updateDraft(id, request), "Đã lưu phiếu nháp");
	}

	@PostMapping("/stock-documents/{id}/cancel")
	public ApiResponse<DocumentDto> cancel(@PathVariable long id) {
		return ApiResponse.ok(service.cancelDraft(id), "Đã hủy phiếu nháp");
	}

	@PostMapping("/stock-documents/{id}/confirm")
	public ApiResponse<DocumentDto> confirm(@PathVariable long id) {
		return ApiResponse.ok(service.confirm(id), "Đã xác nhận phiếu");
	}

	@PostMapping("/stock-documents/{id}/reverse")
	public ApiResponse<DocumentDto> reverse(@PathVariable long id, @Valid @RequestBody ReverseRequest request) {
		return ApiResponse.ok(service.reverse(id, request.reason()), "Đã đảo phiếu");
	}

	@PostMapping("/stock-documents/{id}/payments")
	public ApiResponse<DocumentDto> pay(@PathVariable long id, @Valid @RequestBody PaymentRequest request) {
		return ApiResponse.ok(service.addPayment(id, request), "Đã ghi thanh toán");
	}

	@GetMapping("/stock-balances")
	public ApiResponse<List<StockBalanceDto>> balances(
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "false") boolean onlyInStock) {
		return ApiResponse.ok(service.balances(q, onlyInStock), null);
	}

	@PostMapping("/stock-balances/count")
	public ApiResponse<InventoryCountResult> count(@Valid @RequestBody InventoryCountRequest request) {
		return ApiResponse.ok(service.applyPhysicalCount(request.items()), "Đã lưu kiểm kê và điều chỉnh tồn kho");
	}

	@GetMapping("/stock-movements")
	public ApiResponse<List<MovementDto>> movements(@RequestParam long productId) {
		return ApiResponse.ok(service.movements(productId), null);
	}
}
