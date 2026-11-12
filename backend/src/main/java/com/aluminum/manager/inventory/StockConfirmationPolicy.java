package com.aluminum.manager.inventory;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;

public final class StockConfirmationPolicy {

	public static boolean skipsStock(boolean distribution, String type) {
		return !distribution && "SALE".equals(type);
	}

	private StockConfirmationPolicy() {
	}

	public static void requireDraft(String status) {
		if (!"DRAFT".equals(status)) {
			throw new BusinessException(
					ErrorCode.DOCUMENT_ALREADY_CONFIRMED,
					"Phiếu đã được xác nhận hoặc không còn ở trạng thái nháp",
					null);
		}
	}

	public static void requireReversible(String status, boolean alreadyReversed) {
		if (!"CONFIRMED".equals(status)) {
			throw new BusinessException(
					ErrorCode.DOCUMENT_NOT_REVERSIBLE,
					"Chỉ đảo được phiếu đã xác nhận",
					null);
		}
		if (alreadyReversed) {
			throw new BusinessException(
					ErrorCode.DOCUMENT_NOT_REVERSIBLE,
					"Phiếu đã được đảo, không đảo lần thứ hai",
					null);
		}
	}
}
