package com.aluminum.manager.inventory;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import java.time.YearMonth;

public final class DocumentCodes {

	private DocumentCodes() {
	}

	public static String sequenceKey(String documentType) {
		return switch (documentType) {
			case "ADJUSTMENT_IN", "ADJUSTMENT_OUT" -> "ADJUSTMENT";
			case "PURCHASE", "SALE", "FINANCE_INCOME", "FINANCE_EXPENSE" -> documentType;
			default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Loại chứng từ không hợp lệ", null);
		};
	}

	public static String format(String documentType, YearMonth period, int sequence) {
		if (sequence < 1) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Số thứ tự phiếu không hợp lệ", null);
		}
		String prefix = switch (documentType) {
			case "PURCHASE" -> "PN";
			case "SALE" -> "PX";
			case "ADJUSTMENT_IN", "ADJUSTMENT_OUT" -> "DC";
			case "FINANCE_INCOME" -> "PT";
			case "FINANCE_EXPENSE" -> "PC";
			default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Loại chứng từ không hợp lệ", null);
		};
		String running = sequence < 10_000 ? "%04d".formatted(sequence) : Integer.toString(sequence);
		return "%s-%04d%02d-%s".formatted(prefix, period.getYear(), period.getMonthValue(), running);
	}
}
