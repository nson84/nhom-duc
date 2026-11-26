package com.aluminum.manager.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class StockDtos {

	private StockDtos() {
	}

	public record ItemRequest(
			@NotNull(message = "Chọn sản phẩm") Long productId,
			@NotNull(message = "Chọn loại nhôm") Long categoryId,
			@NotNull(message = "Chọn đơn vị") Long unitId,
			@NotNull(message = "Nhập số lượng")
			@DecimalMin(value = "0", inclusive = false, message = "Số lượng phải lớn hơn 0") BigDecimal quantityDocument,
			@NotNull(message = "Nhập đơn giá")
			@DecimalMin(value = "0", message = "Đơn giá không được âm") BigDecimal unitPrice,
			@DecimalMin(value = "0", message = "Chiết khấu không được âm") BigDecimal lineDiscount,
			/** Phiếu bán: kg | m2 — quyết định cách tính tiền trên phiếu. */
			@Size(max = 10) String billingUnit,
			@DecimalMin(value = "0", inclusive = false, message = "Chiều dài phải lớn hơn 0") BigDecimal widthM,
			@DecimalMin(value = "0", inclusive = false, message = "Chiều rộng phải lớn hơn 0") BigDecimal heightM,
			/** Bán m²: khối lượng thực tế (kg) để trừ tồn — tách với diện tích tính tiền. */
			@DecimalMin(value = "0", inclusive = false, message = "Khối lượng trừ tồn phải lớn hơn 0") BigDecimal stockQuantityKg,
			Long factoryPurchaseItemId,
			@DecimalMin(value = "0", inclusive = false)
			@jakarta.validation.constraints.Digits(integer = 12, fraction = 6) BigDecimal enteredCostPerKg
	) {
		public ItemRequest(Long productId, Long categoryId, Long unitId, BigDecimal quantityDocument,
				BigDecimal unitPrice, BigDecimal lineDiscount, String billingUnit, BigDecimal widthM,
				BigDecimal heightM, BigDecimal stockQuantityKg, Long factoryPurchaseItemId) {
			this(productId, categoryId, unitId, quantityDocument, unitPrice, lineDiscount, billingUnit,
					widthM, heightM, stockQuantityKg, factoryPurchaseItemId, null);
		}
	}

	public record DocumentRequest(
			@NotBlank(message = "Chọn loại phiếu") String documentType,
			@NotNull(message = "Chọn ngày phiếu") LocalDateTime documentDate,
			@Size(max = 200) String counterpartyName,
			Long customerId,
			@Size(max = 150) String issuedBy,
			@Size(max = 50, message = "Người nhập tối đa 50 ký tự") String receivedBy,
			@Size(max = 500) String reason,
			@DecimalMin(value = "0", message = "Chiết khấu không được âm") BigDecimal discountAmount,
			@Size(max = 30) String paymentMethod,
			String notes,
			@Size(max = 80) String idempotencyKey,
			@NotEmpty(message = "Phiếu cần ít nhất một dòng hàng") List<@Valid ItemRequest> items,
			Boolean internal,
			Boolean paperSource
	) {
		public DocumentRequest(String documentType, LocalDateTime documentDate, String counterpartyName,
				Long customerId, String issuedBy, String receivedBy, String reason, BigDecimal discountAmount,
				String paymentMethod, String notes, String idempotencyKey, List<ItemRequest> items, Boolean internal) {
			this(documentType, documentDate, counterpartyName, customerId, issuedBy, receivedBy, reason,
					discountAmount, paymentMethod, notes, idempotencyKey, items, internal, null);
		}
	}

	public record ReverseRequest(@NotBlank(message = "Nhập lý do đảo phiếu") @Size(max = 500) String reason) {
	}

	public record PaymentRequest(
			@NotNull(message = "Nhập số tiền")
			@DecimalMin(value = "0", inclusive = false, message = "Số tiền phải lớn hơn 0") BigDecimal amount,
			@NotBlank(message = "Chọn hình thức thanh toán") @Size(max = 30) String paymentMethod,
			LocalDateTime paymentDate,
			@Size(max = 500) String note,
			@Size(max = 80) String idempotencyKey,
			@Size(max = 80, message = "Người thu tối đa 80 ký tự") String collectedBy,
			@Size(max = 80) String paidBy,
			Boolean confirmLockedCarryoverMonth
	) {
		public PaymentRequest(BigDecimal amount, String paymentMethod, LocalDateTime paymentDate, String note,
				String idempotencyKey, String collectedBy, String paidBy) {
			this(amount, paymentMethod, paymentDate, note, idempotencyKey, collectedBy, paidBy, false);
		}
	}

	public record BatchCollectionLine(
			@NotNull Long documentId,
			@NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount
	) {}

	public record BatchCollectionRequest(
			@NotNull Long customerId,
			@NotNull LocalDateTime paymentDate,
			@NotBlank @Size(max = 30) String paymentMethod,
			@NotBlank @Size(max = 80) String collectedBy,
			@NotNull java.util.UUID receiptId,
			@NotEmpty @Size(max = 500) List<@NotNull @Valid BatchCollectionLine> invoices
	) {}

	public record BatchCollectionItem(long documentId, String documentCode, LocalDateTime documentDate, BigDecimal amount) {}


	public record ItemDto(
			Long id,
			Long productId,
			Long categoryId,
			Long unitId,
			String productCode,
			String productName,
			String categoryName,
			String unitName,
			String billingUnit,
			BigDecimal widthM,
			BigDecimal heightM,
			BigDecimal quantityDocument,
			BigDecimal factorToBase,
			BigDecimal quantityBase,
			BigDecimal unitPrice,
			BigDecimal lineDiscount,
			BigDecimal lineTotal,
			BigDecimal costPerBase,
			BigDecimal costTotal,
			/** Giá vốn thực tế luôn có; giá chuẩn có thể null khi loại chưa có lịch sử giá. */
			Long purchasePriceId,
			BigDecimal standardCostPerBase,
			BigDecimal standardCostTotal,
			Long factoryPurchaseItemId,
			BigDecimal factoryReferenceCostPerKg,
			BigDecimal enteredCostPerKg
	) {
	}

	public record PaymentDto(
			Long id,
			LocalDateTime paymentDate,
			BigDecimal amount,
			String paymentMethod,
			String note,
			/** Tài khoản đang đăng nhập lúc ghi thu. Null với phiếu chi hoặc lần thu cũ không ghép được nhật ký. */
			String collectedBy
	) {
	}

	public record DocumentDto(
			Long id,
			String documentCode,
			String documentType,
			String status,
			LocalDateTime documentDate,
			String counterpartyName,
			Long customerId,
			String issuedBy,
			String receivedBy,
			String reason,
			BigDecimal subtotal,
			BigDecimal discountAmount,
			BigDecimal totalAmount,
			BigDecimal paidAmount,
			String paymentStatus,
			String paymentMethod,
			Long reversalOfDocumentId,
			Long reversedByDocumentId,
			LocalDateTime confirmedAt,
			String notes,
			List<ItemDto> items,
			List<PaymentDto> payments,
			boolean internal,
			Long internalPairDocumentId,
			String internalPairDocumentCode,
			LocalDateTime trashedAt,
			boolean paperSource
	) {
	}

	public record DocumentSummary(
			Long id,
			String documentCode,
			String documentType,
			String status,
			LocalDateTime documentDate,
			String counterpartyName,
			Long customerId,
			BigDecimal totalAmount,
			String paymentStatus,
			boolean internal,
			Long internalPairDocumentId,
			String internalPairDocumentCode,
			LocalDateTime trashedAt
	) {
	}

	public record StockBalanceDto(
			Long productId,
			Long categoryId,
			String productCode,
			String productName,
			String categoryName,
			String baseUnitName,
			BigDecimal quantityBase,
			BigDecimal averageCostPerBase,
			BigDecimal stockValue,
			BigDecimal purchasedKg,
			BigDecimal purchasedValue,
			BigDecimal deliveredKg,
			BigDecimal latestCountScrapKg,
			BigDecimal latestCountLossKg
	) {
	}

	public record InventoryCountItemRequest(
			@NotNull Long productId,
			@NotNull Long categoryId,
			@NotNull @DecimalMin(value = "0", message = "Số cân thực tế không được âm") BigDecimal countedQuantityKg,
			@DecimalMin(value = "0", message = "Vụn nhôm không được âm") BigDecimal scrapKg
	) {
	}

	public record InventoryCountRequest(@NotEmpty List<@Valid InventoryCountItemRequest> items,
			@NotNull LocalDateTime countDate) {
	}

	public record InventoryCountResult(int adjustedItems, BigDecimal shortageKg, BigDecimal surplusKg,
			List<String> documentCodes) {
	}

	public record MovementDto(
			Long id,
			LocalDateTime movementAt,
			String documentCode,
			boolean internal,
			String movementType,
			BigDecimal quantityDeltaBase,
			BigDecimal unitCost
	) {
	}
}
