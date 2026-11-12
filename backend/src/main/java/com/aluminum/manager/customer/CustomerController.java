package com.aluminum.manager.customer;

import com.aluminum.manager.common.ApiResponse;
import com.aluminum.manager.customer.CustomerDtos.CustomerDocumentRow;
import com.aluminum.manager.customer.CustomerDtos.CustomerDto;
import com.aluminum.manager.customer.CustomerDtos.CustomerRequest;
import com.aluminum.manager.customer.CustomerDtos.CustomerSummary;
import com.aluminum.manager.customer.CustomerDtos.DueRemindersResponse;
import com.aluminum.manager.customer.CustomerDtos.LinkCustomerRequest;
import com.aluminum.manager.customer.CustomerDtos.ReminderDto;
import com.aluminum.manager.customer.CustomerDtos.ReminderRequest;
import com.aluminum.manager.inventory.StockDocumentService;
import com.aluminum.manager.inventory.StockDtos.DocumentDto;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
public class CustomerController {

	private final CustomerService customers;
	private final StockDocumentService documents;

	public CustomerController(CustomerService customers, StockDocumentService documents) {
		this.customers = customers;
		this.documents = documents;
	}

	@GetMapping("/customers")
	public ApiResponse<List<CustomerDto>> list(
			@RequestParam(required = false) String q,
			@RequestParam(required = false) Boolean active,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
		return ApiResponse.ok(customers.list(q, active, from, to), null);
	}

	@GetMapping("/customers/due-reminders")
	public ApiResponse<DueRemindersResponse> dueReminders() {
		return ApiResponse.ok(new DueRemindersResponse(customers.dueReminders()), null);
	}

	@GetMapping("/customers/{id}")
	public ApiResponse<CustomerSummary> get(@PathVariable long id) {
		return ApiResponse.ok(customers.get(id), null);
	}

	@PostMapping("/customers")
	public ApiResponse<CustomerDto> create(@Valid @RequestBody CustomerRequest request) {
		return ApiResponse.ok(customers.create(request), "Đã thêm khách hàng");
	}

	@PutMapping("/customers/{id}")
	public ApiResponse<CustomerDto> update(@PathVariable long id, @Valid @RequestBody CustomerRequest request) {
		return ApiResponse.ok(customers.update(id, request), "Đã lưu khách hàng");
	}

	@GetMapping("/customers/{id}/documents")
	public ApiResponse<List<CustomerDocumentRow>> documents(
			@PathVariable long id,
			@RequestParam(required = false) String paymentStatus) {
		return ApiResponse.ok(customers.documents(id, paymentStatus), null);
	}

	@GetMapping("/customers/{id}/reminders")
	public ApiResponse<List<ReminderDto>> reminders(@PathVariable long id) {
		return ApiResponse.ok(customers.reminders(id), null);
	}

	@PostMapping("/customers/{id}/reminders")
	public ApiResponse<ReminderDto> createReminder(
			@PathVariable long id, @Valid @RequestBody ReminderRequest request) {
		return ApiResponse.ok(customers.createReminder(id, request), "Đã tạo lịch nhắc");
	}

	@PutMapping("/customers/{id}/reminders/{reminderId}")
	public ApiResponse<ReminderDto> updateReminder(
			@PathVariable long id,
			@PathVariable long reminderId,
			@RequestBody Map<String, Boolean> body) {
		boolean done = Boolean.TRUE.equals(body.get("done"));
		return ApiResponse.ok(customers.markReminderDone(id, reminderId, done),
				done ? "Đã đánh dấu xong" : "Đã mở lại nhắc");
	}

	@PutMapping("/stock-documents/{id}/customer")
	public ApiResponse<DocumentDto> linkCustomer(
			@PathVariable long id, @RequestBody LinkCustomerRequest request) {
		customers.linkDocument(id, request.customerId());
		return ApiResponse.ok(documents.get(id), "Đã gắn khách hàng");
	}
}
