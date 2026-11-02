package com.aluminum.manager.common;

/**
 * Quy tắc tên dùng chung: nếu có nội dung thì không được bắt đầu bằng số
 * (vd. {@code 10c}, {@code -1}, {@code +2}) — áp dụng nhà cung cấp, khách,
 * người giao/nhập/xuất, họ tên…
 */
public final class NameValidation {

	private NameValidation() {
	}

	public static void requireNoLeadingDigit(String fieldLabel, String value) {
		if (value == null || value.isBlank()) {
			return;
		}
		String trimmed = value.trim();
		char first = trimmed.charAt(0);
		boolean signed = (first == '-' || first == '+') && trimmed.length() > 1;
		char numberStart = signed ? trimmed.charAt(1) : first;
		if (Character.isDigit(numberStart)) {
			throw new BusinessException(
					ErrorCode.VALIDATION_ERROR,
					fieldLabel + " không được bắt đầu bằng số",
					null);
		}
	}
}
