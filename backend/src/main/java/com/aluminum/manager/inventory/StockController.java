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
import java.util.ArrayList;
import java.util.List;
import com.aluminum.manager.shop.ShopAccess;
import org.springframework.jdbc.core.JdbcTemplate;
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
	private final JdbcTemplate jdbc;
	private final ShopAccess shops;


	public StockController(StockDocumentService service, JdbcTemplate jdbc, ShopAccess shops) {
		this.service = service;
		this.jdbc = jdbc;
		this.shops = shops;
	}

	public record DeliveredByCategory(String categoryName, java.math.BigDecimal quantityKg) {}

	@GetMapping("/stock-documents/delivered-by-category")
	public ApiResponse<List<DeliveredByCategory>> deliveredByCategory(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) List<Long> customerIds) {
		StringBuilder sql = new StringBuilder("""
				SELECT c.name AS category_name, COALESCE(SUM(i.quantity_base), 0) AS quantity_kg
				FROM stock_document_items i
				JOIN stock_documents d ON d.id = i.document_id
				JOIN product_categories c ON c.id = i.category_id
				WHERE d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND d.reversal_of_document_id IS NULL
				  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
				""");
		List<Object> args = new ArrayList<>();
		if (from != null) { sql.append(" AND d.document_date >= ?"); args.add(from.atStartOfDay()); }
		if (to != null) { sql.append(" AND d.document_date < ?"); args.add(to.plusDays(1).atStartOfDay()); }
		if (categoryId != null) { sql.append(" AND i.category_id = ?"); args.add(categoryId); }
		if (customerIds != null) {
			if (customerIds.isEmpty()) return ApiResponse.ok(List.of(), null);
			sql.append(" AND d.customer_id IN (");
			for (int i = 0; i < customerIds.size(); i++) sql.append(i == 0 ? "?" : ",?");
			sql.append(")");
			args.addAll(customerIds);
		}
		sql.append(shops.shopOnly("d"));
		sql.append(" GROUP BY c.name ORDER BY c.name");
		return ApiResponse.ok(jdbc.query(sql.toString(), (rs, rowNum) ->
				new DeliveredByCategory(rs.getString("category_name"), rs.getBigDecimal("quantity_kg")), args.toArray()), null);
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
		return ApiResponse.ok(service.getForView(id), null);
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

	@PutMapping("/stock-documents/{id}/confirmed")
	public ApiResponse<DocumentDto> editConfirmed(@PathVariable long id, @Valid @RequestBody DocumentRequest request) {
		return ApiResponse.ok(service.editConfirmed(id, request), "Đã sửa phiếu");
	}

	@GetMapping("/stock-balances")
	public ApiResponse<List<StockBalanceDto>> balances(
			@RequestParam(required = false) String q,
			@RequestParam(defaultValue = "false") boolean onlyInStock) {
		return ApiResponse.ok(service.balances(q, onlyInStock), null);
	}

	@PostMapping("/stock-balances/count")
	public ApiResponse<InventoryCountResult> count(@Valid @RequestBody InventoryCountRequest request) {
		return ApiResponse.ok(service.applyPhysicalCount(request.items(), request.countDate()), "Đã lưu kiểm kê và điều chỉnh tồn kho");
	}

	@GetMapping("/stock-movements")
	public ApiResponse<List<MovementDto>> movements(@RequestParam long productId) {
		return ApiResponse.ok(service.movements(productId), null);
	}
}
