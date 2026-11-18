package com.aluminum.manager.inventory;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import java.time.LocalDateTime;

final class StockEditPolicy {
	private StockEditPolicy() {
	}

	static void requireWithin24Hours(LocalDateTime confirmedAt, LocalDateTime now) {
		if (confirmedAt == null || now.isBefore(confirmedAt) || now.isAfter(confirmedAt.plusHours(24))) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Chỉ sửa phiếu trong 24 giờ kể từ khi xác nhận", null);
		}
	}
}
