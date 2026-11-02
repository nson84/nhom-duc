package com.aluminum.manager.catalog;

import com.aluminum.manager.catalog.CatalogDtos.CategoryDto;
import com.aluminum.manager.catalog.CatalogDtos.CategoryRequest;
import com.aluminum.manager.catalog.CatalogDtos.ProductDto;
import com.aluminum.manager.catalog.CatalogDtos.ProductRequest;
import com.aluminum.manager.catalog.CatalogDtos.PurchasePriceDto;
import com.aluminum.manager.catalog.CatalogDtos.PurchasePriceRequest;
import com.aluminum.manager.catalog.CatalogDtos.UnitDto;
import com.aluminum.manager.catalog.CatalogDtos.UnitRequest;
import com.aluminum.manager.catalog.PurchasePriceService.PriceRow;
import com.aluminum.manager.catalog.PurchasePriceService.ResolvedPrice;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.common.NameValidation;
import com.aluminum.manager.security.CurrentAdmin;
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

	private final JdbcTemplate jdbc;
	private final AuditService audit;
	private final PurchasePriceService purchasePrices;
	private final Clock clock;

	public CatalogService(
			JdbcTemplate jdbc, AuditService audit, PurchasePriceService purchasePrices, Clock clock) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.purchasePrices = purchasePrices;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<CategoryDto> categories() {
		return jdbc.query("""
				SELECT id, name, description, is_active FROM product_categories ORDER BY name
				""", (rs, i) -> new CategoryDto(
				rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getBoolean("is_active")));
	}

	@Transactional
	public CategoryDto createCategory(CategoryRequest request) {
		NameValidation.requireNoLeadingDigit("Tên loại nhôm", request.name());
		long id = insert("""
				INSERT INTO product_categories (name, description, is_active) VALUES (?, ?, ?)
				""", request.name().trim(), blankToNull(request.description()), request.active() == null || request.active());
		audit.record(CurrentAdmin.get().id(), "CREATE_CATEGORY", "product_categories", String.valueOf(id), request.name());
		return category(id);
	}

	@Transactional
	public CategoryDto updateCategory(long id, CategoryRequest request) {
		NameValidation.requireNoLeadingDigit("Tên loại nhôm", request.name());
		category(id);
		try {
			jdbc.update("""
					UPDATE product_categories SET name = ?, description = ?, is_active = ? WHERE id = ?
					""", request.name().trim(), blankToNull(request.description()),
					request.active() == null || request.active(), id);
		} catch (DuplicateKeyException ex) {
			throw duplicate("Tên loại nhôm đã tồn tại");
		}
		audit.record(CurrentAdmin.get().id(), "UPDATE_CATEGORY", "product_categories", String.valueOf(id), request.name());
		return category(id);
	}

	@Transactional(readOnly = true)
	public List<ProductDto> products(String query, Long categoryId, Boolean active, LocalDate asOf) {
		StringBuilder sql = new StringBuilder("""
				SELECT p.id FROM products p
				WHERE 1 = 1
				""");
		List<Object> args = new ArrayList<>();
		if (query != null && !query.isBlank()) {
			sql.append(" AND (p.product_code LIKE ? OR p.name LIKE ?)");
			String like = "%" + query.trim() + "%";
			args.add(like);
			args.add(like);
		}
		if (categoryId != null) {
			sql.append(" AND p.category_id = ?");
			args.add(categoryId);
		}
		if (active != null) {
			sql.append(" AND p.is_active = ?");
			args.add(active);
		}
		sql.append(" ORDER BY p.product_code");
		List<Long> ids = jdbc.queryForList(sql.toString(), Long.class, args.toArray());
		if (ids.isEmpty()) {
			return List.of();
		}
		// Một lượt đọc giá cho cả danh sách thay vì mỗi sản phẩm một query.
		var prices = purchasePrices.resolveAll(ids, asOf == null ? LocalDate.now(clock) : asOf);
		List<ProductDto> result = new ArrayList<>();
		for (Long id : ids) {
			result.add(product(id, prices.get(id)));
		}
		return result;
	}

	@Transactional(readOnly = true)
	public ProductDto product(long id) {
		return product(id, (LocalDate) null);
	}

	@Transactional(readOnly = true)
	public ProductDto product(long id, LocalDate asOf) {
		return product(id, purchasePrices.resolve(id, asOf));
	}

	private ProductDto product(long id, ResolvedPrice effective) {
		List<ProductDto> rows = jdbc.query("""
				SELECT p.id, p.product_code, p.name, p.category_id, c.name AS category_name,
				       p.specification, p.base_unit_name, p.reference_purchase_price,
				       p.default_selling_price, p.default_selling_price_ta,
				       p.image_path, p.description, p.is_active,
				       b.quantity_base, b.average_cost_per_base
				FROM products p
				JOIN product_categories c ON c.id = p.category_id
				LEFT JOIN stock_balances b ON b.product_id = p.id
				WHERE p.id = ?
				""", (rs, i) -> new ProductDto(
				rs.getLong("id"),
				rs.getString("product_code"),
				rs.getString("name"),
				rs.getLong("category_id"),
				rs.getString("category_name"),
				rs.getString("specification"),
				rs.getString("base_unit_name"),
				effective == null ? rs.getBigDecimal("reference_purchase_price") : effective.pricePerBase(),
				effective == null ? null : effective.effectiveFrom(),
				rs.getBigDecimal("default_selling_price"),
				rs.getBigDecimal("default_selling_price_ta"),
				rs.getString("image_path"),
				rs.getString("description"),
				rs.getBoolean("is_active"),
				orZero(rs.getBigDecimal("quantity_base")),
				orZero(rs.getBigDecimal("average_cost_per_base")),
				units(id)), id);
		if (rows.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy sản phẩm", null);
		}
		return rows.get(0);
	}

	@Transactional(readOnly = true)
	public String nextProductCode() {
		List<String> codes = jdbc.queryForList(
				"SELECT product_code FROM products WHERE product_code REGEXP '^NH-[0-9]+$'", String.class);
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
			Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
			max = count == null ? 0 : count;
		}
		return "NH-%04d".formatted(max + 1);
	}

	@Transactional
	public ProductDto createProduct(ProductRequest request) {
		NameValidation.requireNoLeadingDigit("Tên sản phẩm", request.name());
		requireCategory(request.categoryId());
		String code = blankToNull(request.productCode());
		if (code == null) {
			code = nextProductCode();
		}
		long id;
		try {
			id = insert("""
					INSERT INTO products (product_code, name, category_id, specification, base_unit_name,
					                      reference_purchase_price, default_selling_price,
					                      default_selling_price_ta, image_path,
					                      description, is_active)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""",
					code, request.name().trim(), request.categoryId(),
					blankToNull(request.specification()), request.baseUnitName().trim(),
					request.referencePurchasePrice(), request.defaultSellingPrice(),
					sellingPriceTaOrDerived(request),
					blankToNull(request.imagePath()), blankToNull(request.description()),
					request.active() == null || request.active());
		} catch (DuplicateKeyException ex) {
			throw duplicate("Mã nhôm đã tồn tại");
		}
		String baseUnit = request.baseUnitName().trim();
		ensureRetailTaUnit(id, baseUnit);
		ensureTonUnit(id, baseUnit);
		// Giá nhập đầu tiên mở lịch sử giá từ hôm nay để mọi phiếu sau đều tra được giá chuẩn.
		purchasePrices.record(id, request.referencePurchasePrice(), LocalDate.now(clock),
				"Giá nhập khi tạo sản phẩm");
		audit.record(CurrentAdmin.get().id(), "CREATE_PRODUCT", "products", String.valueOf(id), code);
		return product(id);
	}

	/** Nhập lô theo tấn: 1 tấn = 1.000 kg. */
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

	/** Bán lẻ 1 tạ = 100 kg; đơn vị tạ là mặc định trên phiếu bán. */
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
		requireCategory(request.categoryId());
		String code = blankToNull(request.productCode());
		if (code == null) {
			code = existing.productCode();
		}
		try {
			jdbc.update("""
					UPDATE products
					SET product_code = ?, name = ?, category_id = ?, specification = ?, base_unit_name = ?,
					    default_selling_price = ?, default_selling_price_ta = ?, image_path = ?,
					    description = ?, is_active = ?
					WHERE id = ?
					""",
					code, request.name().trim(), request.categoryId(),
					blankToNull(request.specification()), request.baseUnitName().trim(),
					request.defaultSellingPrice(), sellingPriceTaOrDerived(request),
					blankToNull(request.imagePath()), blankToNull(request.description()),
					request.active() == null || request.active(), id);
			jdbc.update("UPDATE product_units SET unit_name = ? WHERE product_id = ? AND is_base_unit = 1",
					request.baseUnitName().trim(), id);
		} catch (DuplicateKeyException ex) {
			throw duplicate("Mã nhôm hoặc tên đơn vị đã tồn tại");
		}
		String baseUnit = request.baseUnitName().trim();
		ensureRetailTaUnit(id, baseUnit);
		ensureTonUnit(id, baseUnit);
		// Giá nhập chỉ mở mốc mới khi thực sự đổi, tránh làm rác lịch sử mỗi lần lưu sản phẩm.
		BigDecimal newPrice = request.referencePurchasePrice();
		ResolvedPrice current = purchasePrices.current(id);
		if (newPrice != null && (current == null || newPrice.compareTo(current.pricePerBase()) != 0)) {
			purchasePrices.record(id, newPrice, LocalDate.now(clock), "Sửa giá nhập trong danh mục");
		}
		audit.record(CurrentAdmin.get().id(), "UPDATE_PRODUCT", "products", String.valueOf(id), code);
		return product(id);
	}

	@Transactional(readOnly = true)
	public List<PurchasePriceDto> purchasePriceHistory(long productId) {
		product(productId);
		return purchasePrices.history(productId).stream()
				.map(row -> new PurchasePriceDto(
						row.id(), row.effectiveFrom(), row.effectiveTo(),
						row.pricePerBase(), row.note(), row.open(), row.future()))
				.toList();
	}

	@Transactional
	public List<PurchasePriceDto> recordPurchasePrice(long productId, PurchasePriceRequest request) {
		product(productId);
		ResolvedPrice saved = purchasePrices.record(
				productId, request.pricePerBase(), request.effectiveFrom(), request.note());
		audit.record(CurrentAdmin.get().id(), "SET_PURCHASE_PRICE", "product_purchase_prices",
				String.valueOf(saved.priceId()),
				"Giá nhập " + productId + " = " + saved.pricePerBase().toPlainString()
						+ " từ " + saved.effectiveFrom());
		return purchasePriceHistory(productId);
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

	private CategoryDto category(long id) {
		return jdbc.query("""
				SELECT id, name, description, is_active FROM product_categories WHERE id = ?
				""", (rs, i) -> new CategoryDto(
				rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getBoolean("is_active")),
				id).stream().findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy loại nhôm", null));
	}

	private void requireCategory(long id) {
		category(id);
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

	/** Cột ta giữ đồng bộ từ giá / kg (×100); UI chỉ nhập giá / tấn. */
	private static BigDecimal sellingPriceTaOrDerived(ProductRequest request) {
		if (request.defaultSellingPriceTa() != null) {
			return request.defaultSellingPriceTa();
		}
		BigDecimal perKg = request.defaultSellingPrice() == null ? BigDecimal.ZERO : request.defaultSellingPrice();
		return perKg.multiply(BigDecimal.valueOf(100));
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static BigDecimal orZero(BigDecimal value) {
		return value == null ? BigDecimal.ZERO : value;
	}

	private record UnitRow(long id, BigDecimal factor, boolean base) {
	}
}
