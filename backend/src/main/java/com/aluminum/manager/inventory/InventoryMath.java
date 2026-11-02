package com.aluminum.manager.inventory;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InventoryMath {

	public static final int QTY_SCALE = 6;
	public static final int MONEY_SCALE = 4;
	public static final int COST_SCALE = 6;
	public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

	private InventoryMath() {
	}

	public static BigDecimal toBaseQuantity(BigDecimal quantityDocument, BigDecimal factorToBase) {
		requirePositive(quantityDocument, "Số lượng phải lớn hơn 0");
		requirePositive(factorToBase, "Hệ số quy đổi phải lớn hơn 0");
		return quantityDocument.multiply(factorToBase).setScale(QTY_SCALE, ROUNDING);
	}

	public static BigDecimal suggestedUnitPrice(BigDecimal pricePerBase, BigDecimal factorToBase) {
		requireNonNegative(pricePerBase, "Giá không được âm");
		requirePositive(factorToBase, "Hệ số quy đổi phải lớn hơn 0");
		return pricePerBase.multiply(factorToBase).setScale(MONEY_SCALE, ROUNDING);
	}

	public static BigDecimal lineTotal(BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineDiscount) {
		requirePositive(quantity, "Số lượng phải lớn hơn 0");
		requireNonNegative(unitPrice, "Đơn giá không được âm");
		BigDecimal discount = lineDiscount == null ? BigDecimal.ZERO : lineDiscount;
		requireNonNegative(discount, "Chiết khấu không được âm");
		BigDecimal total = quantity.multiply(unitPrice).subtract(discount).setScale(MONEY_SCALE, ROUNDING);
		if (total.signum() < 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Thành tiền không được âm", null);
		}
		return total;
	}

	public static BigDecimal documentTotal(BigDecimal subtotal, BigDecimal discountAmount) {
		requireNonNegative(subtotal, "Tổng tiền hàng không được âm");
		BigDecimal discount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
		requireNonNegative(discount, "Chiết khấu không được âm");
		BigDecimal total = subtotal.subtract(discount).setScale(MONEY_SCALE, ROUNDING);
		if (total.signum() < 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tổng thanh toán không được âm", null);
		}
		return total;
	}

	public static BigDecimal weightedAverageCost(
			BigDecimal oldQuantity,
			BigDecimal oldCost,
			BigDecimal incomingQuantity,
			BigDecimal incomingCost) {
		requireNonNegative(oldQuantity, "Tồn kho không được âm");
		requireNonNegative(oldCost, "Giá vốn không được âm");
		requirePositive(incomingQuantity, "Số lượng nhập phải lớn hơn 0");
		requireNonNegative(incomingCost, "Giá nhập không được âm");
		BigDecimal newQuantity = oldQuantity.add(incomingQuantity);
		BigDecimal value = oldQuantity.multiply(oldCost).add(incomingQuantity.multiply(incomingCost));
		return value.divide(newQuantity, COST_SCALE, ROUNDING);
	}

	public static BigDecimal averageAfterRemoval(
			BigDecimal oldQuantity,
			BigDecimal oldCost,
			BigDecimal removedQuantity,
			BigDecimal removedCost) {
		requireNonNegative(oldQuantity, "Tồn kho không được âm");
		requireNonNegative(oldCost, "Giá vốn không được âm");
		requirePositive(removedQuantity, "Số lượng đảo phải lớn hơn 0");
		requireNonNegative(removedCost, "Giá nhập không được âm");
		if (removedQuantity.compareTo(oldQuantity) > 0) {
			throw insufficient(oldQuantity, removedQuantity);
		}
		BigDecimal newQuantity = oldQuantity.subtract(removedQuantity);
		if (newQuantity.signum() == 0) {
			return BigDecimal.ZERO.setScale(COST_SCALE, ROUNDING);
		}
		BigDecimal value = oldQuantity.multiply(oldCost).subtract(removedQuantity.multiply(removedCost));
		if (value.signum() < 0) {
			throw new BusinessException(
					ErrorCode.VALIDATION_ERROR,
					"Không thể đảo phiếu nhập vì giá trị tồn kho âm",
					null);
		}
		return value.divide(newQuantity, COST_SCALE, ROUNDING);
	}

	public static BigDecimal costTotal(BigDecimal quantityBase, BigDecimal costPerBase) {
		requireNonNegative(quantityBase, "Số lượng không được âm");
		requireNonNegative(costPerBase, "Giá vốn không được âm");
		return quantityBase.multiply(costPerBase).setScale(MONEY_SCALE, ROUNDING);
	}

	public static BigDecimal snapshotMoney(BigDecimal amount) {
		requireNonNegative(amount, "Số tiền không được âm");
		return amount.setScale(MONEY_SCALE, ROUNDING);
	}

	public static BigDecimal applyDelta(BigDecimal balance, BigDecimal deltaBase) {
		requireNonNegative(balance, "Tồn kho không được âm");
		BigDecimal next = balance.add(deltaBase).setScale(QTY_SCALE, ROUNDING);
		if (next.signum() < 0) {
			throw insufficient(balance, deltaBase.abs());
		}
		return next;
	}

	public static BigDecimal reversalDelta(BigDecimal originalDelta) {
		if (originalDelta == null || originalDelta.signum() == 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Biến động kho cần đảo không hợp lệ", null);
		}
		return originalDelta.negate();
	}

	private static void requirePositive(BigDecimal value, String message) {
		if (value == null || value.signum() <= 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, message, null);
		}
	}

	private static void requireNonNegative(BigDecimal value, String message) {
		if (value == null || value.signum() < 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, message, null);
		}
	}

	private static BusinessException insufficient(BigDecimal available, BigDecimal requested) {
		Map<String, Object> details = new LinkedHashMap<>();
		details.put("availableQuantity", available);
		details.put("requestedQuantity", requested);
		details.put("unit", "kg");
		return new BusinessException(
				ErrorCode.INSUFFICIENT_STOCK,
				"Số lượng tồn kho không đủ để xuất hàng",
				details);
	}
}
