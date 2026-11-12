package com.aluminum.manager.dashboard;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.shop.ShopAccess;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

	private static final int MONEY_SCALE = 4;
	private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

	private final JdbcTemplate jdbc;
	private final Clock clock;
	private final ShopAccess shops;
	private final YearEndBalanceService yearEndBalances;

	public DashboardService(JdbcTemplate jdbc, Clock clock, ShopAccess shops, YearEndBalanceService yearEndBalances) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.shops = shops;
		this.yearEndBalances = yearEndBalances;
	}

	@Transactional(readOnly = true)
	public DashboardSummary summary(LocalDate from, LocalDate to) {
		DateRange range = range(from, to);
		BigDecimal revenue = money("""
				SELECT COALESCE(SUM(total_amount), 0)
				FROM stock_documents
				WHERE document_type = 'SALE'
				  AND status = 'CONFIRMED'
				  AND document_date >= ? AND document_date < ?
				""", range);
		BigDecimal purchaseAmount = money("""
				SELECT COALESCE(SUM(total_amount), 0) FROM stock_documents
				WHERE document_type = 'PURCHASE' AND status = 'CONFIRMED'
				  AND document_date >= ? AND document_date < ?
				""", range);
		BigDecimal deliveredKg = money("""
				SELECT COALESCE(SUM(i.quantity_base), 0)
				FROM stock_document_items i JOIN stock_documents d ON d.id = i.document_id
				WHERE d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND d.document_date >= ? AND d.document_date < ?
				""", range);
		BigDecimal cogs = money("""
				SELECT COALESCE(SUM(i.cost_total_snapshot), 0)
				FROM stock_document_items i
				JOIN stock_documents d ON d.id = i.document_id
				WHERE d.document_type = 'SALE'
				  AND d.status = 'CONFIRMED'
				  AND d.document_date >= ? AND d.document_date < ?
				""", range);
		BigDecimal cash = money("""
				SELECT COALESCE(SUM(p.amount), 0)
				FROM sales_payments p
				JOIN stock_documents d ON d.id = p.document_id
				WHERE p.payment_date >= ? AND p.payment_date < ?
				""", range);
		BigDecimal expenses = money("""
				SELECT COALESCE(SUM(amount), 0)
				FROM financial_transactions
				WHERE transaction_type = 'EXPENSE'
				  AND status = 'CONFIRMED'
				  AND category IN ('OPERATING_EXPENSE', 'MAINTENANCE', 'PAYROLL', 'OTHER_EXPENSE')
				  AND transaction_date >= ? AND transaction_date < ?
				""", range);
		BigDecimal inventory = scale(jdbc.queryForObject("""
				SELECT COALESCE(SUM(b.quantity_base * b.average_cost_per_base), 0)
				FROM stock_balances b
				JOIN products p ON p.id = b.product_id
				WHERE p.shop_id = ?
				""", BigDecimal.class, shops.currentId()));
		BigDecimal payroll = shops.isDistribution()
				? BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING)
				: scale(jdbc.queryForObject("""
				SELECT COALESCE(SUM(pd.total_pay), 0)
				FROM payroll_details pd
				JOIN payroll_periods pp ON pp.id = pd.payroll_period_id
				WHERE pp.period_year = ? AND pp.period_month = ?
				""", BigDecimal.class, range.to().getYear(), range.to().getMonthValue()));
		return new DashboardSummary(
				range.from(),
				range.to(),
				revenue,
				cash,
				cogs,
				revenue.subtract(cogs).setScale(MONEY_SCALE, ROUNDING),
				expenses,
				inventory,
				payroll,
				debts(range, true),
				shops.isDistribution() ? debts(range, false) : null,
				shops.isDistribution() ? cashExpenses(range) : null,
				shops.isDistribution() ? closing(range) : null,
				purchaseAmount,
				deliveredKg);
	}

	private BigDecimal cashExpenses(DateRange range) {
		return money("""
				SELECT COALESCE(SUM(amount), 0) FROM financial_transactions
				WHERE transaction_type = 'EXPENSE' AND status = 'CONFIRMED'
				  AND transaction_date >= ? AND transaction_date < ?
				""", range);
	}

	private BigDecimal debts(DateRange range, boolean customer) {
		Timestamp end = Timestamp.valueOf(range.to().plusDays(1).atStartOfDay());
		String paid = customer
				? "SELECT SUM(p.amount) FROM sales_payments p WHERE p.document_id = d.id AND p.payment_date < ?"
				: "SELECT SUM(f.amount) FROM financial_transactions f WHERE f.reference_type = 'STOCK_DOCUMENT'"
				  + " AND f.reference_id = d.id AND f.category = 'PURCHASE_PAYMENT' AND f.status = 'CONFIRMED' AND f.transaction_date < ?";
		return jdbc.queryForObject("""
				SELECT COALESCE(SUM(GREATEST(d.total_amount - COALESCE((
				""" + paid + """
				), 0), 0)), 0) FROM stock_documents d
				WHERE d.shop_id = ? AND d.document_type = ? AND d.status = 'CONFIRMED'
				  AND d.is_internal = 0 AND d.document_date < ?
				""", BigDecimal.class, end, shops.currentId(), customer ? "SALE" : "PURCHASE", end);
	}

	private DashboardSummary.Closing closing(DateRange range) {
		return jdbc.query("""
				SELECT quantity_kg, inventory_value, profit FROM distribution_period_closings
				WHERE shop_id = ? AND period_from = ? AND period_to = ?
				""", (rs, i) -> new DashboardSummary.Closing(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getBigDecimal(3)),
				shops.currentId(), range.from(), range.to()).stream().findFirst().orElse(null);
	}

	@Transactional
	public DashboardSummary.Closing closeDistribution(LocalDate from, LocalDate to, boolean annual, Integer year) {
		if (!shops.isDistribution()) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ chốt kỳ Phân phối tại đây", null);
		DateRange range = range(from, to);
		if (range.to().isAfter(LocalDate.now(clock))) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không chốt ngày tương lai", null);
		Integer missing = jdbc.queryForObject("""
				SELECT COUNT(*) FROM stock_document_items i JOIN stock_documents d ON d.id = i.document_id
				WHERE d.shop_id = ? AND d.document_type IN ('PURCHASE', 'SALE')
				  AND d.status = 'CONFIRMED' AND d.document_date < ?
				  AND NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.document_item_id = i.id)
				""", Integer.class, shops.currentId(), Timestamp.valueOf(range.to().plusDays(1).atStartOfDay()));
		if (missing != null && missing > 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR,
				"Có phiếu cũ thiếu biến động kho. Cần đối chiếu tồn trước khi chốt", null);
		// Không chốt nếu chưa cân đủ từng cặp SP/loại đã có giao dịch kho.
		// Số kiểm kê phải là dữ liệu mới nhất và tồn thực tế phải còn khớp.
		// Không dùng kiểm kê ngày hôm nay để giả định số tồn của một ngày quá khứ
		// nếu đã phát sinh giao dịch kho sau ngày kết thúc kỳ.
		Timestamp cutoff = Timestamp.valueOf(range.to().plusDays(1).atStartOfDay());
		Integer uncounted = jdbc.queryForObject("""
				SELECT COUNT(*) FROM stock_balances b
				JOIN products p ON p.id = b.product_id
				LEFT JOIN distribution_inventory_count_evidence c
				  ON c.shop_id = p.shop_id AND c.product_id = b.product_id AND c.category_id = b.category_id
				WHERE p.shop_id = ?
				  AND EXISTS (SELECT 1 FROM stock_movements m WHERE m.product_id = b.product_id
				              AND m.category_id = b.category_id AND m.movement_at < ?)
				  AND (c.counted_at IS NULL OR c.counted_at < ?
				    OR c.counted_quantity_kg <> b.quantity_base
				    OR EXISTS (SELECT 1 FROM stock_movements m WHERE m.product_id = b.product_id
				       AND m.category_id = b.category_id AND m.movement_at > c.counted_at))
				""", Integer.class, shops.currentId(), cutoff, Timestamp.valueOf(range.to().atStartOfDay()));
		if (uncounted != null && uncounted > 0) throw new BusinessException(
				ErrorCode.VALIDATION_ERROR,
				"Chưa kiểm kê đầy đủ hoặc đã phát sinh biến động kho sau kiểm kê: "
				+ uncounted + " cặp sản phẩm/loại. Vào Tồn kho → Kiểm kê để cân lại trước khi chốt", null);
		Integer laterMovements = jdbc.queryForObject("""
				SELECT COUNT(*) FROM stock_movements m JOIN products p ON p.id = m.product_id
				WHERE p.shop_id = ? AND m.movement_at >= ?
				""", Integer.class, shops.currentId(), cutoff);
		if (laterMovements != null && laterMovements > 0) throw new BusinessException(
				ErrorCode.VALIDATION_ERROR,
				"Đã có biến động kho sau ngày chốt; không thể lấy số cân hiện tại làm tồn cuối kỳ quá khứ. "
				+ "Cần đối chiếu kiểm kê theo đúng ngày kết thúc kỳ", null);
		Valuation opening = valuation(range.from());
		Valuation ending = valuation(range.to().plusDays(1));
		BigDecimal purchases = money("""
				SELECT COALESCE(SUM(total_amount), 0) FROM stock_documents
				WHERE document_type = 'PURCHASE' AND status = 'CONFIRMED'
				  AND document_date >= ? AND document_date < ?
				""", range);
		DashboardSummary summary = summary(from, to);
		BigDecimal profit = summary.revenue().subtract(opening.value()).subtract(purchases)
				.add(ending.value()).subtract(summary.operatingExpenses());
		jdbc.update("""
				INSERT INTO distribution_period_closings(shop_id, period_from, period_to, quantity_kg, inventory_value, profit)
				VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE quantity_kg = VALUES(quantity_kg),
				    inventory_value = VALUES(inventory_value), profit = VALUES(profit)
				""", shops.currentId(), range.from(), range.to(), ending.quantity(), ending.value(), profit);
		boolean calendarYear = range.from().equals(LocalDate.of(range.to().getYear(), 1, 1))
				&& range.to().equals(LocalDate.of(range.to().getYear(), 12, 31));
		if (annual || calendarYear) {
			int closingYear = annual && year != null ? year : range.to().getYear();
			if (closingYear < 2000 || closingYear > 2100) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chọn tên năm chốt hợp lệ", null);
			yearEndBalances.save(closingYear, range.from(), range.to(), ending.quantity(), ending.value(), profit);
		}
		return closing(range);
	}

	private Valuation valuation(LocalDate cutoff) {
		Timestamp end = Timestamp.valueOf(cutoff.atStartOfDay());
		var rows = jdbc.query("""
				WITH balances AS (
				    SELECT m.product_id, m.category_id, SUM(m.quantity_delta_base) AS qty
				    FROM stock_movements m JOIN products p ON p.id = m.product_id
				    WHERE p.shop_id = ? AND m.movement_at < ?
				    GROUP BY m.product_id, m.category_id
				    HAVING SUM(m.quantity_delta_base) <> 0
				), receipts AS (
				    SELECT i.product_id, i.category_id, i.quantity_base AS qty, i.line_total AS value,
				           SUM(i.quantity_base) OVER (PARTITION BY i.product_id, i.category_id
				             ORDER BY d.document_date DESC, d.id DESC, i.id DESC ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW)
				             - i.quantity_base AS prior_qty
				    FROM stock_document_items i JOIN stock_documents d ON d.id = i.document_id
				    WHERE d.shop_id = ? AND d.document_type = 'PURCHASE' AND d.document_date < ?
				      AND d.confirmed_at IS NOT NULL AND NOT EXISTS (
				        SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id AND r.document_date < ?
				      )
				)
				SELECT b.qty, COALESCE(SUM(r.qty), 0) AS sourced,
				       COALESCE(SUM(LEAST(r.qty, GREATEST(b.qty - r.prior_qty, 0)) * r.value / r.qty), 0) AS value
				FROM balances b LEFT JOIN receipts r ON r.product_id = b.product_id AND r.category_id = b.category_id
				GROUP BY b.product_id, b.category_id, b.qty
				""", (rs, i) -> new BigDecimal[]{rs.getBigDecimal("qty"), rs.getBigDecimal("sourced"), rs.getBigDecimal("value")},
				shops.currentId(), end, shops.currentId(), end, end);
		BigDecimal quantity = BigDecimal.ZERO;
		BigDecimal value = BigDecimal.ZERO;
		for (var row : rows) {
			if (row[0].signum() < 0 || row[0].compareTo(row[1]) > 0) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR,
						"Tồn kho không khớp lịch sử phiếu nhập tại " + cutoff.minusDays(1) + ". Hãy đối chiếu trước khi chốt", null);
			}
			quantity = quantity.add(row[0]);
			value = value.add(row[2]);
		}
		return new Valuation(quantity, scale(value));
	}

	private record Valuation(BigDecimal quantity, BigDecimal value) {}

	@Transactional(readOnly = true)
	public List<DashboardPoint> charts(LocalDate from, LocalDate to) {
		DateRange range = range(from, to);
		Timestamp start = Timestamp.valueOf(range.from().atStartOfDay());
		Timestamp end = Timestamp.valueOf(range.to().plusDays(1).atStartOfDay());
		Map<LocalDate, MutablePoint> points = new LinkedHashMap<>();
		for (LocalDate day = range.from(); !day.isAfter(range.to()); day = day.plusDays(1)) {
			points.put(day, new MutablePoint());
		}
		jdbc.query(shops.apply("""
				SELECT DATE(document_date) AS day,
				       COALESCE(SUM(total_amount), 0) AS revenue
				FROM stock_documents
				WHERE document_type = 'SALE'
				  AND status = 'CONFIRMED'
				  AND document_date >= ? AND document_date < ?
				GROUP BY DATE(document_date)
				"""), rs -> {
			LocalDate day = rs.getDate("day").toLocalDate();
			MutablePoint point = points.get(day);
			if (point != null) {
				point.revenue = scale(rs.getBigDecimal("revenue"));
			}
		}, start, end);
		jdbc.query(shops.apply("""
				SELECT DATE(d.document_date) AS day,
				       COALESCE(SUM(i.cost_total_snapshot), 0) AS cogs
				FROM stock_document_items i
				JOIN stock_documents d ON d.id = i.document_id
				WHERE d.document_type = 'SALE'
				  AND d.status = 'CONFIRMED'
				  AND d.document_date >= ? AND d.document_date < ?
				GROUP BY DATE(d.document_date)
				"""), rs -> {
			LocalDate day = rs.getDate("day").toLocalDate();
			MutablePoint point = points.get(day);
			if (point != null) {
				point.cogs = scale(rs.getBigDecimal("cogs"));
			}
		}, start, end);
		jdbc.query(shops.apply("""
				SELECT DATE(p.payment_date) AS day, COALESCE(SUM(p.amount), 0) AS cash_collected
				FROM sales_payments p
				JOIN stock_documents d ON d.id = p.document_id
				WHERE p.payment_date >= ? AND p.payment_date < ?
				GROUP BY DATE(p.payment_date)
				"""), rs -> {
			LocalDate day = rs.getDate("day").toLocalDate();
			MutablePoint point = points.get(day);
			if (point != null) {
				point.cash = scale(rs.getBigDecimal("cash_collected"));
			}
		}, start, end);
		List<DashboardPoint> result = new ArrayList<>();
		points.forEach((day, point) -> result.add(new DashboardPoint(
				day,
				point.revenue,
				point.cash,
				point.cogs,
				point.revenue.subtract(point.cogs).setScale(MONEY_SCALE, ROUNDING))));
		return result;
	}

	private BigDecimal money(String sql, DateRange range) {
		return scale(jdbc.queryForObject(
				shops.apply(sql),
				BigDecimal.class,
				Timestamp.valueOf(range.from().atStartOfDay()),
				Timestamp.valueOf(range.to().plusDays(1).atStartOfDay())));
	}

	private DateRange range(LocalDate from, LocalDate to) {
		LocalDate today = LocalDate.now(clock);
		LocalDate start = from == null ? today.withDayOfMonth(1) : from;
		LocalDate end = to == null ? today : to;
		if (end.isBefore(start)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Ngày kết thúc phải sau hoặc bằng ngày bắt đầu", null);
		}
		if (start.plusDays(400).isBefore(end)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Khoảng thời gian báo cáo không quá 400 ngày", null);
		}
		return new DateRange(start, end);
	}

	private static BigDecimal scale(BigDecimal value) {
		if (value == null) {
			return BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
		}
		return value.setScale(MONEY_SCALE, ROUNDING);
	}

	private record DateRange(LocalDate from, LocalDate to) {
	}

	private static final class MutablePoint {
		private BigDecimal revenue = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
		private BigDecimal cash = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
		private BigDecimal cogs = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
	}
}
