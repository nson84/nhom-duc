package com.aluminum.manager.catalog;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class CatalogDtos {

	private CatalogDtos() {
	}

	public record CategoryDto(
			Long id,
			String name,
			String description,
			boolean active,
			/** Giá nhập đang áp dụng (đ/kg). */
			BigDecimal referencePurchasePrice,
			LocalDate purchasePriceEffectiveFrom,
			/** Giá bán gợi ý / kg. */
			BigDecimal defaultSellingPrice,
			BigDecimal defaultSellingPriceTa,
			BigDecimal defaultSellingPriceTon,
			BigDecimal defaultSellingPriceM2
	) {
	}

	public record CategoryRequest(
			@NotBlank(message = "Nhập tên loại nhôm") @Size(max = 100) String name,
			@Size(max = 500) String description,
			Boolean active,
			@NotNull(message = "Nhập giá nhập")
			@DecimalMin(value = "0", message = "Giá không được âm") BigDecimal referencePurchasePrice,
			@NotNull(message = "Nhập giá bán / kg")
			@DecimalMin(value = "0", message = "Giá không được âm") BigDecimal defaultSellingPrice,
			@DecimalMin(value = "0", message = "Giá không được âm") BigDecimal defaultSellingPriceTa,
			@DecimalMin(value = "0", message = "Giá không được âm") BigDecimal defaultSellingPriceTon,
			@DecimalMin(value = "0", message = "Giá không được âm") BigDecimal defaultSellingPriceM2
	) {
	}

	public record UnitDto(
			Long id,
			String unitName,
			BigDecimal factorToBase,
			boolean base,
			boolean defaultSelling,
			boolean active
	) {
	}

	public record UnitRequest(
			@NotBlank(message = "Nhập tên đơn vị") @Size(max = 30) String unitName,
			@NotNull(message = "Nhập hệ số quy đổi")
			@DecimalMin(value = "0", inclusive = false, message = "Hệ số quy đổi phải lớn hơn 0") BigDecimal factorToBase,
			Boolean defaultSelling,
			Boolean active
	) {
	}

	public record ProductDto(
			Long id,
			String productCode,
			String name,
			Long categoryId,
			String baseUnitName,
			String imagePath,
			String description,
			boolean active,
			/** Tổng tồn kg gộp mọi loại. */
			BigDecimal quantityBase,
			List<UnitDto> units
	) {
	}

	public record PurchasePriceDto(
			Long id,
			LocalDate effectiveFrom,
			LocalDate effectiveTo,
			BigDecimal pricePerBase,
			String note,
			boolean open,
			boolean future
	) {
	}

	public record PurchasePriceRequest(
			@NotNull(message = "Nhập giá nhập")
			@DecimalMin(value = "0", message = "Giá nhập không được âm") BigDecimal pricePerBase,
			LocalDate effectiveFrom,
			@Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note
	) {
	}

	/** SP chỉ mã / tên / ảnh / trạng thái. */
	public record ProductRequest(
			@Size(max = 50, message = "Mã nhôm tối đa 50 ký tự") String productCode,
			@NotBlank(message = "Nhập tên sản phẩm") @Size(max = 200) String name,
		Long categoryId,
			@Size(max = 2_000_000, message = "Ảnh vượt quá dung lượng cho phép") String imagePath,
			@Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String description,
			Boolean active
	) {
	}

	/** Kết quả đẩy danh mục (SP + loại) sang shop kia — không đụng tồn kho. */
	public record CatalogSyncResult(
			String targetShopCode,
			String targetShopName,
			int categoriesCreated,
			int categoriesUpdated,
			int productsCreated,
			int productsUpdated
	) {
	}
}
