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
			@NotNull(message = "Chọn đơn vị") Long unitId,
			@NotNull(message = "Nhập số lượng")
			@DecimalMin(value = "0", inclusive = false, message = "Số lượng phải lớn hơn 0") BigDecimal quantityDocument,
			@NotNull(message = "Nhập đơn giá")
			@DecimalMin(value = "0", message = "Đơn giá không được âm") BigDecimal unitPrice,
			@DecimalMin(value = "0", message = "Chiết khấu không được âm") BigDecimal lineDiscount
	) {
	}

	public record DocumentRequest(
			@NotBlank(message = "Chọn loại phiếu") String documentType,
			@NotNull(message = "Chọn ngày phiếu") LocalDateTime documentDate,
			@Size(max = 200) String counterpartyName,
			@Size(max = 150) String issuedBy,
			@Size(max = 50, message = "Người nhập tối đa 50 ký tự") String receivedBy,
			@Size(max = 500) String reason,
			@DecimalMin(value = "0", message = "Chiết khấu không được âm") BigDecimal discountAmount,
			@Size(max = 30) String paymentMethod,
			String notes,
			@Size(max = 80) String idempotencyKey,
			@NotEmpty(message = "Phiếu cần ít nhất một dòng hàng") List<@Valid ItemRequest> items,
			Boolean internal
	) {
	}

	public record ReverseRequest(@NotBlank(message = "Nhập lý do đảo phiếu") @Size(max = 500) String reason) {
	}

	public record PaymentRequest(
			@NotNull(message = "Nhập số tiền")
			@DecimalMin(value = "0", inclusive = false, message = "Số tiền phải lớn hơn 0") BigDecimal amount,
			@NotBlank(message = "Chọn hình thức thanh toán") @Size(max = 30) String paymentMethod,
			LocalDateTime paymentDate,
			@Size(max = 500) String note,
			@Size(max = 80) String idempotencyKey
	) {
	}

	public record ItemDto(
			Long id,
			Long productId,
			Long unitId,
			String productCode,
			String productName,
			String unitName,
			BigDecimal quantityDocument,
			BigDecimal factorToBase,
			BigDecimal quantityBase,
			BigDecimal unitPrice,
			BigDecimal lineDiscount,
			BigDecimal lineTotal,
			BigDecimal costPerBase,
			BigDecimal costTotal,
			/** Giá vốn thực tế luôn có; giá chuẩn có thể null khi sản phẩm chưa có lịch sử giá. */
			Long purchasePriceId,
			BigDecimal standardCostPerBase,
			BigDecimal standardCostTotal
	) {
	}

	public record PaymentDto(
			Long id,
			LocalDateTime paymentDate,
			BigDecimal amount,
			String paymentMethod,
			String note
	) {
	}

	public record DocumentDto(
			Long id,
			String documentCode,
			String documentType,
			String status,
			LocalDateTime documentDate,
			String counterpartyName,
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
			String internalPairDocumentCode
	) {
	}

	public record DocumentSummary(
			Long id,
			String documentCode,
			String documentType,
			String status,
			LocalDateTime documentDate,
			String counterpartyName,
			BigDecimal totalAmount,
			String paymentStatus,
			boolean internal,
			Long internalPairDocumentId,
			String internalPairDocumentCode
	) {
	}

	public record StockBalanceDto(
			Long productId,
			String productCode,
			String productName,
			String categoryName,
			String baseUnitName,
			BigDecimal quantityBase,
			BigDecimal averageCostPerBase,
			BigDecimal stockValue
	) {
	}

	public record MovementDto(
			Long id,
			LocalDateTime movementAt,
			String documentCode,
			String movementType,
			BigDecimal quantityDeltaBase,
			BigDecimal unitCost
	) {
	}
}
