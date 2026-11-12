package com.aluminum.manager.system;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class BankReceivingAccountDtos {
	private BankReceivingAccountDtos() {
	}

	public record BankAccountDto(
			long id,
			String label,
			String bankBin,
			String accountNo,
			String accountName,
			String imagePath,
			boolean isDefault,
			int sortOrder) {
	}

	public record BankAccountRequest(
			@NotBlank(message = "Nhập tên gợi nhớ tài khoản")
			@Size(max = 100, message = "Tên gợi nhớ quá dài")
			String label,
			@NotBlank(message = "Chọn ngân hàng")
			@Pattern(regexp = "^\\d{6}$", message = "Mã ngân hàng gồm 6 chữ số")
			String bankBin,
			@NotBlank(message = "Nhập số tài khoản")
			@Pattern(regexp = "^[0-9A-Za-z]{4,19}$", message = "Số tài khoản chỉ gồm chữ và số, 4 đến 19 ký tự")
			String accountNo,
			@Size(max = 100, message = "Tên chủ tài khoản quá dài")
			String accountName,
			@Size(max = 2_000_000, message = "Ảnh QR vượt quá dung lượng cho phép")
			String imagePath,
			Boolean isDefault) {
	}
}
