package com.aluminum.manager.factory;

import com.aluminum.manager.auth.UserRole;
import com.aluminum.manager.common.ApiResponse;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.security.CurrentAdmin;
import com.aluminum.manager.shop.ShopAccess;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Báo cáo quản trị gộp: chỉ tính giao dịch với bên ngoài một lần. */
@RestController
@RequestMapping("/api/v1/admin/combined")
public class AdminCombinedController {
	private final JdbcTemplate jdbc;
	private final ShopAccess shops;

	public AdminCombinedController(JdbcTemplate jdbc, ShopAccess shops) {
		this.jdbc = jdbc;
		this.shops = shops;
	}

	public record Branch(long shopId, String name, LocalDate periodFrom, LocalDate periodTo,
			BigDecimal salesRevenue, BigDecimal scrapRevenue,
			BigDecimal cashCollected, BigDecimal cashIn, BigDecimal cashOut, BigDecimal netCash,
			BigDecimal customerDebt, BigDecimal externalPurchases,
			BigDecimal materialCost, BigDecimal payrollCost, BigDecimal otherExpenses,
			BigDecimal estimatedProfit, boolean inventoryCountReady) {
	}

	public record Combined(int year, Branch distribution, Branch manufacturing,
			BigDecimal revenue, BigDecimal cashCollected, BigDecimal cashIn,
			BigDecimal cashOut, BigDecimal netCash, BigDecimal customerDebt,
			BigDecimal externalPurchases, BigDecimal payrollCost, BigDecimal otherExpenses,
			BigDecimal estimatedProfit) {
	}

	@GetMapping("/years/{year}")
	public ApiResponse<Combined> year(@PathVariable int year) {
		if (CurrentAdmin.get().role() != UserRole.ADMIN) throw new BusinessException(
				ErrorCode.FORBIDDEN, "Chỉ Admin xem được tổng hợp hai bên", null);
		if (year < 2000 || year > 2100) throw new BusinessException(ErrorCode.VALIDATION_ERROR,
				"Năm không hợp lệ", null);
		Branch distribution = branch(shops.idOf("DISTRIBUTION"), "Phân phối", year, false);
		Branch manufacturing = branch(shops.idOf("MANUFACTURING"), "Xưởng", year, true);
		BigDecimal profit = manufacturing.estimatedProfit() == null || distribution.estimatedProfit() == null ? null
				: distribution.estimatedProfit().add(manufacturing.estimatedProfit());
		return ApiResponse.ok(new Combined(year, distribution, manufacturing,
				distribution.salesRevenue().add(manufacturing.salesRevenue()).add(manufacturing.scrapRevenue()),
				distribution.cashCollected().add(manufacturing.cashCollected()),
				distribution.cashIn().add(manufacturing.cashIn()),
				distribution.cashOut().add(manufacturing.cashOut()),
				distribution.netCash().add(manufacturing.netCash()),
				distribution.customerDebt().add(manufacturing.customerDebt()),
				distribution.externalPurchases().add(manufacturing.externalPurchases()),
				manufacturing.payrollCost(),
				distribution.otherExpenses().add(manufacturing.otherExpenses()), profit), null);
	}

	private Branch branch(long shopId, String name, int year, boolean factory) {
		var periods = jdbc.query("SELECT period_from, period_to FROM year_end_balances WHERE shop_id = ? AND year_number = ?",
				(rs, i) -> new LocalDate[]{rs.getDate(1).toLocalDate(), rs.getDate(2).toLocalDate()}, shopId, year);
		if (periods.isEmpty() && factory) periods = jdbc.query("SELECT period_from, period_to FROM factory_year_counts WHERE year_number = ?",
				(rs, i) -> new LocalDate[]{rs.getDate(1).toLocalDate(), rs.getDate(2).toLocalDate()}, year);
		LocalDate[] period = periods.isEmpty() ? new LocalDate[]{LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31)} : periods.get(0);
		Timestamp from = Timestamp.valueOf(period[0].atStartOfDay());
		Timestamp until = Timestamp.valueOf(period[1].plusDays(1).atStartOfDay());
		BigDecimal sales = sum("""
				SELECT COALESCE(SUM(d.total_amount), 0) FROM stock_documents d
				WHERE d.shop_id = ? AND d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
				  AND d.is_internal = 0 AND d.document_date >= ? AND d.document_date < ?
				""", shopId, from, until);
		BigDecimal scrap = factory ? sum("""
				SELECT COALESCE(SUM(total_amount), 0) FROM factory_scrap_sales
				WHERE shop_id = ? AND status = 'CONFIRMED' AND sale_date >= ? AND sale_date < ?
				""", shopId, from, until) : BigDecimal.ZERO;
		BigDecimal cash = sum("""
				SELECT COALESCE(SUM(p.amount), 0) FROM sales_payments p
				JOIN stock_documents d ON d.id = p.document_id
				WHERE d.shop_id = ? AND d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
				  AND d.is_internal = 0 AND p.payment_date >= ? AND p.payment_date < ?
				""", shopId, from, until);
		if (factory) cash = cash.add(sum("""
				SELECT COALESCE(SUM(p.amount), 0) FROM factory_scrap_payments p
				JOIN factory_scrap_sales s ON s.id = p.scrap_sale_id
				WHERE s.shop_id = ? AND s.status = 'CONFIRMED'
				  AND p.payment_date >= ? AND p.payment_date < ?
				""", shopId, from, until));
		BigDecimal cashIn = sum("""
				SELECT COALESCE(SUM(amount), 0) FROM financial_transactions
				WHERE shop_id = ? AND status = 'CONFIRMED' AND is_internal = 0
				  AND transaction_type = 'INCOME' AND transaction_date >= ? AND transaction_date < ?
				""", shopId, from, until);
		BigDecimal cashOut = sum("""
				SELECT COALESCE(SUM(amount), 0) FROM financial_transactions
				WHERE shop_id = ? AND status = 'CONFIRMED' AND is_internal = 0
				  AND transaction_type = 'EXPENSE' AND transaction_date >= ? AND transaction_date < ?
				""", shopId, from, until);
		BigDecimal debt = sum("""
				SELECT COALESCE(SUM(d.total_amount), 0) FROM stock_documents d
				WHERE d.shop_id = ? AND d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
				  AND d.is_internal = 0 AND d.document_date < ?
				""", shopId, until).subtract(sum("""
				SELECT COALESCE(SUM(p.amount), 0) FROM sales_payments p
				JOIN stock_documents d ON d.id = p.document_id
				WHERE d.shop_id = ? AND d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
				  AND d.is_internal = 0 AND d.document_date < ? AND p.payment_date < ?
				""", shopId, until, until));
		if (factory) debt = debt.add(sum("""
				SELECT COALESCE(SUM(total_amount), 0) FROM factory_scrap_sales
				WHERE shop_id = ? AND status = 'CONFIRMED' AND sale_date < ?
				""", shopId, until)).subtract(sum("""
				SELECT COALESCE(SUM(p.amount), 0) FROM factory_scrap_payments p
				JOIN factory_scrap_sales s ON s.id = p.scrap_sale_id
				WHERE s.shop_id = ? AND s.status = 'CONFIRMED'
				  AND s.sale_date < ? AND p.payment_date < ?
				""", shopId, until, until));
		BigDecimal externalPurchases = sum("""
				SELECT COALESCE(SUM(d.total_amount), 0) FROM stock_documents d
				WHERE d.shop_id = ? AND d.document_type = 'PURCHASE' AND d.status = 'CONFIRMED'
				  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
				  AND d.is_internal = 0 AND d.document_date >= ? AND d.document_date < ?
				""", shopId, from, until);
		BigDecimal expenses = sum("""
				SELECT COALESCE(SUM(amount), 0) FROM financial_transactions
				WHERE shop_id = ? AND status = 'CONFIRMED' AND transaction_type = 'EXPENSE'
				  AND (category IN ('OPERATING_EXPENSE', 'MAINTENANCE', 'OTHER_EXPENSE')
				       OR (category = 'PAYROLL' AND reference_type = 'MANUAL'))
				  AND transaction_date >= ? AND transaction_date < ?
				""", shopId, from, until);
		BigDecimal payroll = factory ? sum("""
				SELECT COALESCE(SUM(GREATEST(pd.base_pay + pd.overtime_pay + pd.bonus_pay + pd.allowances - pd.deductions, 0)
				                    - pd.holiday_bonus_pay), 0) FROM payroll_details pd
				JOIN payroll_periods pp ON pp.id = pd.payroll_period_id
				JOIN employees e ON e.id = pd.employee_id
				WHERE e.shop_id = ?
				  AND LAST_DAY(MAKEDATE(pp.period_year, 1) + INTERVAL (pp.period_month - 1) MONTH) >= ?
				  AND LAST_DAY(MAKEDATE(pp.period_year, 1) + INTERVAL (pp.period_month - 1) MONTH) <= ?
				""", shopId, period[0], period[1]).add(sum("""
				SELECT COALESCE(SUM(h.bonus_amount), 0) FROM holiday_employee_bonuses b
				JOIN holidays h ON h.id = b.holiday_id JOIN employees e ON e.id = b.employee_id
				WHERE e.shop_id = ? AND h.holiday_date >= ? AND h.holiday_date <= ?
				""", shopId, period[0], period[1])) : BigDecimal.ZERO;
		BigDecimal material;
		BigDecimal profit;
		boolean ready = true;
		if (factory) {
			Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM factory_year_counts WHERE year_number = ? AND status = 'CLOSED'",
					Integer.class, year);
			ready = count != null && count > 0;
			if (ready) {
				BigDecimal opening = sum("SELECT COALESCE(opening_value, 0) FROM factory_year_counts WHERE year_number = ?", year);
				BigDecimal closing = sum("""
					SELECT raw_value + scrap_value + work_in_progress_value + finished_value
					FROM factory_year_counts WHERE year_number = ?
					""", year);
				BigDecimal allPurchases = sum("""
					SELECT COALESCE(SUM(d.total_amount), 0) FROM stock_documents d
					WHERE d.shop_id = ? AND d.document_type = 'PURCHASE' AND d.status = 'CONFIRMED'
					  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
					  AND d.document_date >= ? AND d.document_date < ?
					""", shopId, from, until);
				material = opening.add(allPurchases).subtract(closing);
				var snapshot = jdbc.query("SELECT profit FROM year_end_balances WHERE shop_id = ? AND year_number = ?",
						(rs, row) -> rs.getBigDecimal(1), shopId, year);
				profit = snapshot.isEmpty() ? sales.add(scrap).subtract(material).subtract(expenses).subtract(payroll) : snapshot.get(0);
			} else { material = null; profit = null; }
		} else {
			var closed = jdbc.query("SELECT profit FROM year_end_balances WHERE shop_id = ? AND year_number = ?",
					(rs, row) -> rs.getBigDecimal(1), shopId, year);
			if (closed.isEmpty()) closed = jdbc.query("""
					SELECT profit FROM distribution_period_closings
					WHERE shop_id = ? AND period_from = ? AND period_to = ?
					""", (rs, row) -> rs.getBigDecimal(1), shopId, period[0], period[1]);
			ready = !closed.isEmpty();
			profit = ready ? closed.get(0) : null;
			material = null;
		}
		return new Branch(shopId, name, period[0], period[1], sales, scrap, cash, cashIn, cashOut,
				cashIn.subtract(cashOut), debt, externalPurchases,
				material, payroll, expenses, profit, ready);
	}

	private BigDecimal sum(String sql, Object... args) {
		BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
		return value == null ? BigDecimal.ZERO : value;
	}
}
