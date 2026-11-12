package com.aluminum.manager.catalog;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lịch sử giá nhập theo loại nhôm (khoảng hiệu lực).
 */
@Service
public class PurchasePriceService {

	public static final int PRICE_SCALE = 6;
	private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

	private final JdbcTemplate jdbc;
	private final Clock clock;

	public PurchasePriceService(JdbcTemplate jdbc, Clock clock) {
		this.jdbc = jdbc;
		this.clock = clock;
	}

	public record PriceRow(
			Long id,
			LocalDate effectiveFrom,
			LocalDate effectiveTo,
			BigDecimal pricePerBase,
			String note,
			boolean open,
			boolean future
	) {
	}

	public record ResolvedPrice(Long priceId, BigDecimal pricePerBase, LocalDate effectiveFrom) {
	}

	@Transactional(readOnly = true)
	public List<PriceRow> history(long categoryId) {
		LocalDate today = LocalDate.now(clock);
		ResolvedPrice current = resolve(categoryId, today);
		Long currentId = current == null ? null : current.priceId();
		return jdbc.query("""
				SELECT id, effective_from, effective_to, price_per_base, note
				FROM category_purchase_prices
				WHERE category_id = ?
				ORDER BY effective_from DESC, id DESC
				""", (rs, i) -> {
			long id = rs.getLong("id");
			LocalDate from = rs.getDate("effective_from").toLocalDate();
			LocalDate to = rs.getDate("effective_to") == null ? null : rs.getDate("effective_to").toLocalDate();
			return new PriceRow(
					id, from, to, rs.getBigDecimal("price_per_base"), rs.getString("note"),
					currentId != null && currentId == id, from.isAfter(today));
		}, categoryId);
	}

	@Transactional(readOnly = true)
	public ResolvedPrice current(long categoryId) {
		return resolve(categoryId, LocalDate.now(clock));
	}

	@Transactional(readOnly = true)
	public ResolvedPrice resolve(long categoryId, LocalDate asOf) {
		LocalDate date = asOf == null ? LocalDate.now(clock) : asOf;
		List<ResolvedPrice> rows = jdbc.query("""
				SELECT id, price_per_base, effective_from
				FROM category_purchase_prices
				WHERE category_id = ? AND effective_from <= ?
				ORDER BY effective_from DESC, id DESC
				LIMIT 1
				""", (rs, i) -> new ResolvedPrice(
				rs.getLong("id"), rs.getBigDecimal("price_per_base"),
				rs.getDate("effective_from").toLocalDate()), categoryId, java.sql.Date.valueOf(date));
		return rows.isEmpty() ? null : rows.get(0);
	}

	@Transactional(readOnly = true)
	public Map<Long, ResolvedPrice> resolveAll(Collection<Long> categoryIds, LocalDate asOf) {
		Map<Long, ResolvedPrice> result = new HashMap<>();
		if (categoryIds == null || categoryIds.isEmpty()) {
			return result;
		}
		LocalDate date = asOf == null ? LocalDate.now(clock) : asOf;
		String placeholders = String.join(",", Collections.nCopies(categoryIds.size(), "?"));
		List<Object> args = new ArrayList<>(categoryIds);
		args.add(java.sql.Date.valueOf(date));
		jdbc.query("""
				WITH ranked AS (
				    SELECT category_id, id, price_per_base, effective_from,
				           ROW_NUMBER() OVER (
				               PARTITION BY category_id ORDER BY effective_from DESC, id DESC
				           ) AS rn
				    FROM category_purchase_prices
				    WHERE category_id IN (%s) AND effective_from <= ?
				)
				SELECT category_id, id, price_per_base, effective_from FROM ranked WHERE rn = 1
				""".formatted(placeholders), rs -> {
			result.put(rs.getLong("category_id"), new ResolvedPrice(
					rs.getLong("id"), rs.getBigDecimal("price_per_base"),
					rs.getDate("effective_from").toLocalDate()));
		}, args.toArray());
		return result;
	}

	@Transactional
	public ResolvedPrice record(long categoryId, BigDecimal pricePerBase, LocalDate effectiveFrom, String note) {
		BigDecimal price = requirePrice(pricePerBase);
		LocalDate from = effectiveFrom == null ? LocalDate.now(clock) : effectiveFrom;

		Integer total = jdbc.queryForObject(
				"SELECT COUNT(*) FROM category_purchase_prices WHERE category_id = ?", Integer.class, categoryId);
		if (total == null || total == 0) {
			long firstId = insert(categoryId, from, price, blank(note));
			refreshCatalogPrice(categoryId);
			return new ResolvedPrice(firstId, price, from);
		}

		Long inEffectId = null;
		LocalDate inEffectFrom = null;
		List<Object[]> inEffect = jdbc.query("""
				SELECT id, effective_from FROM category_purchase_prices
				WHERE category_id = ? AND effective_from <= ?
				ORDER BY effective_from DESC, id DESC LIMIT 1
				""", (rs, i) -> new Object[] {
						rs.getLong("id"), rs.getDate("effective_from").toLocalDate()},
				categoryId, java.sql.Date.valueOf(from)).stream().toList();
		if (!inEffect.isEmpty()) {
			inEffectId = (Long) inEffect.get(0)[0];
			inEffectFrom = (LocalDate) inEffect.get(0)[1];
		}
		if (inEffectId == null) {
			LocalDate earliest = jdbc.queryForObject("""
					SELECT MIN(effective_from) FROM category_purchase_prices WHERE category_id = ?
					""", java.sql.Date.class, categoryId).toLocalDate();
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Ngày hiệu lực không được trước mốc giá sớm nhất (" + earliest + "). "
							+ "Giá quá khứ không sửa để không làm lệch chi phí đã chốt", null);
		}

		BigDecimal currentPrice = jdbc.queryForObject(
				"SELECT price_per_base FROM category_purchase_prices WHERE id = ?", BigDecimal.class, inEffectId);
		boolean priceChanged = currentPrice == null || currentPrice.compareTo(price) != 0;
		boolean sameDay = inEffectFrom.equals(from);

		long priceId;
		if (sameDay && !priceChanged) {
			jdbc.update("UPDATE category_purchase_prices SET note = ? WHERE id = ?", blank(note), inEffectId);
			priceId = inEffectId;
		} else {
			priceId = insert(categoryId, from, price, blank(note));
		}
		recalculateRanges(categoryId);
		refreshCatalogPrice(categoryId);
		return new ResolvedPrice(priceId, price, from);
	}

	private void refreshCatalogPrice(long categoryId) {
		ResolvedPrice today = resolve(categoryId, LocalDate.now(clock));
		if (today != null) {
			jdbc.update("UPDATE product_categories SET reference_purchase_price = ? WHERE id = ?",
					today.pricePerBase(), categoryId);
		}
	}

	private void recalculateRanges(long categoryId) {
		List<Object[]> rows = jdbc.query("""
				SELECT id, effective_from FROM category_purchase_prices
				WHERE category_id = ? ORDER BY effective_from ASC, id ASC
				""", (rs, i) -> new Object[] {
						rs.getLong("id"), rs.getDate("effective_from").toLocalDate()}, categoryId).stream().toList();
		for (int i = 0; i < rows.size(); i++) {
			long id = (Long) rows.get(i)[0];
			LocalDate from = (LocalDate) rows.get(i)[1];
			java.sql.Date to;
			if (i == rows.size() - 1) {
				to = null;
			} else {
				LocalDate nextFrom = (LocalDate) rows.get(i + 1)[1];
				to = java.sql.Date.valueOf(nextFrom.isAfter(from) ? nextFrom.minusDays(1) : from);
			}
			jdbc.update("UPDATE category_purchase_prices SET effective_to = ? WHERE id = ?", to, id);
		}
	}

	private long insert(long categoryId, LocalDate from, BigDecimal price, String note) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO category_purchase_prices (category_id, effective_from, price_per_base, note)
					VALUES (?, ?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, categoryId);
			ps.setDate(2, java.sql.Date.valueOf(from));
			ps.setBigDecimal(3, price);
			ps.setString(4, note);
			return ps;
		}, keys);
		return keys.getKey().longValue();
	}

	private static BigDecimal requirePrice(BigDecimal value) {
		if (value == null) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Nhập giá nhập", null);
		}
		if (value.signum() < 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Giá nhập không được âm", null);
		}
		return value.setScale(PRICE_SCALE, ROUNDING);
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}
}
