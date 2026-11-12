package com.aluminum.manager.shop;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ShopAccess {

	private final JdbcTemplate jdbc;
	private volatile Long manufacturingId;
	private volatile Long distributionId;

	public ShopAccess(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public long currentId() {
		return idOf(ShopContext.code());
	}

	public long idOf(String code) {
		ensureLoaded();
		return ShopContext.DISTRIBUTION.equals(ShopContext.normalize(code)) ? distributionId : manufacturingId;
	}

	public boolean isDistribution() {
		return ShopContext.DISTRIBUTION.equals(ShopContext.code());
	}

	/** Shop còn lại (dùng nội bộ). Đồng bộ danh mục chỉ cho phép Phân phối → Xưởng ở CatalogService. */
	public String otherShopCode() {
		return isDistribution() ? ShopContext.MANUFACTURING : ShopContext.DISTRIBUTION;
	}

	public long otherShopId() {
		return idOf(otherShopCode());
	}

	public String shopName(String code) {
		ensureLoaded();
		String normalized = ShopContext.normalize(code);
		String name = jdbc.query("""
				SELECT name FROM shops WHERE code = ?
				""", (rs, i) -> rs.getString("name"), normalized).stream().findFirst().orElse(normalized);
		return name;
	}

	public void requireManufacturing() {
		if (isDistribution()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Phân phối nhôm khối không có nhân viên và không tính công", null);
		}
	}

	/**
	 * Lọc đúng cửa hàng. Khi xem gộp thì bỏ lọc cửa hàng và loại dòng nội bộ
	 * khỏi doanh thu / chi ngoài.
	 */
	public String scope(String alias) {
		return fragment(alias, true);
	}

	/** Chỉ lọc cửa hàng, vẫn giữ dòng nội bộ (sổ của từng cửa hàng). */
	public String shopOnly(String alias) {
		return fragment(alias, false);
	}

	public String apply(String sql) {
		if (sql.contains("shop_id =")) {
			return sql;
		}
		String fragment = fragmentFor(sql);
		if (fragment.isEmpty()) {
			return sql;
		}
		String lower = sql.toLowerCase();
		int group = lower.lastIndexOf("group by");
		int order = lower.lastIndexOf("order by");
		int cut = -1;
		if (group >= 0) {
			cut = group;
		}
		if (order >= 0 && (cut < 0 || order < cut)) {
			cut = order;
		}
		if (cut < 0) {
			return sql + fragment;
		}
		return sql.substring(0, cut) + fragment + " " + sql.substring(cut);
	}

	private String fragmentFor(String sql) {
		if (sql.contains("payroll_periods")) {
			return "";
		}
		if (sql.contains("FROM products p") && !sql.contains("stock_documents")) {
			return shopOnly("p");
		}
		if (sql.contains("stock_documents d")) {
			return scope("d");
		}
		if (sql.contains("FROM stock_documents")) {
			return scope(null);
		}
		if (sql.contains("financial_transactions ft")) {
			return scope("ft");
		}
		if (sql.contains("FROM financial_transactions")) {
			return scope(null);
		}
		return "";
	}

	private String fragment(String alias, boolean dropInternalWhenAll) {
		String prefix = alias == null || alias.isBlank() ? "" : alias + ".";
		StringBuilder sb = new StringBuilder();
		if (!ShopScope.all()) {
			sb.append(" AND ").append(prefix).append("shop_id = ").append(currentId());
		} else if (dropInternalWhenAll) {
			sb.append(" AND ").append(prefix).append("is_internal = 0");
		}
		return sb.toString();
	}

	private void ensureLoaded() {
		if (manufacturingId != null && distributionId != null) {
			return;
		}
		synchronized (this) {
			if (manufacturingId != null && distributionId != null) {
				return;
			}
			manufacturingId = jdbc.queryForObject(
					"SELECT id FROM shops WHERE code = 'MANUFACTURING'", Long.class);
			distributionId = jdbc.queryForObject(
					"SELECT id FROM shops WHERE code = 'DISTRIBUTION'", Long.class);
		}
	}
}
