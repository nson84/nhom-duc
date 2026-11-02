package com.aluminum.manager.system;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ShopSettings(
		@NotBlank(message = "Nhập tên cửa hàng")
		@Size(max = 200, message = "Tên cửa hàng quá dài")
		String shopName,
		@Size(max = 300, message = "Địa chỉ quá dài")
		String shopAddress,
		@Size(max = 30, message = "Số điện thoại quá dài")
		String shopPhone,
		@NotBlank(message = "Nhập giờ sao lưu")
		@Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Giờ sao lưu phải theo dạng HH:mm")
		String backupTime,
		@NotNull(message = "Nhập số ngày giữ bản sao lưu")
		@Min(value = 1, message = "Giữ bản sao lưu ít nhất 1 ngày")
		@Max(value = 3650, message = "Số ngày giữ bản sao lưu quá lớn")
		Integer backupRetentionDays,
		@Size(max = 1000, message = "Đường dẫn thư mục sao lưu quá dài")
		String backupDirectory
) {
}
