package com.aluminum.manager.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<ApiResponse<Void>> business(BusinessException ex) {
		return ResponseEntity.status(ex.getCode().status())
				.body(ApiResponse.fail(ex.getCode(), ex.getMessage(), ex.getDetails()));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException ex) {
		Map<String, String> fields = new LinkedHashMap<>();
		ex.getBindingResult().getFieldErrors().forEach(error ->
				fields.put(error.getField(), error.getDefaultMessage()));
		String message = fields.values().stream().findFirst().orElse("Dữ liệu không hợp lệ");
		return ResponseEntity.badRequest()
				.body(ApiResponse.fail(ErrorCode.VALIDATION_ERROR, message, fields));
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiResponse<Void>> unreadable(HttpMessageNotReadableException ex) {
		return ResponseEntity.badRequest()
				.body(ApiResponse.fail(ErrorCode.VALIDATION_ERROR, "Dữ liệu gửi lên không đọc được", null));
	}

	/** Thiếu hoặc sai Content-Type. Trả 415 đúng chuẩn thay vì 500 để client biết đường sửa. */
	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiResponse<Void>> mediaType(HttpMediaTypeNotSupportedException ex) {
		return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
				.body(ApiResponse.fail(ErrorCode.VALIDATION_ERROR,
						"Dữ liệu phải gửi dạng JSON (Content-Type: application/json)", null));
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	public ResponseEntity<ApiResponse<Void>> conflict(DataIntegrityViolationException ex) {
		log.warn("Data integrity violation", ex);
		return ResponseEntity.status(ErrorCode.DUPLICATE_PRODUCT_CODE.status())
				.body(ApiResponse.fail(
						ErrorCode.DUPLICATE_PRODUCT_CODE,
						"Dữ liệu bị trùng hoặc vi phạm ràng buộc",
						null));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiResponse<Void>> unhandled(Exception ex) {
		log.error("Unhandled error", ex);
		return ResponseEntity.internalServerError()
				.body(ApiResponse.fail(
						ErrorCode.INTERNAL_ERROR,
						"Có lỗi trong hệ thống. Thử lại hoặc xem nhật ký backend.",
						null));
	}
}
