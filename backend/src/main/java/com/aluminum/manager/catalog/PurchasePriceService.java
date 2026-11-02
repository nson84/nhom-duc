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
 * Lịch sử giá nhập theo khoảng hiệu lực.
 *
 * <p>Giá nhập thay đổi theo ngày nên không được ghi đè một ô duy nhất. Mỗi lần Admin sửa giá, một dòng
 * lịch sử mới mở ra từ ngày sửa và dòng cũ được đóng lại. Nhờ vậy phiếu nhập lùi ngày vẫn lấy đúng giá
 * của ngày đó, còn chi phí chuẩn của phiếu đã xác nhận thì đã snapshot nên không bị lệch về sau.
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
			/** True khi đây là mốc đang áp dụng hôm nay. */
			boolean open,
			/** True khi mốc được đặt cho một ngày trong tương lai. */
			boolean future
	) {
	}

	/** Giá đã chốt cho một dòng phiếu: giữ cả id lịch sử để truy vết. */
	public record ResolvedPrice(Long priceId, BigDecimal pricePerBase, LocalDate effectiveFrom) {
	}

	@Transactional(readOnly = true)
	public List<PriceRow> history(long productId) {
		LocalDate today = LocalDate.now(clock);
		ResolvedPrice current = resolve(productId, today);
		Long currentId = current == null ? null : current.priceId();
		return jdbc.query("""
				SELECT id, effective_from, effective_to, price_per_base, note
				FROM product_purchase_prices
				WHERE product_id = ?
				ORDER BY effective_from DESC, id DESC
				""", (rs, i) -> {
			long id = rs.getLong("id");
			LocalDate from = rs.getDate("effective_from").toLocalDate();
			LocalDate to = rs.getDate("effective_to") == null ? null : rs.getDate("effective_to").toLocalDate();
			return new PriceRow(
					id, from, to, rs.getBigDecimal("price_per_base"), rs.getString("note"),
					currentId != null && currentId == id, from.isAfter(today));
		}, productId);
	}

	@Transactional(readOnly = true)
	public ResolvedPrice current(long productId) {
		return resolve(productId, LocalDate.now(clock));
	}

	/**
	 * Giá có hiệu lực tại {@code asOf}. Trả về {@code null} khi sản phẩm chưa có lịch sử giá hoặc khi
	 * ngày tra cứu nằm trước mốc giá sớm nhất.
	 *
	 * <p>Không mượn mốc sớm nhất cho ngày trước nó: đó là ngày chưa có giá chuẩn nên để trống còn hơn
	 * gán một con số không có thật rồi tính lãi chuẩn sai. Nhiều mốc cùng ngày thì mốc có id lớn hơn thắng.
	 */
	@Transactional(readOnly = true)
	public ResolvedPrice resolve(long productId, LocalDate asOf) {
		LocalDate date = asOf == null ? LocalDate.now(clock) : asOf;
		List<ResolvedPrice> rows = jdbc.query("""
				SELECT id, price_per_base, effective_from
				FROM product_purchase_prices
				WHERE product_id = ? AND effective_from <= ?
				ORDER BY effective_from DESC, id DESC
				LIMIT 1
				""", (rs, i) -> new ResolvedPrice(
				rs.getLong("id"), rs.getBigDecimal("price_per_base"),
				rs.getDate("effective_from").toLocalDate()), productId, java.sql.Date.valueOf(date));
		return rows.isEmpty() ? null : rows.get(0);
	}

	/**
	 * Giá hiệu lực tại {@code asOf} của nhiều sản phẩm trong một lượt đọc, tránh N+1 khi mở danh mục.
	 * Sản phẩm không có mốc giá nào trước ngày tra cứu sẽ không xuất hiện trong map.
	 */
	@Transactional(readOnly = true)
	public Map<Long, ResolvedPrice> resolveAll(Collection<Long> productIds, LocalDate asOf) {
		Map<Long, ResolvedPrice> result = new HashMap<>();
		if (productIds == null || productIds.isEmpty()) {
			return result;
		}
		LocalDate date = asOf == null ? LocalDate.now(clock) : asOf;
		String placeholders = String.join(",", Collections.nCopies(productIds.size(), "?"));
		List<Object> args = new ArrayList<>(productIds);
		args.add(java.sql.Date.valueOf(date));
		jdbc.query("""
				WITH ranked AS (
				    SELECT product_id, id, price_per_base, effective_from,
				           ROW_NUMBER() OVER (
				               PARTITION BY product_id ORDER BY effective_from DESC, id DESC
				           ) AS rn
				    FROM product_purchase_prices
				    WHERE product_id IN (%s) AND effective_from <= ?
				)
				SELECT product_id, id, price_per_base, effective_from FROM ranked WHERE rn = 1
				""".formatted(placeholders), rs -> {
			result.put(rs.getLong("product_id"), new ResolvedPrice(
					rs.getLong("id"), rs.getBigDecimal("price_per_base"),
					rs.getDate("effective_from").toLocalDate()));
		}, args.toArray());
		// Sản phẩm có lịch sử nhưng toàn bộ nằm sau ngày tra cứu: không gán giá, coi như chưa có giá chuẩn.
		return result;
	}

	/**
	 * Ghi một mốc giá mới.
	 *
	 * <p>Quy tắc: lịch sử đã dùng trên phiếu thì không sửa. Mốc đang hiệu lực tại ngày ghi chỉ được sửa
	 * tại chỗ khi chưa phiếu nào dùng nó; nếu đã dùng thì mở mốc mới. Mốc đã đặt cho ngày tương lai không
	 * chặn việc chỉnh giá hôm nay.
	 */
	@Transactional
	public ResolvedPrice record(long productId, BigDecimal pricePerBase, LocalDate effectiveFrom, String note) {
		BigDecimal price = requirePrice(pricePerBase);
		LocalDate from = effectiveFrom == null ? LocalDate.now(clock) : effectiveFrom;

		Integer total = jdbc.queryForObject(
				"SELECT COUNT(*) FROM product_purchase_prices WHERE product_id = ?", Integer.class, productId);
		if (total == null || total == 0) {
			long firstId = insert(productId, from, price, blank(note));
			refreshCatalogPrice(productId);
			return new ResolvedPrice(firstId, price, from);
		}

		// Mốc đang hiệu lực đúng vào ngày ghi (mốc muộn nhất có effective_from <= from).
		Long inEffectId = null;
		LocalDate inEffectFrom = null;
		List<Object[]> inEffect = jdbc.query("""
				SELECT id, effective_from FROM product_purchase_prices
				WHERE product_id = ? AND effective_from <= ?
				ORDER BY effective_from DESC, id DESC LIMIT 1
				""", (rs, i) -> new Object[] {
						rs.getLong("id"), rs.getDate("effective_from").toLocalDate()},
				productId, java.sql.Date.valueOf(from)).stream().toList();
		if (!inEffect.isEmpty()) {
			inEffectId = (Long) inEffect.get(0)[0];
			inEffectFrom = (LocalDate) inEffect.get(0)[1];
		}
		if (inEffectId == null) {
			// Ngày ghi nằm trước mọi mốc đã có: ghi vào đây là viết lại quá khứ đã chốt.
			LocalDate earliest = jdbc.queryForObject("""
					SELECT MIN(effective_from) FROM product_purchase_prices WHERE product_id = ?
					""", java.sql.Date.class, productId).toLocalDate();
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Ngày hiệu lực không được trước mốc giá sớm nhất (" + earliest + "). "
							+ "Giá quá khứ không sửa để không làm lệch chi phí đã chốt", null);
		}

		BigDecimal currentPrice = jdbc.queryForObject(
				"SELECT price_per_base FROM product_purchase_prices WHERE id = ?", BigDecimal.class, inEffectId);
		Integer used = jdbc.queryForObject(
				"SELECT COUNT(*) FROM stock_document_items WHERE purchase_price_id = ?", Integer.class, inEffectId);
		boolean usedByDocuments = used != null && used > 0;
		boolean priceChanged = currentPrice == null || currentPrice.compareTo(price) != 0;
		boolean sameDay = inEffectFrom.equals(from);

		long priceId;
		if (sameDay && !usedByDocuments) {
			// Chưa phiếu nào dùng mốc này: sửa tại chỗ cho lịch sử gọn.
			jdbc.update("""
					UPDATE product_purchase_prices SET price_per_base = ?, note = ?
					WHERE id = ?
					""", price, blank(note), inEffectId);
			priceId = inEffectId;
		} else if (sameDay && !priceChanged) {
			// Cùng ngày, cùng giá: chỉ cập nhật ghi chú, không tạo mốc thừa.
			jdbc.update("UPDATE product_purchase_prices SET note = ? WHERE id = ?", blank(note), inEffectId);
			priceId = inEffectId;
		} else {
			// Mốc cũ đã dùng trên phiếu, hoặc ngày ghi muộn hơn: luôn mở mốc mới, không ghi đè lịch sử.
			priceId = insert(productId, from, price, blank(note));
		}
		recalculateRanges(productId);
		refreshCatalogPrice(productId);
		return new ResolvedPrice(priceId, price, from);
	}

	/** Cột tổng hợp trên products chỉ phản chiếu giá đang có hiệu lực hôm nay, không phải mốc tương lai. */
	private void refreshCatalogPrice(long productId) {
		ResolvedPrice today = resolve(productId, LocalDate.now(clock));
		if (today != null) {
			jdbc.update("UPDATE products SET reference_purchase_price = ? WHERE id = ?",
					today.pricePerBase(), productId);
		}
	}

	/**
	 * Tính lại khoảng hiệu lực cho toàn bộ lịch sử giá của một sản phẩm.
	 *
	 * <p>Nhờ vậy lịch sử luôn liền mạch và chỉ mốc cuối là "đang áp dụng", kể cả khi Admin đặt giá tương
	 * lai rồi quay lại chỉnh giá hôm nay. Nhiều mốc cùng ngày: mốc cũ đóng ngay trong ngày đó (id lớn hơn
	 * thắng khi tra cứu), không vi phạm ràng buộc ngày.
	 */
	private void recalculateRanges(long productId) {
		List<Object[]> rows = jdbc.query("""
				SELECT id, effective_from FROM product_purchase_prices
				WHERE product_id = ? ORDER BY effective_from ASC, id ASC
				""", (rs, i) -> new Object[] {
						rs.getLong("id"), rs.getDate("effective_from").toLocalDate()}, productId).stream().toList();
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
			jdbc.update("UPDATE product_purchase_prices SET effective_to = ? WHERE id = ?", to, id);
		}
	}

	private long insert(long productId, LocalDate from, BigDecimal price, String note) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO product_purchase_prices (product_id, effective_from, price_per_base, note)
					VALUES (?, ?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, productId);
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
