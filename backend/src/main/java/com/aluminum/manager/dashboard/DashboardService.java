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

	public DashboardService(JdbcTemplate jdbc, Clock clock, ShopAccess shops) {
		this.jdbc = jdbc;
		this.clock = clock;
		this.shops = shops;
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
				  AND category IN ('OPERATING_EXPENSE', 'PAYROLL', 'OTHER_EXPENSE')
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
				payroll);
	}

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
		if (start.plusDays(366).isBefore(end)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Khoảng thời gian báo cáo không quá 366 ngày", null);
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
