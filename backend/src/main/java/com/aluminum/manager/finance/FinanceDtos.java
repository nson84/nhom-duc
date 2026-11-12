package com.aluminum.manager.finance;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class FinanceDtos {

	private FinanceDtos() {
	}

	public record TransactionDto(
			Long id,
			String transactionCode,
			LocalDateTime transactionDate,
			String transactionType,
			String category,
			BigDecimal amount,
			String description,
			String spentBy,
			String referenceType,
			Long referenceId,
			String status,
			LocalDateTime createdAt,
			boolean internal,
			String shopCode
	) {
	}

	public record CreateTransactionRequest(
			LocalDateTime transactionDate,
			@NotBlank(message = "Chọn loại thu/chi") String transactionType,
			@NotBlank(message = "Chọn nhóm giao dịch") String category,
			@NotNull(message = "Nhập số tiền")
			@DecimalMin(value = "0", inclusive = false, message = "Số tiền phải lớn hơn 0") BigDecimal amount,
			@Size(max = 500) String description,
			@Size(max = 150) String spentBy,
			@Size(max = 80) String idempotencyKey
	) {
	}

	public record VoidTransactionRequest(
			@NotBlank(message = "Nhập lý do hủy phiếu thu/chi") @Size(max = 500) String reason
	) {
	}

	public record FinanceSummaryDto(
			LocalDate from,
			LocalDate to,
			BigDecimal totalIncome,
			BigDecimal saleReceiptIncome,
			BigDecimal otherIncome,
			BigDecimal totalExpense,
			BigDecimal operatingExpense,
			BigDecimal payrollExpense,
			BigDecimal otherExpense,
			BigDecimal purchasePaymentOutflow,
			BigDecimal netCashflow,
			BigDecimal internalIncome,
			BigDecimal internalExpense
	) {
	}

	public record DailyRevenueRowDto(
			LocalDate day,
			int saleCount,
			BigDecimal revenue,
			BigDecimal cogs,
			BigDecimal grossProfit,
			BigDecimal cashCollected,
			BigDecimal operatingExpense,
			BigDecimal standardCogs,
			BigDecimal standardGrossProfit,
			/** Giá vốn thực tế − giá vốn chuẩn. Dương: nhập đắt hơn giá chuẩn. */
			BigDecimal priceVariance
	) {
	}

	public record ProfitReportDto(
			LocalDate from,
			LocalDate to,
			BigDecimal revenue,
			BigDecimal cogs,
			BigDecimal grossProfit,
			BigDecimal operatingExpenses,
			BigDecimal netProfit,
			BigDecimal cashCollected,
			BigDecimal uncollectedRevenue,
			BigDecimal purchaseStockValue,
			BigDecimal purchasePaymentOutflow,
			/** Giá vốn theo giá nhập chuẩn của đúng ngày phiếu. */
			BigDecimal standardCogs,
			BigDecimal standardGrossProfit,
			BigDecimal standardNetProfit,
			/** Giá vốn thực tế − giá vốn chuẩn. Dương: nhập đắt hơn giá chuẩn nên lãi thực thấp hơn lãi chuẩn. */
			BigDecimal priceVariance,
			/** False khi còn dòng bán chưa xác định được giá chuẩn (chưa có lịch sử giá nhập). */
			boolean standardCostComplete,
			List<DailyRevenueRowDto> dailyRows
	) {
	}

	/** Lãi gộp tham khảo: giá vốn loại nhôm theo ngày bán, không gồm công và hao hụt. */
	public record InvoiceGrossMarginDto(long documentId, String documentCode, LocalDate documentDate,
			String customerName, BigDecimal revenue, BigDecimal estimatedMaterialCost,
			BigDecimal estimatedGrossProfit, boolean costComplete) {}

	public record ProductSalesRowDto(
			Long productId,
			String productCode,
			String productName,
			String categoryName,
			String baseUnitName,
			BigDecimal quantityBaseSold,
			BigDecimal revenue,
			BigDecimal cogs,
			BigDecimal grossProfit,
			BigDecimal standardCogs,
			BigDecimal standardGrossProfit,
			BigDecimal priceVariance
	) {
	}

	/** Một mốc giá nhập kèm số lượng đã nhập thực tế trong kỳ để soát chênh lệch. */
	public record PurchasePriceVarianceRowDto(
			Long productId,
			String productCode,
			String productName,
			String categoryName,
			String baseUnitName,
			/** Ngày hiệu lực của mốc giá chuẩn đã dùng. Null khi dòng phiếu không còn giữ liên kết lịch sử. */
			LocalDate effectiveFrom,
			BigDecimal standardPricePerBase,
			BigDecimal actualQuantityBase,
			BigDecimal actualPurchaseValue,
			BigDecimal actualAverageCostPerBase,
			BigDecimal varianceValue,
			BigDecimal variancePercent
	) {
	}

	public record InventoryReportRowDto(
			Long productId,
			String productCode,
			String productName,
			String categoryName,
			String baseUnitName,
			BigDecimal openingQuantityBase,
			BigDecimal inQuantityBase,
			BigDecimal outQuantityBase,
			BigDecimal closingQuantityBase,
			BigDecimal averageCostPerBase,
			BigDecimal inventoryValue
	) {
	}

	public record PayrollReportRowDto(
			Long periodId,
			int year,
			int month,
			String status,
			int employeeCount,
			BigDecimal totalCreditedDays,
			BigDecimal totalBasePay,
			BigDecimal totalOvertimePay,
			BigDecimal totalAllowances,
			BigDecimal totalDeductions,
			BigDecimal grandTotalPay,
			BigDecimal paidAmount
	) {
	}
}
