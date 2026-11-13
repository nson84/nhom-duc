package com.aluminum.manager.catalog;

import com.aluminum.manager.catalog.CatalogDtos.CatalogSyncResult;
import com.aluminum.manager.catalog.CatalogDtos.CategoryDto;
import com.aluminum.manager.catalog.CatalogDtos.CategoryRequest;
import com.aluminum.manager.catalog.CatalogDtos.ProductDto;
import com.aluminum.manager.catalog.CatalogDtos.ProductRequest;
import com.aluminum.manager.catalog.CatalogDtos.PurchasePriceDto;
import com.aluminum.manager.catalog.CatalogDtos.PurchasePriceRequest;
import com.aluminum.manager.catalog.CatalogDtos.UnitDto;
import com.aluminum.manager.catalog.CatalogDtos.UnitRequest;
import com.aluminum.manager.catalog.PurchasePriceService.ResolvedPrice;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.common.NameValidation;
import com.aluminum.manager.security.CurrentAdmin;
import com.aluminum.manager.shop.ShopAccess;
import com.aluminum.manager.shop.ShopContext;
import com.aluminum.manager.system.AuditService;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CatalogService {

	public static final String SELL_WEIGHT = "WEIGHT";
	public static final String UNIT_KG = "kg";
	public static final String UNIT_AREA = "m²";

	private final JdbcTemplate jdbc;
	private final AuditService audit;
	private final PurchasePriceService purchasePrices;
	private final Clock clock;
	private final ShopAccess shops;
	private final ProductImages images;

	public CatalogService(
			JdbcTemplate jdbc, AuditService audit, PurchasePriceService purchasePrices, Clock clock, ShopAccess shops,
			ProductImages images) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.purchasePrices = purchasePrices;
		this.clock = clock;
		this.shops = shops;
		this.images = images;
	}

	/**
	 * Chỉ Phân phối → Xưởng: loại khớp theo tên, SP khớp theo mã.
	 * Loại mới: copy giá nhập tham khảo, KHÔNG copy giá bán thành phẩm.
	 * Loại đã có: đồng bộ giá nhập tham khảo, giữ giá bán/mô tả/trạng thái của Xưởng.
	 * SP mới: tạo kèm đơn vị; SP đã có: chỉ cập nhật tên/ảnh. Tồn kho không đụng.
	 */
	@Transactional
	public CatalogSyncResult syncCatalogToOtherShop() {
		if (!shops.isDistribution()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Chỉ Phân phối được đồng bộ danh mục sang Xưởng. Xưởng không đồng bộ ngược về Phân phối.",
					null);
		}
		long sourceShopId = shops.currentId();
		String targetCode = ShopContext.MANUFACTURING;
		long targetShopId = shops.idOf(targetCode);
		String targetName = shops.shopName(targetCode);

		int categoriesCreated = 0;
		int categoriesUpdated = 0;
		List<CategoryRow> sourceCategories = jdbc.query("""
				SELECT id, name, description, is_active, reference_purchase_price,
				       default_selling_price, default_selling_price_ta,
				       default_selling_price_ton, default_selling_price_m2
				FROM product_categories WHERE shop_id = ?
				ORDER BY name
				""", (rs, i) -> new CategoryRow(
				rs.getLong("id"),
				rs.getString("name"),
				rs.getString("description"),
				rs.getBoolean("is_active"),
				rs.getBigDecimal("reference_purchase_price"),
				rs.getBigDecimal("default_selling_price"),
				rs.getBigDecimal("default_selling_price_ta"),
				rs.getBigDecimal("default_selling_price_ton"),
				orZero(rs.getBigDecimal("default_selling_price_m2"))), sourceShopId);

		for (CategoryRow src : sourceCategories) {
			// Ưu tiên giá nhập có hiệu lực hôm nay, không ghi đè giá bán Xưởng.
			var sourcePrice = purchasePrices.resolve(src.id(), LocalDate.now(clock));
			BigDecimal referencePrice = sourcePrice == null ? orZero(src.purchaseKg()) : sourcePrice.pricePerBase();
			Long targetId = jdbc.query("""
					SELECT id FROM product_categories WHERE shop_id = ? AND name = ?
					""", (rs, i) -> rs.getLong("id"), targetShopId, src.name()).stream().findFirst().orElse(null);
			if (targetId == null) {
				long newId = insert("""
						INSERT INTO product_categories (
						    name, description, reference_purchase_price, default_selling_price,
						    default_selling_price_ta, default_selling_price_ton, default_selling_price_m2,
						    is_active, shop_id)
						VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
						""", src.name(), blankToNull(src.description()),
						referencePrice, BigDecimal.ZERO, BigDecimal.ZERO,
						BigDecimal.ZERO, BigDecimal.ZERO, src.active(), targetShopId);
				if (referencePrice.signum() > 0) purchasePrices.record(newId, referencePrice, LocalDate.now(clock),
						"Đồng bộ từ " + ShopContext.code());
				categoriesCreated++;
			} else {
				jdbc.update("""
						UPDATE product_categories
						SET reference_purchase_price = ?
						WHERE id = ?
						""", referencePrice, targetId);
				// Đồng bộ mốc giá nhập tham khảo, không đụng các trường giá bán của Xưởng.
				var currentPrice = purchasePrices.resolve(targetId, LocalDate.now(clock));
				if (referencePrice.signum() > 0 && (currentPrice == null || currentPrice.pricePerBase().compareTo(referencePrice) != 0)) {
					purchasePrices.record(targetId, referencePrice, LocalDate.now(clock),
							"Đồng bộ giá nhập từ " + ShopContext.code());
				}
				categoriesUpdated++;
			}
		}

		int productsCreated = 0;
		int productsUpdated = 0;
		List<ProductRow> sourceProducts = jdbc.query("""
				SELECT id, product_code, name, image_path, description, is_active, base_unit_name
				FROM products WHERE shop_id = ?
				ORDER BY product_code
				""", (rs, i) -> new ProductRow(
				rs.getLong("id"),
				rs.getString("product_code"),
				rs.getString("name"),
				rs.getString("image_path"),
				rs.getString("description"),
				rs.getBoolean("is_active"),
				rs.getString("base_unit_name")), sourceShopId);

		for (ProductRow src : sourceProducts) {
			String image = images.save(src.imagePath());
			if (!java.util.Objects.equals(image, src.imagePath())) {
				jdbc.update("UPDATE products SET image_path = ? WHERE id = ? AND image_path = ?",
						image, src.id(), src.imagePath());
			}
			Long targetId = jdbc.query("""
					SELECT id FROM products WHERE shop_id = ? AND product_code = ?
					""", (rs, i) -> rs.getLong("id"), targetShopId, src.code()).stream().findFirst().orElse(null);
			if (targetId == null) {
				long newId = insert("""
						INSERT INTO products (product_code, name, category_id, specification, base_unit_name,
						                      sell_mode, reference_purchase_price, default_selling_price,
						                      default_selling_price_ta, default_selling_price_m2, kg_per_m2,
						                      image_path, description, is_active, shop_id)
						VALUES (?, ?, NULL, NULL, ?, 'WEIGHT', 0, 0, 0, 0, NULL, ?, ?, ?, ?)
						""",
						src.code(), src.name().trim(),
						src.baseUnit() == null || src.baseUnit().isBlank() ? UNIT_KG : src.baseUnit(),
						image, blankToNull(src.description()),
						src.active(), targetShopId);
				ensureRetailTaUnit(newId, UNIT_KG);
				ensureTonUnit(newId, UNIT_KG);
				productsCreated++;
			} else {
				jdbc.update("""
						UPDATE products
						SET name = ?, image_path = ?
						WHERE id = ?
						""", src.name().trim(), image, targetId);
				ensureRetailTaUnit(targetId, UNIT_KG);
				ensureTonUnit(targetId, UNIT_KG);
				productsUpdated++;
			}
		}

		audit.record(CurrentAdmin.get().id(), "SYNC_CATALOG", "shops", String.valueOf(targetShopId),
				"Đồng bộ danh mục → " + targetCode
						+ " (loại +" + categoriesCreated + "/~" + categoriesUpdated
						+ ", SP +" + productsCreated + "/~" + productsUpdated + ")");
		return new CatalogSyncResult(
				targetCode, targetName, categoriesCreated, categoriesUpdated, productsCreated, productsUpdated);
	}

	@Transactional(readOnly = true)
	public List<CategoryDto> categories() {
		LocalDate today = LocalDate.now(clock);
		List<CategoryDto> rows = jdbc.query("""
				SELECT id, name, description, is_active, reference_purchase_price,
				       default_selling_price, default_selling_price_ta,
				       default_selling_price_ton, default_selling_price_m2
				FROM product_categories
				WHERE 1 = 1
				""" + shops.shopOnly(null) + """
				 ORDER BY name
				""", (rs, i) -> new CategoryDto(
				rs.getLong("id"),
				rs.getString("name"),
				rs.getString("description"),
				rs.getBoolean("is_active"),
				rs.getBigDecimal("reference_purchase_price"),
				null,
				rs.getBigDecimal("default_selling_price"),
				rs.getBigDecimal("default_selling_price_ta"),
				rs.getBigDecimal("default_selling_price_ton"),
				orZero(rs.getBigDecimal("default_selling_price_m2"))));
		if (rows.isEmpty()) {
			return rows;
		}
		var prices = purchasePrices.resolveAll(rows.stream().map(CategoryDto::id).toList(), today);
		return rows.stream().map(c -> {
			ResolvedPrice eff = prices.get(c.id());
			return new CategoryDto(
					c.id(), c.name(), c.description(), c.active(),
					eff == null ? c.referencePurchasePrice() : eff.pricePerBase(),
					eff == null ? null : eff.effectiveFrom(),
					c.defaultSellingPrice(), c.defaultSellingPriceTa(),
					c.defaultSellingPriceTon(), c.defaultSellingPriceM2());
		}).toList();
	}

	@Transactional
	public CategoryDto createCategory(CategoryRequest request) {
		NameValidation.requireNoLeadingDigit("Tên loại nhôm", request.name());
		BigDecimal purchaseKg = orZero(request.referencePurchasePrice());
		BigDecimal sellKg = orZero(request.defaultSellingPrice());
		BigDecimal sellTa = request.defaultSellingPriceTa() != null
				? request.defaultSellingPriceTa()
				: sellKg.multiply(BigDecimal.valueOf(100));
		BigDecimal sellTon = request.defaultSellingPriceTon() != null
				? request.defaultSellingPriceTon()
				: sellKg.multiply(BigDecimal.valueOf(1000));
		BigDecimal sellM2 = orZero(request.defaultSellingPriceM2());
		long id = insert("""
				INSERT INTO product_categories (
				    name, description, reference_purchase_price, default_selling_price,
				    default_selling_price_ta, default_selling_price_ton, default_selling_price_m2,
				    is_active, shop_id)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
				""", request.name().trim(), blankToNull(request.description()),
				purchaseKg, sellKg, sellTa, sellTon, sellM2,
				request.active() == null || request.active(), shops.currentId());
		purchasePrices.record(id, purchaseKg, LocalDate.now(clock), "Giá nhập khi tạo loại nhôm");
		audit.record(CurrentAdmin.get().id(), "CREATE_CATEGORY", "product_categories", String.valueOf(id), request.name());
		return category(id);
	}

	@Transactional
	public CategoryDto updateCategory(long id, CategoryRequest request) {
		NameValidation.requireNoLeadingDigit("Tên loại nhôm", request.name());
		category(id);
		BigDecimal sellKg = orZero(request.defaultSellingPrice());
		BigDecimal sellTa = request.defaultSellingPriceTa() != null
				? request.defaultSellingPriceTa()
				: sellKg.multiply(BigDecimal.valueOf(100));
		BigDecimal sellTon = request.defaultSellingPriceTon() != null
				? request.defaultSellingPriceTon()
				: sellKg.multiply(BigDecimal.valueOf(1000));
		BigDecimal sellM2 = orZero(request.defaultSellingPriceM2());
		BigDecimal purchaseKg = orZero(request.referencePurchasePrice());
		try {
			jdbc.update("""
					UPDATE product_categories
					SET name = ?, description = ?, default_selling_price = ?,
					    default_selling_price_ta = ?, default_selling_price_ton = ?,
					    default_selling_price_m2 = ?, is_active = ?
					WHERE id = ?
					""", request.name().trim(), blankToNull(request.description()),
					sellKg, sellTa, sellTon, sellM2,
					request.active() == null || request.active(), id);
		} catch (DuplicateKeyException ex) {
			throw duplicate("Tên loại nhôm đã tồn tại");
		}
		ResolvedPrice current = purchasePrices.current(id);
		if (current == null || purchaseKg.compareTo(current.pricePerBase()) != 0) {
			purchasePrices.record(id, purchaseKg, LocalDate.now(clock), "Sửa giá nhập loại nhôm");
		}
		audit.record(CurrentAdmin.get().id(), "UPDATE_CATEGORY", "product_categories", String.valueOf(id), request.name());
		return category(id);
	}

	@Transactional(readOnly = true)
	public List<ProductDto> products(String query, Long categoryId, Boolean active, LocalDate asOf) {
		StringBuilder sql = new StringBuilder("""
				SELECT p.id FROM products p
				WHERE 1 = 1
				""" + shops.shopOnly("p"));
		List<Object> args = new ArrayList<>();
		if (query != null && !query.isBlank()) {
			sql.append(" AND (p.product_code LIKE ? OR p.name LIKE ?)");
			String like = "%" + query.trim() + "%";
			args.add(like);
			args.add(like);
		}
		if (active != null) {
			sql.append(" AND p.is_active = ?");
			args.add(active);
		}
		// categoryId giữ param API cũ nhưng không lọc SP (SP không gắn loại cố định).
		sql.append(" ORDER BY p.product_code");
		List<Long> ids = jdbc.queryForList(sql.toString(), Long.class, args.toArray());
		if (ids.isEmpty()) {
			return List.of();
		}
		List<ProductDto> result = new ArrayList<>();
		for (Long id : ids) {
			result.add(product(id));
		}
		return result;
	}

	@Transactional(readOnly = true)
	public ProductDto product(long id) {
		return product(id, (LocalDate) null);
	}

	@Transactional(readOnly = true)
	public ProductDto product(long id, LocalDate asOf) {
		List<ProductDto> rows = jdbc.query("""
			SELECT p.id, p.product_code, p.name, p.category_id, p.base_unit_name,
				       p.image_path, p.description, p.is_active,
				       COALESCE((
				           SELECT SUM(b.quantity_base) FROM stock_balances b WHERE b.product_id = p.id
				       ), 0) AS quantity_base
				FROM products p
				WHERE p.id = ? AND p.shop_id = ?
				""", (rs, i) -> new ProductDto(
				rs.getLong("id"),
				rs.getString("product_code"),
				rs.getString("name"),
				rs.getObject("category_id", Long.class),
				rs.getString("base_unit_name"),
				images.display(rs.getString("image_path")),
				rs.getString("description"),
				rs.getBoolean("is_active"),
				orZero(rs.getBigDecimal("quantity_base")),
				units(id)), id, shops.currentId());
		if (rows.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy sản phẩm", null);
		}
		return rows.get(0);
	}

	@Transactional(readOnly = true)
	public String nextProductCode() {
		List<String> codes = jdbc.queryForList(
				"SELECT product_code FROM products WHERE shop_id = ? AND product_code REGEXP '^NH-[0-9]+$'",
				String.class, shops.currentId());
		int max = 0;
		for (String c : codes) {
			try {
				int num = Integer.parseInt(c.substring(3));
				if (num > max) {
					max = num;
				}
			} catch (NumberFormatException ignored) {
			}
		}
		if (max == 0) {
			Integer count = jdbc.queryForObject(
					"SELECT COUNT(*) FROM products WHERE shop_id = ?", Integer.class, shops.currentId());
			max = count == null ? 0 : count;
		}
		return "NH-%04d".formatted(max + 1);
	}

	@Transactional
	public ProductDto createProduct(ProductRequest request) {
		NameValidation.requireNoLeadingDigit("Tên sản phẩm", request.name());
		requireUniqueProductName(request.name(), null);
		String code = blankToNull(request.productCode());
		if (code == null) {
			code = nextProductCode();
		}
		String baseUnit = UNIT_KG;
		if (request.categoryId() != null) category(request.categoryId());
		long id;
		try {
			id = insert("""
					INSERT INTO products (product_code, name, category_id, specification, base_unit_name,
					                      sell_mode, reference_purchase_price, default_selling_price,
					                      default_selling_price_ta, default_selling_price_m2, kg_per_m2,
					                      image_path, description, is_active, shop_id)
					VALUES (?, ?, ?, NULL, ?, 'WEIGHT', 0, 0, 0, 0, NULL, ?, ?, ?, ?)
					""",
				code, request.name().trim(), request.categoryId(), baseUnit,
					images.save(request.imagePath()), blankToNull(request.description()),
					request.active() == null || request.active(), shops.currentId());
		} catch (DuplicateKeyException ex) {
			throw duplicate("Mã nhôm đã tồn tại");
		}
		ensureRetailTaUnit(id, baseUnit);
		ensureTonUnit(id, baseUnit);
		audit.record(CurrentAdmin.get().id(), "CREATE_PRODUCT", "products", String.valueOf(id), code);
		return product(id);
	}

	private void ensureTonUnit(long productId, String baseUnitName) {
		if (!"kg".equalsIgnoreCase(baseUnitName)) {
			return;
		}
		Integer existing = jdbc.queryForObject(
				"SELECT COUNT(*) FROM product_units WHERE product_id = ? AND unit_name = 'tấn'",
				Integer.class, productId);
		if (existing != null && existing > 0) {
			return;
		}
		try {
			jdbc.update("""
					INSERT INTO product_units (product_id, unit_name, factor_to_base, is_base_unit,
					                           is_default_selling_unit, is_active)
					VALUES (?, 'tấn', 1000, 0, 0, 1)
					""", productId);
		} catch (DuplicateKeyException ignored) {
		}
	}

	private void ensureRetailTaUnit(long productId, String baseUnitName) {
		if (!"kg".equalsIgnoreCase(baseUnitName)) {
			return;
		}
		Integer existing = jdbc.queryForObject(
				"SELECT COUNT(*) FROM product_units WHERE product_id = ? AND unit_name = 'tạ'",
				Integer.class, productId);
		if (existing != null && existing > 0) {
			return;
		}
		jdbc.update("UPDATE product_units SET is_default_selling_unit = 0 WHERE product_id = ?", productId);
		try {
			jdbc.update("""
					INSERT INTO product_units (product_id, unit_name, factor_to_base, is_base_unit,
					                           is_default_selling_unit, is_active)
					VALUES (?, 'tạ', 100, 0, 1, 1)
					""", productId);
		} catch (DuplicateKeyException ignored) {
		}
	}

	@Transactional
	public ProductDto updateProduct(long id, ProductRequest request) {
		NameValidation.requireNoLeadingDigit("Tên sản phẩm", request.name());
		ProductDto existing = product(id);
		if (!existing.name().trim().equalsIgnoreCase(request.name().trim())) {
			requireUniqueProductName(request.name(), id);
		}
		if (request.categoryId() != null) category(request.categoryId());
		String code = blankToNull(request.productCode());
		if (code == null) {
			code = existing.productCode();
		}
		try {
			jdbc.update("""
					UPDATE products
					SET product_code = ?, name = ?, category_id = ?, image_path = ?, description = ?, is_active = ?
					WHERE id = ?
					""",
				code, request.name().trim(), request.categoryId(),
					images.save(request.imagePath()), blankToNull(request.description()),
					request.active() == null || request.active(), id);
		} catch (DuplicateKeyException ex) {
			throw duplicate("Mã nhôm đã tồn tại");
		}
		ensureRetailTaUnit(id, UNIT_KG);
		ensureTonUnit(id, UNIT_KG);
		audit.record(CurrentAdmin.get().id(), "UPDATE_PRODUCT", "products", String.valueOf(id), code);
		return product(id);
	}

	private void requireUniqueProductName(String name, Long excludingId) {
		String sql = "SELECT COUNT(*) FROM products WHERE shop_id = ? AND LOWER(TRIM(name)) = LOWER(TRIM(?))"
				+ (excludingId == null ? "" : " AND id <> ?");
		Integer count = excludingId == null
				? jdbc.queryForObject(sql, Integer.class, shops.currentId(), name)
				: jdbc.queryForObject(sql, Integer.class, shops.currentId(), name, excludingId);
		if (count != null && count > 0) {
			throw duplicate("Đã có sản phẩm này rồi, vui lòng chọn sản phẩm khác");
		}
	}

	@Transactional(readOnly = true)
	public List<PurchasePriceDto> purchasePriceHistory(long categoryId) {
		category(categoryId);
		return purchasePrices.history(categoryId).stream()
				.map(row -> new PurchasePriceDto(
						row.id(), row.effectiveFrom(), row.effectiveTo(),
						row.pricePerBase(), row.note(), row.open(), row.future()))
				.toList();
	}

	@Transactional
	public List<PurchasePriceDto> recordPurchasePrice(long categoryId, PurchasePriceRequest request) {
		category(categoryId);
		ResolvedPrice saved = purchasePrices.record(
				categoryId, request.pricePerBase(), request.effectiveFrom(), request.note());
		audit.record(CurrentAdmin.get().id(), "SET_PURCHASE_PRICE", "category_purchase_prices",
				String.valueOf(saved.priceId()),
				"Giá nhập loại " + categoryId + " = " + saved.pricePerBase().toPlainString()
						+ " từ " + saved.effectiveFrom());
		return purchasePriceHistory(categoryId);
	}

	@Transactional
	public ProductDto addUnit(long productId, UnitRequest request) {
		product(productId);
		boolean defaultSelling = Boolean.TRUE.equals(request.defaultSelling());
		if (defaultSelling) {
			jdbc.update("UPDATE product_units SET is_default_selling_unit = 0 WHERE product_id = ?", productId);
		}
		try {
			jdbc.update("""
					INSERT INTO product_units (product_id, unit_name, factor_to_base, is_base_unit,
					                           is_default_selling_unit, is_active)
					VALUES (?, ?, ?, 0, ?, ?)
					""", productId, request.unitName().trim(), request.factorToBase(), defaultSelling,
					request.active() == null || request.active());
		} catch (DuplicateKeyException ex) {
			throw duplicate("Đơn vị này đã có trong sản phẩm");
		}
		audit.record(CurrentAdmin.get().id(), "ADD_UNIT", "products", String.valueOf(productId), request.unitName());
		return product(productId);
	}

	@Transactional
	public ProductDto updateUnit(long productId, long unitId, UnitRequest request) {
		UnitRow unit = jdbc.query("""
				SELECT id, factor_to_base, is_base_unit FROM product_units WHERE id = ? AND product_id = ?
				""", (rs, i) -> new UnitRow(rs.getLong("id"), rs.getBigDecimal("factor_to_base"), rs.getBoolean("is_base_unit")),
				unitId, productId).stream().findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy đơn vị", null));
		if (unit.base()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Đơn vị cơ sở sửa trong thông tin sản phẩm", null);
		}
		if (request.factorToBase().compareTo(unit.factor()) != 0) {
			Integer used = jdbc.queryForObject(
					"SELECT COUNT(*) FROM stock_document_items WHERE unit_id = ?", Integer.class, unitId);
			if (used != null && used > 0) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR,
						"Hệ số này đã dùng trong phiếu. Hãy tạo đơn vị mới thay vì sửa hệ số cũ", null);
			}
		}
		boolean defaultSelling = Boolean.TRUE.equals(request.defaultSelling());
		if (defaultSelling) {
			jdbc.update("UPDATE product_units SET is_default_selling_unit = 0 WHERE product_id = ?", productId);
		}
		try {
			jdbc.update("""
					UPDATE product_units
					SET unit_name = ?, factor_to_base = ?, is_default_selling_unit = ?, is_active = ?
					WHERE id = ?
					""", request.unitName().trim(), request.factorToBase(), defaultSelling,
					request.active() == null || request.active(), unitId);
		} catch (DuplicateKeyException ex) {
			throw duplicate("Đơn vị này đã có trong sản phẩm");
		}
		audit.record(CurrentAdmin.get().id(), "UPDATE_UNIT", "product_units", String.valueOf(unitId), request.unitName());
		return product(productId);
	}

	private List<UnitDto> units(long productId) {
		return jdbc.query("""
				SELECT id, unit_name, factor_to_base, is_base_unit, is_default_selling_unit, is_active
				FROM product_units WHERE product_id = ?
				ORDER BY is_base_unit DESC, factor_to_base
				""", (rs, i) -> new UnitDto(
				rs.getLong("id"), rs.getString("unit_name"), rs.getBigDecimal("factor_to_base"),
				rs.getBoolean("is_base_unit"), rs.getBoolean("is_default_selling_unit"), rs.getBoolean("is_active")),
				productId);
	}

	public CategoryDto category(long id) {
		LocalDate today = LocalDate.now(clock);
		ResolvedPrice eff = purchasePrices.resolve(id, today);
		return jdbc.query("""
				SELECT id, name, description, is_active, reference_purchase_price,
				       default_selling_price, default_selling_price_ta,
				       default_selling_price_ton, default_selling_price_m2
				FROM product_categories WHERE id = ? AND shop_id = ?
				""", (rs, i) -> new CategoryDto(
				rs.getLong("id"),
				rs.getString("name"),
				rs.getString("description"),
				rs.getBoolean("is_active"),
				eff == null ? rs.getBigDecimal("reference_purchase_price") : eff.pricePerBase(),
				eff == null ? null : eff.effectiveFrom(),
				rs.getBigDecimal("default_selling_price"),
				rs.getBigDecimal("default_selling_price_ta"),
				rs.getBigDecimal("default_selling_price_ton"),
				orZero(rs.getBigDecimal("default_selling_price_m2"))),
				id, shops.currentId()).stream().findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy loại nhôm", null));
	}

	private long insert(String sql, Object... args) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		try {
			jdbc.update(connection -> {
				PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
				for (int i = 0; i < args.length; i++) {
					ps.setObject(i + 1, args[i]);
				}
				return ps;
			}, keys);
		} catch (DuplicateKeyException ex) {
			if (sql.contains("product_categories")) {
				throw duplicate("Tên loại nhôm đã tồn tại");
			}
			throw ex;
		}
		return keys.getKey().longValue();
	}

	private static BusinessException duplicate(String message) {
		return new BusinessException(ErrorCode.DUPLICATE_PRODUCT_CODE, message, null);
	}

	public static String normalizeSellMode(String value) {
		if (value == null || value.isBlank() || "WEIGHT".equalsIgnoreCase(value.trim())
				|| "AREA".equalsIgnoreCase(value.trim())) {
			return SELL_WEIGHT;
		}
		return SELL_WEIGHT;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static BigDecimal orZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private record UnitRow(long id, BigDecimal factor, boolean base) {
	}

	private record CategoryRow(
			long id,
			String name,
			String description,
			boolean active,
			BigDecimal purchaseKg,
			BigDecimal sellKg,
			BigDecimal sellTa,
			BigDecimal sellTon,
			BigDecimal sellM2
	) {
	}

	private record ProductRow(
			long id,
			String code,
			String name,
			String imagePath,
			String description,
			boolean active,
			String baseUnit
	) {
	}
}
