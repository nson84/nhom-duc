package com.aluminum.manager.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

public final class LandingDtos {

	private LandingDtos() {
	}

	/** Dữ liệu khách gửi từ landing page. Không nhận id, status hay ghi chú nội bộ. */
	public record LeadRequest(
			@NotBlank(message = "Nhập họ tên")
			@Size(max = 150, message = "Họ tên tối đa 150 ký tự")
			String fullName,

			@NotBlank(message = "Nhập số điện thoại")
			@Pattern(regexp = "^(0|\\+84)[0-9]{8,10}$",
					message = "Số điện thoại chưa đúng, ví dụ 0912345678")
			String phone,

			@Email(message = "Email chưa đúng định dạng")
			@Size(max = 150, message = "Email tối đa 150 ký tự")
			String email,

			@Size(max = 1000, message = "Nội dung tối đa 1000 ký tự")
			String message,

			@Size(max = 30) String requestType,

			/** Trường bẫy spam: người thật để trống, bot thường điền. */
			String website,

			@Size(max = 200) String sourcePage
	) {
	}

	public record LeadCreatedResponse(Long id) {
	}

	public record LeadDto(
			Long id,
			String fullName,
			String phone,
			String email,
			String message,
			String requestType,
			String status,
			String internalNote,
			LocalDateTime contactedAt,
			String contactedBy,
			String sourcePage,
			LocalDateTime createdAt
	) {
	}

	public record LeadUpdateRequest(
			@NotBlank(message = "Chọn trạng thái") String status,
			@Size(max = 1000) String internalNote
	) {
	}

	/** Số liệu gọn cho màn hình danh sách khách để lại thông tin. */
	public record LeadSummary(
			long newCount,
			long contactedCount,
			long closedCount,
			long todayCount
	) {
	}
}
