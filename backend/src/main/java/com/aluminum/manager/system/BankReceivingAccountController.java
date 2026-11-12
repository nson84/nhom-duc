package com.aluminum.manager.system;

import com.aluminum.manager.common.ApiResponse;
import com.aluminum.manager.system.BankReceivingAccountDtos.BankAccountDto;
import com.aluminum.manager.system.BankReceivingAccountDtos.BankAccountRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bank-accounts")
public class BankReceivingAccountController {

	private final BankReceivingAccountService service;

	public BankReceivingAccountController(BankReceivingAccountService service) {
		this.service = service;
	}

	@GetMapping
	public ApiResponse<List<BankAccountDto>> list() {
		return ApiResponse.ok(service.list(), null);
	}

	@GetMapping("/default")
	public ApiResponse<BankAccountDto> getDefault() {
		return ApiResponse.ok(service.getDefault(), null);
	}

	@PostMapping
	public ApiResponse<BankAccountDto> create(@Valid @RequestBody BankAccountRequest request) {
		return ApiResponse.ok(service.create(request), "Đã thêm tài khoản QR");
	}

	@PutMapping("/{id}")
	public ApiResponse<BankAccountDto> update(@PathVariable long id, @Valid @RequestBody BankAccountRequest request) {
		return ApiResponse.ok(service.update(id, request), "Đã lưu tài khoản QR");
	}

	@PostMapping("/{id}/default")
	public ApiResponse<BankAccountDto> setDefault(@PathVariable long id) {
		return ApiResponse.ok(service.setDefault(id), "Đã đặt làm tài khoản mặc định");
	}

	@DeleteMapping("/{id}")
	public ApiResponse<Void> delete(@PathVariable long id) {
		service.delete(id);
		return ApiResponse.ok(null, "Đã xóa tài khoản QR");
	}
}
