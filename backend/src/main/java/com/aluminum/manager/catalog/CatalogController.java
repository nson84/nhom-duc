package com.aluminum.manager.catalog;

import com.aluminum.manager.catalog.CatalogDtos.CategoryDto;
import com.aluminum.manager.catalog.CatalogDtos.CategoryRequest;
import com.aluminum.manager.catalog.CatalogDtos.ProductDto;
import com.aluminum.manager.catalog.CatalogDtos.ProductRequest;
import com.aluminum.manager.catalog.CatalogDtos.PurchasePriceDto;
import com.aluminum.manager.catalog.CatalogDtos.PurchasePriceRequest;
import com.aluminum.manager.catalog.CatalogDtos.UnitRequest;
import com.aluminum.manager.common.ApiResponse;
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
public class CatalogController {

	private final CatalogService service;

	public CatalogController(CatalogService service) {
		this.service = service;
	}

	@GetMapping("/categories")
	public ApiResponse<List<CategoryDto>> categories() {
		return ApiResponse.ok(service.categories(), null);
	}

	@PostMapping("/categories")
	public ApiResponse<CategoryDto> createCategory(@Valid @RequestBody CategoryRequest request) {
		return ApiResponse.ok(service.createCategory(request), "Đã thêm loại nhôm");
	}

	@PutMapping("/categories/{id}")
	public ApiResponse<CategoryDto> updateCategory(@PathVariable long id, @Valid @RequestBody CategoryRequest request) {
		return ApiResponse.ok(service.updateCategory(id, request), "Đã lưu loại nhôm");
	}

	@GetMapping("/products")
	public ApiResponse<List<ProductDto>> products(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) Long categoryId,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
		return ApiResponse.ok(service.products(q, categoryId, active, asOf), null);
	}

	@GetMapping("/products/next-code")
	public ApiResponse<String> nextProductCode() {
		return ApiResponse.ok(service.nextProductCode(), null);
	}

	@GetMapping("/products/{id}")
	public ApiResponse<ProductDto> product(
			@PathVariable long id,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
		return ApiResponse.ok(service.product(id, asOf), null);
	}

	@PostMapping("/products")
	public ApiResponse<ProductDto> create(@Valid @RequestBody ProductRequest request) {
		return ApiResponse.ok(service.createProduct(request), "Đã thêm sản phẩm");
	}

	@PutMapping("/products/{id}")
	public ApiResponse<ProductDto> update(@PathVariable long id, @Valid @RequestBody ProductRequest request) {
		return ApiResponse.ok(service.updateProduct(id, request), "Đã lưu sản phẩm");
	}

	@PostMapping("/products/{id}/units")
	public ApiResponse<ProductDto> addUnit(@PathVariable long id, @Valid @RequestBody UnitRequest request) {
		return ApiResponse.ok(service.addUnit(id, request), "Đã thêm đơn vị");
	}

	@PutMapping("/products/{id}/units/{unitId}")
	public ApiResponse<ProductDto> updateUnit(
			@PathVariable long id, @PathVariable long unitId, @Valid @RequestBody UnitRequest request) {
		return ApiResponse.ok(service.updateUnit(id, unitId, request), "Đã lưu đơn vị");
	}

	@GetMapping("/products/{id}/purchase-prices")
	public ApiResponse<List<PurchasePriceDto>> purchasePrices(@PathVariable long id) {
		return ApiResponse.ok(service.purchasePriceHistory(id), null);
	}

	@PostMapping("/products/{id}/purchase-prices")
	public ApiResponse<List<PurchasePriceDto>> recordPurchasePrice(
			@PathVariable long id, @Valid @RequestBody PurchasePriceRequest request) {
		return ApiResponse.ok(service.recordPurchasePrice(id, request), "Đã ghi giá nhập mới");
	}
}
