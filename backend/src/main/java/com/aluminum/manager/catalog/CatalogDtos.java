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

	public record CategoryDto(Long id, String name, String description, boolean active) {
	}

	public record CategoryRequest(
			@NotBlank(message = "Nhập tên loại nhôm") @Size(max = 100) String name,
			@Size(max = 500) String description,
			Boolean active
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
			String categoryName,
			String specification,
			String baseUnitName,
			BigDecimal referencePurchasePrice,
			/** Giá nhập đang áp dụng có hiệu lực từ ngày nào. Null khi sản phẩm chưa có lịch sử giá. */
			LocalDate purchasePriceEffectiveFrom,
			/** Giá bán / kg (từ giá bán / tấn ÷ 1000). */
			BigDecimal defaultSellingPrice,
			/** Đồng bộ nội bộ từ giá tấn (×100); UI không nhập riêng. */
			BigDecimal defaultSellingPriceTa,
			String imagePath,
			String description,
			boolean active,
			BigDecimal quantityBase,
			BigDecimal averageCostPerBase,
			List<UnitDto> units
	) {
	}

	/** Một mốc giá nhập trong lịch sử. {@code effectiveTo} null nghĩa là mốc xa nhất đã ghi. */
	public record PurchasePriceDto(
			Long id,
			LocalDate effectiveFrom,
			LocalDate effectiveTo,
			BigDecimal pricePerBase,
			String note,
			/** True khi đây là mốc đang áp dụng hôm nay. */
			boolean open,
			/** True khi mốc được đặt cho một ngày trong tương lai. */
			boolean future
	) {
	}

	public record PurchasePriceRequest(
			@NotNull(message = "Nhập giá nhập")
			@DecimalMin(value = "0", message = "Giá nhập không được âm") BigDecimal pricePerBase,
			/** Bỏ trống thì hiệu lực từ hôm nay. */
			LocalDate effectiveFrom,
			@Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String note
	) {
	}

	public record ProductRequest(
			@Size(max = 50, message = "Mã nhôm tối đa 50 ký tự") String productCode,
			@NotBlank(message = "Nhập tên sản phẩm") @Size(max = 200) String name,
			@NotNull(message = "Chọn loại nhôm") Long categoryId,
			@Size(max = 200) String specification,
			@NotBlank(message = "Nhập đơn vị cơ sở") @Size(max = 30) String baseUnitName,
			@NotNull(message = "Nhập giá nhập")
			@DecimalMin(value = "0", message = "Giá không được âm") BigDecimal referencePurchasePrice,
			@NotNull(message = "Nhập giá bán / tấn")
			@DecimalMin(value = "0", message = "Giá không được âm") BigDecimal defaultSellingPrice,
			@DecimalMin(value = "0", message = "Giá không được âm") BigDecimal defaultSellingPriceTa,
			@Size(max = 2_000_000, message = "Ảnh vượt quá dung lượng cho phép") String imagePath,
			@Size(max = 500, message = "Ghi chú tối đa 500 ký tự") String description,
			Boolean active
	) {
	}
}
