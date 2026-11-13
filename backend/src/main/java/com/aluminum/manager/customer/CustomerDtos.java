package com.aluminum.manager.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class CustomerDtos {

	private CustomerDtos() {
	}

	public record CustomerRequest(
			@NotBlank(message = "Nhập tên khách hàng") @Size(max = 200) String name,
			@Size(max = 30) String phone,
			@Size(max = 500) String note,
			Boolean active
	) {
	}

	public record CustomerDto(
			Long id,
			String name,
			String phone,
			String note,
			boolean active,
			String shopCode,
			String shopName,
			BigDecimal totalSales,
			BigDecimal outstandingDebt,
			int openInvoiceCount,
			BigDecimal receivedAmount,
			BigDecimal deliveredKg
	) {
	}

	public record CustomerSummary(
			Long id,
			String name,
			String phone,
			String note,
			boolean active,
			String shopCode,
			String shopName,
			BigDecimal totalSales,
			BigDecimal outstandingDebt,
			int saleCount,
			int openInvoiceCount,
			BigDecimal receivedAmount,
			BigDecimal deliveredKg
	) {
	}

	public record CustomerDocumentRow(
			Long id,
			String documentCode,
			LocalDateTime documentDate,
			String status,
			BigDecimal totalAmount,
			BigDecimal paidAmount,
			BigDecimal remainingAmount,
			String paymentStatus
	) {
	}

	public record ReminderRequest(
			@NotNull(message = "Chọn ngày nhắc") LocalDate remindOn,
			Long documentId,
			@Size(max = 500) String note
	) {
	}

	public record ReminderDto(
			Long id,
			Long customerId,
			String customerName,
			Long documentId,
			String documentCode,
			LocalDate remindOn,
			String note,
			boolean done,
			String shopCode,
			String shopName
	) {
	}

	public record LinkCustomerRequest(Long customerId) {
	}

	public record DueRemindersResponse(List<ReminderDto> items) {
	}
}
