package com.aluminum.manager.common;

public record ApiResponse<T>(
		boolean success,
		T data,
		String message,
		String code,
		Object details
) {
	public static <T> ApiResponse<T> ok(T data, String message) {
		return new ApiResponse<>(true, data, message, null, null);
	}

	public static ApiResponse<Void> fail(ErrorCode code, String message, Object details) {
		return new ApiResponse<>(false, null, message, code.name(), details);
	}
}
