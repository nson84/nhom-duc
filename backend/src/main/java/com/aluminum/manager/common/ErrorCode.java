package com.aluminum.manager.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
	VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
	FORBIDDEN(HttpStatus.FORBIDDEN),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
	PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN),
	INSUFFICIENT_STOCK(HttpStatus.CONFLICT),
	DUPLICATE_PRODUCT_CODE(HttpStatus.CONFLICT),
	DOCUMENT_ALREADY_CONFIRMED(HttpStatus.CONFLICT),
	DOCUMENT_NOT_REVERSIBLE(HttpStatus.CONFLICT),
	PAYROLL_PERIOD_LOCKED(HttpStatus.CONFLICT),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

	private final HttpStatus status;

	ErrorCode(HttpStatus status) {
		this.status = status;
	}

	public HttpStatus status() {
		return status;
	}
}
