package com.aluminum.manager.factory;

import com.aluminum.manager.factory.FactoryDtos.RawLayerDto;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** Phân bổ lượng nhôm còn lại vào các phiếu nhập, theo thứ tự mới nhất trước. */
public final class FactoryLayerAllocator {
	private FactoryLayerAllocator() {
	}

	public static List<RawLayerDto> allocate(BigDecimal quantityKg, List<RawLayerDto> newestFirst) {
		if (quantityKg == null || quantityKg.signum() < 0) throw new IllegalArgumentException("Số kg không hợp lệ");
		BigDecimal remaining = quantityKg;
		List<RawLayerDto> result = new ArrayList<>();
		for (RawLayerDto source : newestFirst) {
			if (remaining.signum() == 0) break;
			BigDecimal used = remaining.min(source.quantityKg());
			BigDecimal value = used.multiply(source.unitCostPerKg()).setScale(4, RoundingMode.HALF_UP);
			result.add(new RawLayerDto(source.purchaseItemId(), source.documentCode(),
					source.documentDate(), used, source.unitCostPerKg(), value));
			remaining = remaining.subtract(used);
		}
		if (remaining.signum() > 0) throw new IllegalArgumentException("Thiếu " + remaining + " kg trong lịch sử nhập");
		return result;
	}
}
