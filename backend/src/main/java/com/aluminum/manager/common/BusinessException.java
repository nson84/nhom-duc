package com.aluminum.manager.common;

public class BusinessException extends RuntimeException {

	private final ErrorCode code;
	private final Object details;

	public BusinessException(ErrorCode code, String message, Object details) {
		super(message);
		this.code = code;
		this.details = details;
	}

	public ErrorCode getCode() {
		return code;
	}

	public Object getDetails() {
		return details;
	}
}
