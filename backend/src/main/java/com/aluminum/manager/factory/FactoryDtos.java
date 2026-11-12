package com.aluminum.manager.factory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class FactoryDtos {
	private FactoryDtos() {
	}

	public record ScrapSaleRequest(
			@NotNull LocalDateTime saleDate,
			@NotBlank @Size(max = 200) String buyerName,
			@NotBlank @Size(max = 150) String sellerName,
			@NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantityKg,
			@NotNull @DecimalMin("0") BigDecimal unitPrice,
			@Size(max = 500) String notes,
			@Size(max = 80) String idempotencyKey) {
	}

	public record ScrapPaymentRequest(
			@NotNull LocalDateTime paymentDate,
			@NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
			@NotBlank @Size(max = 30) String paymentMethod,
			@NotBlank @Size(max = 80) String collectedBy,
			@Size(max = 80) String idempotencyKey) {
	}

	public record ScrapPaymentDto(long id, LocalDateTime paymentDate, BigDecimal amount,
			String paymentMethod, String collectedBy) {
	}

	public record ScrapSaleDto(long id, LocalDateTime saleDate, String buyerName,
			String sellerName, BigDecimal quantityKg, BigDecimal unitPrice,
			BigDecimal totalAmount, BigDecimal paidAmount, String status,
			String notes, List<ScrapPaymentDto> payments) {
	}

	public record YearCountRequest(
			LocalDate periodFrom,
			LocalDate periodTo,
			@NotNull @DecimalMin("0") BigDecimal openingKg,
			@NotNull @DecimalMin("0") BigDecimal openingValue,
			@NotNull @DecimalMin("0") BigDecimal rawKg,
			@NotNull @DecimalMin("0") BigDecimal rawValue,
			@NotNull @DecimalMin("0") BigDecimal scrapKg,
			@NotNull @DecimalMin("0") BigDecimal scrapValue,
			@NotNull @DecimalMin("0") BigDecimal workInProgressKg,
			@NotNull @DecimalMin("0") BigDecimal workInProgressValue,
			@NotNull @DecimalMin("0") BigDecimal finishedKg,
			@NotNull @DecimalMin("0") BigDecimal finishedValue,
			@Size(max = 500) String notes) {
	}

	public record YearCountDto(int year, LocalDate periodFrom, LocalDate periodTo, BigDecimal openingKg, BigDecimal openingValue,
			BigDecimal rawKg, BigDecimal rawValue, BigDecimal scrapKg, BigDecimal scrapValue,
			BigDecimal workInProgressKg, BigDecimal workInProgressValue,
			BigDecimal finishedKg, BigDecimal finishedValue,
			String status, String notes, LocalDateTime closedAt) {
	}

	public record RawCountInput(long categoryId, @NotNull @DecimalMin("0") BigDecimal quantityKg) {
	}

	public record RawCountRequest(@NotNull List<@Valid RawCountInput> items) {
	}

	public record InventorySaveRequest(@NotNull @Valid YearCountRequest count,
			@NotNull List<@Valid RawCountInput> items) { }

	public record RawLayerDto(long purchaseItemId, String documentCode, LocalDateTime documentDate,
			BigDecimal quantityKg, BigDecimal unitCostPerKg, BigDecimal valueAmount) {
	}

	public record RawCountDto(long categoryId, String categoryName, BigDecimal quantityKg,
			BigDecimal valueAmount, List<RawLayerDto> layers) {
	}

	public record PurchaseCostOption(long purchaseItemId, long categoryId, String categoryName,
			String documentCode, LocalDateTime documentDate, BigDecimal quantityKg,
			BigDecimal costPerKg) {
	}

	public record SaleCostRow(long itemId, long documentId, String documentCode, LocalDateTime documentDate,
			String customerName, String productName, long categoryId, String categoryName, BigDecimal quantityKg,
			BigDecimal referenceCostPerKg, BigDecimal referenceValue, BigDecimal salesAmount,
			BigDecimal quantityDocument, String unitName, BigDecimal unitPrice) {
	}

	public record SaleCostReport(BigDecimal quantityKg, BigDecimal referenceValue,
			BigDecimal salesAmount, List<SaleCostRow> rows) {
	}

	public record YearReportDto(int year, String countStatus, BigDecimal openingKg,
			BigDecimal purchasedKg, BigDecimal finishedSoldKg, BigDecimal scrapSoldKg,
			BigDecimal closingKg, BigDecimal initialDiscrepancyKg, BigDecimal estimatedLossKg,
			BigDecimal openingValue, BigDecimal purchaseValue, BigDecimal closingValue,
			BigDecimal materialCost, BigDecimal finishedRevenue, BigDecimal scrapRevenue,
			BigDecimal payrollCost, BigDecimal otherExpenses, BigDecimal estimatedProfit,
			BigDecimal finishedCashCollected, BigDecimal scrapCashCollected) {
	}

	public record CustomerYearRow(long documentId, String documentCode,
			LocalDateTime documentDate, String productName, String unitName, BigDecimal quantityKg,
			BigDecimal unitPrice, BigDecimal lineTotal, String sellerName,
			BigDecimal documentTotal, BigDecimal documentPaid) {
	}

	public record CustomerYearDto(long customerId, String customerName, int year,
			BigDecimal quantityKg, BigDecimal revenue, BigDecimal paidAmount,
			BigDecimal outstandingAmount, int documentCount, List<CustomerYearRow> rows) {
	}

	public record SettlementInvoiceRow(long documentId, String documentCode,
			LocalDateTime documentDate, String products, String sellerName,
			BigDecimal quantityKg, BigDecimal totalAmount, BigDecimal paidAsOf,
			BigDecimal remainingAsOf) {
	}

	public record CustomerSettlementRow(long customerId, String customerName,
			BigDecimal openingDebt, BigDecimal periodSales, BigDecimal periodReceipts,
			BigDecimal closingDebt, List<SettlementInvoiceRow> invoices) {
	}

	public record SettlementReportDto(int year, Integer month, LocalDateTime from,
			LocalDateTime toExclusive, BigDecimal totalOpeningDebt,
			BigDecimal totalPeriodSales, BigDecimal totalPeriodReceipts,
			BigDecimal totalClosingDebt, int unlinkedSalesCount,
			BigDecimal unlinkedSalesValue, List<CustomerSettlementRow> customers) {
	}
}
