package com.aluminum.manager.finance;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.finance.FinanceDtos.CreateTransactionRequest;
import com.aluminum.manager.finance.FinanceDtos.DailyRevenueRowDto;
import com.aluminum.manager.finance.FinanceDtos.FinanceSummaryDto;
import com.aluminum.manager.finance.FinanceDtos.InventoryReportRowDto;
import com.aluminum.manager.finance.FinanceDtos.PayrollReportRowDto;
import com.aluminum.manager.finance.FinanceDtos.ProductSalesRowDto;
import com.aluminum.manager.finance.FinanceDtos.ProfitReportDto;
import com.aluminum.manager.finance.FinanceDtos.PurchasePriceVarianceRowDto;
import com.aluminum.manager.finance.FinanceDtos.TransactionDto;
import com.aluminum.manager.inventory.DocumentSequenceService;
import com.aluminum.manager.inventory.InventoryMath;
import com.aluminum.manager.security.CurrentAdmin;
import com.aluminum.manager.shop.ShopAccess;
import com.aluminum.manager.shop.ShopContext;
import com.aluminum.manager.shop.ShopScope;
import com.aluminum.manager.system.AuditService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceService {

	private static final int MONEY_SCALE = 4;
	private static final int QTY_SCALE = 6;
	private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
	private static final Set<String> MANUAL_EXPENSE_CATEGORIES = Set.of(
			"OPERATING_EXPENSE", "MAINTENANCE", "PAYROLL", "OTHER_EXPENSE");

	private final JdbcTemplate jdbc;
	private final DocumentSequenceService sequences;
	private final AuditService audit;
	private final Clock clock;
	private final ShopAccess shops;

	public FinanceService(
			JdbcTemplate jdbc, DocumentSequenceService sequences, AuditService audit, Clock clock, ShopAccess shops) {
		this.jdbc = jdbc;
		this.sequences = sequences;
		this.audit = audit;
		this.clock = clock;
		this.shops = shops;
	}

	// ---------- Transactions ----------

	@Transactional(readOnly = true)
	public List<TransactionDto> listTransactions(
			LocalDate from, LocalDate to, String type, String category, String status, String query) {
		DateRange range = range(from, to);
		StringBuilder sql = new StringBuilder("""
				SELECT ft.id, ft.transaction_code, ft.transaction_date, ft.transaction_type, ft.category,
				       ft.amount, ft.description, ft.spent_by, ft.reference_type, ft.reference_id, ft.status, ft.created_at,
				       ft.is_internal, s.code AS shop_code
				FROM financial_transactions ft
				JOIN shops s ON s.id = ft.shop_id
				WHERE ft.transaction_date >= ? AND ft.transaction_date < ?
				""" + shops.shopOnly("ft"));
		List<Object> args = new ArrayList<>();
		args.add(Timestamp.valueOf(range.from().atStartOfDay()));
		args.add(Timestamp.valueOf(range.to().plusDays(1).atStartOfDay()));

		if (type != null && !type.isBlank()) {
			sql.append(" AND ft.transaction_type = ?");
			args.add(type.trim());
		}
		if (category != null && !category.isBlank()) {
			sql.append(" AND ft.category = ?");
			args.add(category.trim());
		}
		if (status != null && !status.isBlank()) {
			sql.append(" AND ft.status = ?");
			args.add(status.trim());
		}
		if (query != null && !query.isBlank()) {
			sql.append(" AND (ft.transaction_code LIKE ? OR ft.description LIKE ?)");
			String like = "%" + query.trim() + "%";
			args.add(like);
			args.add(like);
		}
		sql.append(" ORDER BY ft.transaction_date DESC, ft.id DESC LIMIT 500");
		return jdbc.query(sql.toString(), (rs, i) -> mapTransaction(rs), args.toArray());
	}

	@Transactional
	public TransactionDto createTransaction(CreateTransactionRequest request) {
		String type = request.transactionType().trim();
		String category = request.category().trim();
		if ("SALE_RECEIPT".equals(category) || "PURCHASE_PAYMENT".equals(category)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Khoản thu bán hàng và chi trả phiếu nhập phải ghi nhận trực tiếp trên phiếu kho để tránh ghi trùng chứng từ gốc",
					null);
		}
		if ("PAYROLL".equals(category)) {
			shops.requireManufacturing();
		}
		if (!"EXPENSE".equals(type)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ lập phiếu chi thủ công trong sổ thu chi", null);
		}
		if (!MANUAL_EXPENSE_CATEGORIES.contains(category)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Nhóm phiếu chi thủ công không hợp lệ", null);
		}

		if (shops.isDistribution() && blank(request.spentBy()) == null) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Nhập người chi", null);
		}
		String key = blank(request.idempotencyKey());
		if (key != null) {
			List<Long> existing = jdbc.queryForList(
					"SELECT id FROM financial_transactions WHERE idempotency_key = ?", Long.class, key);
			if (!existing.isEmpty()) {
				return getTransaction(existing.get(0));
			}
		}

		LocalDateTime when = request.transactionDate() == null ? LocalDateTime.now(clock) : request.transactionDate();
		if (!shops.isDistribution()) assertFactoryYearOpen(when);
		String code = sequences.nextCode("FINANCE_EXPENSE", when.toLocalDate());
		BigDecimal amount = InventoryMath.snapshotMoney(request.amount());
		long id = insert("""
				INSERT INTO financial_transactions (
				    transaction_code, transaction_date, transaction_type, category,
				    amount, description, spent_by, reference_type, reference_id, status, idempotency_key,
				    shop_id, is_internal)
				VALUES (?, ?, ?, ?, ?, ?, ?, 'MANUAL', NULL, 'CONFIRMED', ?, ?, 0)
				""",
				code, Timestamp.valueOf(when), type, category, amount, blank(request.description()), blank(request.spentBy()), key,
				shops.currentId());
		audit.record(CurrentAdmin.get().id(), "CREATE_FINANCE_" + type, "financial_transactions",
				String.valueOf(id), code);
		return getTransaction(id);
	}

	@Transactional
	public TransactionDto voidTransaction(long id, String reason) {
		TransactionDto tx = getTransaction(id);
		if ("VOID".equals(tx.status())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phiếu thu/chi này đã được hủy trước đó", null);
		}
		if ("STOCK_DOCUMENT".equals(tx.referenceType())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Khoản thu/chi gắn với phiếu kho không thể hủy rời. Hãy kiểm tra trên phiếu kho tương ứng", null);
		}
		if ("HOLIDAY_BONUS".equals(tx.referenceType())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Hãy chỉnh trạng thái đã nhận trong Lịch nghỉ lễ để cập nhật khoản chi thưởng", null);
		}
		if ("SCRAP_SALE".equals(tx.referenceType())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Khoản thu phế liệu phải được đối soát trên phiếu bán phế liệu", null);
		}
		if (ShopContext.MANUFACTURING.equals(tx.shopCode())) assertFactoryYearOpen(tx.transactionDate());
		String updatedDesc = (tx.description() == null ? "" : tx.description() + " ")
				+ "[Đã hủy: " + reason.trim() + "]";
		jdbc.update("""
				UPDATE financial_transactions SET status = 'VOID', description = ? WHERE id = ?
				""", updatedDesc.length() > 500 ? updatedDesc.substring(0, 500) : updatedDesc, id);
		audit.record(CurrentAdmin.get().id(), "VOID_FINANCE_TRANSACTION", "financial_transactions",
				String.valueOf(id), "Hủy phiếu " + tx.transactionCode() + ". Lý do: " + reason.trim());
		return getTransaction(id);
	}

	@Transactional(readOnly = true)
	public TransactionDto getTransaction(long id) {
		List<TransactionDto> rows = jdbc.query("""
				SELECT ft.*, s.code AS shop_code
				FROM financial_transactions ft
				JOIN shops s ON s.id = ft.shop_id
				WHERE ft.id = ?
				""", (rs, i) -> mapTransaction(rs), id);
		if (rows.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy phiếu thu/chi", null);
		}
		TransactionDto tx = rows.get(0);
		if (!ShopScope.all() && tx.shopCode() != null && !ShopContext.code().equals(tx.shopCode())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy phiếu thu/chi", null);
		}
		return tx;
	}

	private static TransactionDto mapTransaction(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new TransactionDto(
				rs.getLong("id"),
				rs.getString("transaction_code"),
				rs.getTimestamp("transaction_date").toLocalDateTime(),
				rs.getString("transaction_type"),
				rs.getString("category"),
				rs.getBigDecimal("amount"),
				rs.getString("description"),
				rs.getString("spent_by"),
				rs.getString("reference_type"),
				(Long) rs.getObject("reference_id"),
				rs.getString("status"),
				rs.getTimestamp("created_at").toLocalDateTime(),
				rs.getBoolean("is_internal"),
				rs.getString("shop_code"));
	}

	private void assertFactoryYearOpen(LocalDateTime date) {
		Integer closed = jdbc.queryForObject("""
				SELECT COUNT(*) FROM factory_year_counts
				WHERE status = 'CLOSED' AND period_from <= ? AND period_to >= ?
				""", Integer.class, date.toLocalDate(), date.toLocalDate());
		if (closed != null && closed > 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Ngày giao dịch thuộc kỳ đã chốt kiểm kê xưởng", null);
		}
	}

	// ---------- Finance Summary ----------

	@Transactional(readOnly = true)
	public FinanceSummaryDto summary(LocalDate from, LocalDate to) {
		DateRange range = range(from, to);
		BigDecimal saleReceipt = sumByCategory("INCOME", "SALE_RECEIPT", range);
		BigDecimal otherIncome = sumByCategory("INCOME", "OTHER_INCOME", range);
		BigDecimal totalIncome = saleReceipt.add(otherIncome).setScale(MONEY_SCALE, ROUNDING);

		BigDecimal operatingExpense = sumByCategory("EXPENSE", "OPERATING_EXPENSE", range).add(sumByCategory("EXPENSE", "MAINTENANCE", range));
		BigDecimal payrollExpense = shops.isDistribution()
				? BigDecimal.ZERO : sumByCategory("EXPENSE", "PAYROLL", range);
		BigDecimal otherExpense = sumByCategory("EXPENSE", "OTHER_EXPENSE", range);
		BigDecimal purchasePayment = sumByCategory("EXPENSE", "PURCHASE_PAYMENT", range);
		BigDecimal totalExpense = operatingExpense.add(payrollExpense).add(otherExpense).add(purchasePayment)
				.setScale(MONEY_SCALE, ROUNDING);

		BigDecimal internalIncome = sumInternal("INCOME", range);
		BigDecimal internalExpense = sumInternal("EXPENSE", range);
		return new FinanceSummaryDto(
				range.from(),
				range.to(),
				totalIncome,
				saleReceipt,
				otherIncome,
				totalExpense,
				operatingExpense,
				payrollExpense,
				otherExpense,
				purchasePayment,
				totalIncome.subtract(totalExpense).setScale(MONEY_SCALE, ROUNDING),
				internalIncome,
				internalExpense);
	}

	// ---------- Reports ----------

	@Transactional(readOnly = true)
	public ProfitReportDto profitReport(LocalDate from, LocalDate to) {
		DateRange range = range(from, to);
		Timestamp start = Timestamp.valueOf(range.from().atStartOfDay());
		Timestamp end = Timestamp.valueOf(range.to().plusDays(1).atStartOfDay());

		BigDecimal revenue = queryMoney("""
				SELECT COALESCE(SUM(total_amount), 0)
				FROM stock_documents
				WHERE document_type = 'SALE' AND status = 'CONFIRMED'
				  AND document_date >= ? AND document_date < ?
				""", start, end);

		BigDecimal cogs = queryMoney("""
				SELECT COALESCE(SUM(i.cost_total_snapshot), 0)
				FROM stock_document_items i
				JOIN stock_documents d ON d.id = i.document_id
				WHERE d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND d.document_date >= ? AND d.document_date < ?
				""", start, end);

		BigDecimal grossProfit = revenue.subtract(cogs).setScale(MONEY_SCALE, ROUNDING);

		// Giá vốn chuẩn: dùng snapshot giá chuẩn đã chốt trên từng dòng bán.
		// Dòng cũ (trước khi có lịch sử giá) không có snapshot nên được tính là chưa đủ dữ liệu,
		// không lấy giá hôm nay để suy ngược vì sẽ làm sai lệch lãi chuẩn.
		BigDecimal standardCogsRaw = jdbc.queryForObject(shops.apply("""
				SELECT COALESCE(SUM(i.standard_cost_total_snapshot), 0)
				FROM stock_document_items i
				JOIN stock_documents d ON d.id = i.document_id
				WHERE d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND d.document_date >= ? AND d.document_date < ?
				"""), BigDecimal.class, start, end);
		BigDecimal standardCogs = scaleMoney(standardCogsRaw);
		Integer missingStandard = jdbc.queryForObject(shops.apply("""
				SELECT COUNT(*)
				FROM stock_document_items i
				JOIN stock_documents d ON d.id = i.document_id
				WHERE d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND d.document_date >= ? AND d.document_date < ?
				  AND i.standard_cost_total_snapshot IS NULL
				"""), Integer.class, start, end);
		boolean standardComplete = missingStandard == null || missingStandard == 0;
		BigDecimal standardGrossProfit = revenue.subtract(standardCogs).setScale(MONEY_SCALE, ROUNDING);
		BigDecimal standardNetProfit = standardGrossProfit.subtract(
				queryMoney("""
						SELECT COALESCE(SUM(amount), 0)
						FROM financial_transactions
						WHERE transaction_type = 'EXPENSE' AND status = 'CONFIRMED'
						  AND category IN ('OPERATING_EXPENSE', 'MAINTENANCE', 'PAYROLL', 'OTHER_EXPENSE')
						  AND transaction_date >= ? AND transaction_date < ?
						""", start, end)).setScale(MONEY_SCALE, ROUNDING);
		BigDecimal priceVariance = cogs.subtract(standardCogs).setScale(MONEY_SCALE, ROUNDING);

		BigDecimal operatingExpenses = queryMoney("""
				SELECT COALESCE(SUM(amount), 0)
				FROM financial_transactions
				WHERE transaction_type = 'EXPENSE' AND status = 'CONFIRMED'
				  AND category IN ('OPERATING_EXPENSE', 'MAINTENANCE', 'PAYROLL', 'OTHER_EXPENSE')
				  AND transaction_date >= ? AND transaction_date < ?
				""", start, end);

		BigDecimal netProfit = grossProfit.subtract(operatingExpenses).setScale(MONEY_SCALE, ROUNDING);

		BigDecimal cashCollected = queryMoney("""
				SELECT COALESCE(SUM(p.amount), 0)
				FROM sales_payments p
				JOIN stock_documents d ON d.id = p.document_id
				WHERE d.status = 'CONFIRMED'
				  AND p.payment_date >= ? AND p.payment_date < ?
				""", start, end);

		BigDecimal paidForSalesInPeriod = queryMoney("""
				SELECT COALESCE(SUM(p.amount), 0)
				FROM sales_payments p
				JOIN stock_documents d ON d.id = p.document_id
				WHERE d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND d.document_date >= ? AND d.document_date < ?
				""", start, end);
		BigDecimal uncollected = revenue.subtract(paidForSalesInPeriod).max(BigDecimal.ZERO)
				.setScale(MONEY_SCALE, ROUNDING);

		BigDecimal purchaseStockValue = queryMoney("""
				SELECT COALESCE(SUM(total_amount), 0)
				FROM stock_documents
				WHERE document_type = 'PURCHASE' AND status = 'CONFIRMED'
				  AND document_date >= ? AND document_date < ?
				""", start, end);

		BigDecimal purchasePaymentOutflow = sumByCategory("EXPENSE", "PURCHASE_PAYMENT", range);

		Map<LocalDate, DailyAccumulator> daily = new LinkedHashMap<>();
		for (LocalDate d = range.from(); !d.isAfter(range.to()); d = d.plusDays(1)) {
			daily.put(d, new DailyAccumulator());
		}

		jdbc.query(shops.apply("""
				SELECT DATE(document_date) AS day, COUNT(*) AS cnt, COALESCE(SUM(total_amount), 0) AS rev
				FROM stock_documents
				WHERE document_type = 'SALE' AND status = 'CONFIRMED'
				  AND document_date >= ? AND document_date < ?
				GROUP BY DATE(document_date)
				"""), rs -> {
			DailyAccumulator acc = daily.get(rs.getDate("day").toLocalDate());
			if (acc != null) {
				acc.saleCount = rs.getInt("cnt");
				acc.revenue = scaleMoney(rs.getBigDecimal("rev"));
			}
		}, start, end);

		jdbc.query(shops.apply("""
				SELECT DATE(d.document_date) AS day, COALESCE(SUM(i.cost_total_snapshot), 0) AS cogs,
				       COALESCE(SUM(i.standard_cost_total_snapshot), 0) AS standard_cogs
				FROM stock_document_items i
				JOIN stock_documents d ON d.id = i.document_id
				WHERE d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND d.document_date >= ? AND d.document_date < ?
				GROUP BY DATE(d.document_date)
				"""), rs -> {
			DailyAccumulator acc = daily.get(rs.getDate("day").toLocalDate());
			if (acc != null) {
				acc.cogs = scaleMoney(rs.getBigDecimal("cogs"));
				acc.standardCogs = scaleMoney(rs.getBigDecimal("standard_cogs"));
			}
		}, start, end);

		jdbc.query(shops.apply("""
				SELECT DATE(p.payment_date) AS day, COALESCE(SUM(p.amount), 0) AS cash
				FROM sales_payments p
				JOIN stock_documents d ON d.id = p.document_id
				WHERE d.status = 'CONFIRMED'
				  AND p.payment_date >= ? AND p.payment_date < ?
				GROUP BY DATE(p.payment_date)
				"""), rs -> {
			DailyAccumulator acc = daily.get(rs.getDate("day").toLocalDate());
			if (acc != null) {
				acc.cash = scaleMoney(rs.getBigDecimal("cash"));
			}
		}, start, end);

		jdbc.query(shops.apply("""
				SELECT DATE(transaction_date) AS day, COALESCE(SUM(amount), 0) AS exp
				FROM financial_transactions
				WHERE transaction_type = 'EXPENSE' AND status = 'CONFIRMED'
				  AND category IN ('OPERATING_EXPENSE', 'MAINTENANCE', 'PAYROLL', 'OTHER_EXPENSE')
				  AND transaction_date >= ? AND transaction_date < ?
				GROUP BY DATE(transaction_date)
				"""), rs -> {
			DailyAccumulator acc = daily.get(rs.getDate("day").toLocalDate());
			if (acc != null) {
				acc.expense = scaleMoney(rs.getBigDecimal("exp"));
			}
		}, start, end);

		List<DailyRevenueRowDto> dailyRows = new ArrayList<>();
		daily.forEach((day, acc) -> {
			if (acc.saleCount > 0 || acc.revenue.signum() > 0 || acc.cash.signum() > 0 || acc.expense.signum() > 0) {
				BigDecimal standardGross = acc.revenue.subtract(acc.standardCogs).setScale(MONEY_SCALE, ROUNDING);
				dailyRows.add(new DailyRevenueRowDto(
						day,
						acc.saleCount,
						acc.revenue,
						acc.cogs,
						acc.revenue.subtract(acc.cogs).setScale(MONEY_SCALE, ROUNDING),
						acc.cash,
						acc.expense,
						acc.standardCogs,
						standardGross,
						acc.cogs.subtract(acc.standardCogs).setScale(MONEY_SCALE, ROUNDING)));
			}
		});

		return new ProfitReportDto(
				range.from(),
				range.to(),
				revenue,
				cogs,
				grossProfit,
				operatingExpenses,
				netProfit,
				cashCollected,
				uncollected,
				purchaseStockValue,
				purchasePaymentOutflow,
				standardCogs,
				standardGrossProfit,
				standardNetProfit,
				priceVariance,
				standardComplete,
				dailyRows);
	}

	/**
	 * Ước tính lãi gộp vật tư của phiếu bán theo giá nhập tham khảo tại ngày giao.
	 * Không suy diễn giá vốn thực tế, nhân công, phụ kiện hoặc hao hụt từ các khoản này.
	 */
	@Transactional(readOnly = true)
	public List<FinanceDtos.InvoiceGrossMarginDto> invoiceGrossMargins(LocalDate from, LocalDate to) {
		DateRange period = range(from, to);
		Timestamp start = Timestamp.valueOf(period.from().atStartOfDay());
		Timestamp end = Timestamp.valueOf(period.to().plusDays(1).atStartOfDay());
		String sql = shops.apply("""
				SELECT d.id, d.document_code, DATE(d.document_date) AS sale_day,
				       COALESCE(d.counterparty_name, 'Khách chưa gắn') AS customer_name,
				       d.total_amount AS revenue,
				       SUM(i.quantity_base * COALESCE(
				           NULLIF(i.factory_reference_cost_per_kg, 0),
				           NULLIF(i.standard_cost_per_base_snapshot, 0),
				           NULLIF((SELECT cpp.price_per_base FROM category_purchase_prices cpp
				            WHERE cpp.category_id = i.category_id
				              AND cpp.effective_from <= DATE(d.document_date)
				            ORDER BY cpp.effective_from DESC, cpp.id DESC LIMIT 1), 0)
				       )) AS reference_cost,
				       SUM(CASE WHEN COALESCE(
				           NULLIF(i.factory_reference_cost_per_kg, 0), NULLIF(i.standard_cost_per_base_snapshot, 0),
				           NULLIF((SELECT cpp.price_per_base FROM category_purchase_prices cpp
				            WHERE cpp.category_id = i.category_id
				              AND cpp.effective_from <= DATE(d.document_date)
				            ORDER BY cpp.effective_from DESC, cpp.id DESC LIMIT 1), 0)
				       ) IS NULL THEN 1 ELSE 0 END) AS missing_cost
				FROM stock_documents d
				JOIN stock_document_items i ON i.document_id = d.id
				WHERE d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND d.is_internal = 0
				  AND d.document_date >= ? AND d.document_date < ?
				GROUP BY d.id, d.document_code, DATE(d.document_date),
				         d.counterparty_name, d.total_amount
				ORDER BY sale_day DESC, d.id DESC
				""");
		return jdbc.query(sql, (rs, index) -> invoiceMargin(
				rs.getLong("id"), rs.getString("document_code"),
				rs.getDate("sale_day").toLocalDate(), rs.getString("customer_name"),
				rs.getBigDecimal("revenue"), rs.getBigDecimal("reference_cost"),
				rs.getInt("missing_cost")), start, end);
	}

	static FinanceDtos.InvoiceGrossMarginDto invoiceMargin(long id, String code, LocalDate day,
			String customer, BigDecimal revenueValue, BigDecimal referenceCost, int missingCost) {
		BigDecimal revenue = scaleMoney(revenueValue);
		boolean complete = missingCost == 0 && referenceCost != null;
		BigDecimal cost = complete ? scaleMoney(referenceCost) : null;
		return new FinanceDtos.InvoiceGrossMarginDto(id, code, day, customer, revenue,
				cost, cost == null ? null : revenue.subtract(cost), complete);
	}

	@Transactional(readOnly = true)
	public List<ProductSalesRowDto> salesByProduct(
			LocalDate from, LocalDate to, Long productId, Long categoryId) {
		DateRange range = range(from, to);
		Timestamp start = Timestamp.valueOf(range.from().atStartOfDay());
		Timestamp end = Timestamp.valueOf(range.to().plusDays(1).atStartOfDay());

		StringBuilder sql = new StringBuilder("""
				SELECT p.id AS product_id, p.product_code, p.name AS product_name,
				       c.name AS category_name, p.base_unit_name,
				       COALESCE(SUM(i.quantity_base), 0) AS qty_sold,
				       COALESCE(SUM(i.line_total), 0) AS revenue,
				       COALESCE(SUM(i.cost_total_snapshot), 0) AS cogs,
				       COALESCE(SUM(i.standard_cost_total_snapshot), 0) AS standard_cogs,
				       SUM(CASE WHEN i.standard_cost_total_snapshot IS NULL THEN 1 ELSE 0 END) AS missing_standard
				FROM stock_document_items i
				JOIN stock_documents d ON d.id = i.document_id
				JOIN products p ON p.id = i.product_id
				JOIN product_categories c ON c.id = p.category_id
				WHERE d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND d.document_date >= ? AND d.document_date < ?
				""");
		List<Object> args = new ArrayList<>();
		args.add(start);
		args.add(end);
		if (productId != null) {
			sql.append(" AND p.id = ?");
			args.add(productId);
		}
		if (categoryId != null) {
			sql.append(" AND p.category_id = ?");
			args.add(categoryId);
		}
		sql.append(shops.scope("d"));
		sql.append("""
				 GROUP BY p.id, p.product_code, p.name, c.name, p.base_unit_name
				 ORDER BY revenue DESC, p.product_code ASC
				""");
		return jdbc.query(shops.apply(sql.toString()), (rs, i) -> {
			BigDecimal rev = scaleMoney(rs.getBigDecimal("revenue"));
			BigDecimal cogs = scaleMoney(rs.getBigDecimal("cogs"));
			BigDecimal standardCogs = scaleMoney(rs.getBigDecimal("standard_cogs"));
			BigDecimal standardGross = rev.subtract(standardCogs).setScale(MONEY_SCALE, ROUNDING);
			return new ProductSalesRowDto(
					rs.getLong("product_id"),
					rs.getString("product_code"),
					rs.getString("product_name"),
					rs.getString("category_name"),
					rs.getString("base_unit_name"),
					rs.getBigDecimal("qty_sold").setScale(QTY_SCALE, ROUNDING),
					rev,
					cogs,
					rev.subtract(cogs).setScale(MONEY_SCALE, ROUNDING),
					standardCogs,
					standardGross,
					cogs.subtract(standardCogs).setScale(MONEY_SCALE, ROUNDING));
		}, args.toArray());
	}

	/**
	 * Soát chênh lệch giữa giá nhập chuẩn (bảng giá áp dụng đúng ngày phiếu) và giá nhập thực tế
	 * đã ghi trên phiếu nhập. Dương: nhập đắt hơn chuẩn.
	 *
	 * <p>Nhóm theo {@code standard_cost_per_base_snapshot} đã chốt trên dòng phiếu, không đọc lại giá
	 * hiện tại của dòng lịch sử: Admin sửa giá cùng ngày sẽ làm đổi dòng lịch sử, nhưng phiếu đã xác
	 * nhận phải giữ nguyên chi phí chuẩn.
	 */
	@Transactional(readOnly = true)
	public List<PurchasePriceVarianceRowDto> purchasePriceVariance(LocalDate from, LocalDate to, Long categoryId) {
		DateRange range = range(from, to);
		Timestamp start = Timestamp.valueOf(range.from().atStartOfDay());
		Timestamp end = Timestamp.valueOf(range.to().plusDays(1).atStartOfDay());

		StringBuilder sql = new StringBuilder("""
				SELECT p.id AS product_id, p.product_code, p.name AS product_name,
				       c.name AS category_name, p.base_unit_name,
				       pp.effective_from AS standard_from,
				       i.standard_cost_per_base_snapshot AS standard_price,
				       COALESCE(SUM(i.quantity_base), 0) AS actual_qty,
				       COALESCE(SUM(i.cost_total_snapshot), 0) AS actual_value
				FROM stock_document_items i
				JOIN stock_documents d ON d.id = i.document_id
				JOIN products p ON p.id = i.product_id
				JOIN product_categories c ON c.id = i.category_id
				LEFT JOIN category_purchase_prices pp ON pp.id = i.purchase_price_id
				WHERE d.document_type = 'PURCHASE' AND d.status = 'CONFIRMED'
				  AND d.document_date >= ? AND d.document_date < ?
				  AND i.standard_cost_per_base_snapshot IS NOT NULL
				""");
		List<Object> args = new ArrayList<>();
		args.add(start);
		args.add(end);
		if (categoryId != null) {
			sql.append(" AND i.category_id = ?");
			args.add(categoryId);
		}
		sql.append("""
				 GROUP BY p.id, p.product_code, p.name, c.name, p.base_unit_name,
				          i.purchase_price_id, pp.effective_from, i.standard_cost_per_base_snapshot
				 ORDER BY p.product_code ASC, pp.effective_from DESC, i.purchase_price_id DESC
				""");

		return jdbc.query(shops.apply(sql.toString()), (rs, i) -> {
			BigDecimal actualQty = rs.getBigDecimal("actual_qty").setScale(QTY_SCALE, ROUNDING);
			BigDecimal actualValue = scaleMoney(rs.getBigDecimal("actual_value"));
			BigDecimal standardPrice = rs.getBigDecimal("standard_price").setScale(QTY_SCALE, ROUNDING);
			BigDecimal standardValue = actualQty.multiply(standardPrice).setScale(MONEY_SCALE, ROUNDING);
			BigDecimal variance = actualValue.subtract(standardValue).setScale(MONEY_SCALE, ROUNDING);
			BigDecimal actualAverage = actualQty.signum() == 0
					? BigDecimal.ZERO.setScale(QTY_SCALE, ROUNDING)
					: actualValue.divide(actualQty, QTY_SCALE, ROUNDING);
			BigDecimal percent = standardValue.signum() == 0
					? null
					: variance.multiply(BigDecimal.valueOf(100)).divide(standardValue, 2, ROUNDING);
			java.sql.Date standardFrom = rs.getDate("standard_from");
			return new PurchasePriceVarianceRowDto(
					rs.getLong("product_id"),
					rs.getString("product_code"),
					rs.getString("product_name"),
					rs.getString("category_name"),
					rs.getString("base_unit_name"),
					standardFrom == null ? null : standardFrom.toLocalDate(),
					standardPrice,
					actualQty,
					actualValue,
					actualAverage,
					variance,
					percent);
		}, args.toArray());
	}

	@Transactional(readOnly = true)
	public List<InventoryReportRowDto> inventoryReport(LocalDate from, LocalDate to, Long categoryId) {
		DateRange range = range(from, to);
		Timestamp start = Timestamp.valueOf(range.from().atStartOfDay());
		Timestamp end = Timestamp.valueOf(range.to().plusDays(1).atStartOfDay());

		StringBuilder sql = new StringBuilder("""
				SELECT p.id AS product_id, p.product_code, p.name AS product_name,
				       c.name AS category_name, p.base_unit_name,
				       b.quantity_base AS current_qty, b.average_cost_per_base AS avg_cost,
				       COALESCE((SELECT SUM(m.quantity_delta_base) FROM stock_movements m
				                 WHERE m.product_id = p.id AND m.movement_at < ?), 0) AS opening_qty,
				       COALESCE((SELECT SUM(m.quantity_delta_base) FROM stock_movements m
				                 WHERE m.product_id = p.id AND m.movement_at >= ? AND m.movement_at < ?
				                   AND m.quantity_delta_base > 0), 0) AS in_qty,
				       COALESCE((SELECT SUM(-m.quantity_delta_base) FROM stock_movements m
				                 WHERE m.product_id = p.id AND m.movement_at >= ? AND m.movement_at < ?
				                   AND m.quantity_delta_base < 0), 0) AS out_qty
				FROM products p
				JOIN product_categories c ON c.id = p.category_id
				JOIN stock_balances b ON b.product_id = p.id
				WHERE 1 = 1
				""");
		List<Object> args = new ArrayList<>();
		args.add(start);
		args.add(start);
		args.add(end);
		args.add(start);
		args.add(end);
		if (categoryId != null) {
			sql.append(" AND p.category_id = ?");
			args.add(categoryId);
		}
		sql.append(" ORDER BY p.product_code ASC");

		return jdbc.query(shops.apply(sql.toString()), (rs, i) -> {
			BigDecimal opening = rs.getBigDecimal("opening_qty").setScale(QTY_SCALE, ROUNDING);
			BigDecimal inQty = rs.getBigDecimal("in_qty").setScale(QTY_SCALE, ROUNDING);
			BigDecimal outQty = rs.getBigDecimal("out_qty").setScale(QTY_SCALE, ROUNDING);
			BigDecimal closing = opening.add(inQty).subtract(outQty).setScale(QTY_SCALE, ROUNDING);
			BigDecimal currentQty = rs.getBigDecimal("current_qty").setScale(QTY_SCALE, ROUNDING);
			BigDecimal avgCost = rs.getBigDecimal("avg_cost").setScale(6, ROUNDING);
			return new InventoryReportRowDto(
					rs.getLong("product_id"),
					rs.getString("product_code"),
					rs.getString("product_name"),
					rs.getString("category_name"),
					rs.getString("base_unit_name"),
					opening,
					inQty,
					outQty,
					closing,
					avgCost,
					currentQty.multiply(avgCost).setScale(MONEY_SCALE, ROUNDING));
		}, args.toArray());
	}

	@Transactional(readOnly = true)
	public List<PayrollReportRowDto> payrollReport(Integer year) {
		if (!ShopScope.all() && shops.isDistribution()) {
			return List.of();
		}
		StringBuilder sql = new StringBuilder("""
				SELECT pp.id, pp.period_year, pp.period_month, pp.status,
				       COUNT(pd.id) AS emp_count,
				       COALESCE(SUM(pd.credited_days), 0) AS total_days,
				       COALESCE(SUM(pd.base_pay), 0) AS base_pay,
				       COALESCE(SUM(pd.overtime_pay), 0) AS ot_pay,
				       COALESCE(SUM(pd.allowances), 0) AS allowances,
				       COALESCE(SUM(pd.deductions), 0) AS deductions,
				       COALESCE(SUM(pd.total_pay), 0) AS total_pay,
				       COALESCE((SELECT SUM(ft.amount) FROM financial_transactions ft
				                 WHERE ft.reference_type = 'PAYROLL_PERIOD' AND ft.reference_id = pp.id
				                   AND ft.category = 'PAYROLL' AND ft.status = 'CONFIRMED'), 0) AS paid_amount
				FROM payroll_periods pp
				LEFT JOIN payroll_details pd ON pd.payroll_period_id = pp.id
				WHERE 1 = 1
				""");
		List<Object> args = new ArrayList<>();
		if (year != null) {
			sql.append(" AND pp.period_year = ?");
			args.add(year);
		}
		sql.append(" GROUP BY pp.id, pp.period_year, pp.period_month, pp.status ORDER BY pp.period_year DESC, pp.period_month DESC");
		return jdbc.query(shops.apply(sql.toString()), (rs, i) -> new PayrollReportRowDto(
				rs.getLong("id"),
				rs.getInt("period_year"),
				rs.getInt("period_month"),
				rs.getString("status"),
				rs.getInt("emp_count"),
				rs.getBigDecimal("total_days").setScale(3, ROUNDING),
				scaleMoney(rs.getBigDecimal("base_pay")),
				scaleMoney(rs.getBigDecimal("ot_pay")),
				scaleMoney(rs.getBigDecimal("allowances")),
				scaleMoney(rs.getBigDecimal("deductions")),
				scaleMoney(rs.getBigDecimal("total_pay")),
				scaleMoney(rs.getBigDecimal("paid_amount"))), args.toArray());
	}

	@Transactional(readOnly = true)
	public String exportCsv(String type, LocalDate from, LocalDate to, Long productId, Long categoryId, Integer year) {
		StringBuilder sb = new StringBuilder("\uFEFF");
		String kind = type == null ? "profit" : type.trim().toLowerCase();
		if (shops.isDistribution() && "profit".equals(kind)) {
			ProfitReportDto report = profitReport(from, to);
			sb.append("Chỉ tiêu,Số tiền\r\n");
			sb.append("Doanh thu bán hàng,").append(report.revenue().toPlainString()).append("\r\n");
			sb.append("Tiền thực thu,").append(report.cashCollected().toPlainString()).append("\r\n");
			sb.append("Doanh thu chưa thu tiền,").append(report.uncollectedRevenue().toPlainString()).append("\r\n");
			sb.append("Chi phí hoạt động,").append(report.operatingExpenses().toPlainString()).append("\r\n");
			return sb.toString();
		}
		if (shops.isDistribution() && "sales-by-product".equals(kind)) {
			sb.append("Mã nhôm,Tên sản phẩm,Loại nhôm,Đơn vị,SL bán,Doanh thu\r\n");
			for (var row : salesByProduct(from, to, productId, categoryId)) {
				sb.append(csv(row.productCode())).append(",").append(csv(row.productName())).append(",")
						.append(csv(row.categoryName())).append(",").append(csv(row.baseUnitName())).append(",")
						.append(row.quantityBaseSold()).append(",").append(row.revenue()).append("\r\n");
			}
			return sb.toString();
		}
		if (shops.isDistribution() && "inventory".equals(kind)) {
			sb.append("Mã nhôm,Tên sản phẩm,Loại nhôm,Đơn vị,Tồn đầu kỳ,Nhập trong kỳ,Xuất trong kỳ,Tồn cuối kỳ\r\n");
			for (var row : inventoryReport(from, to, categoryId)) {
				sb.append(csv(row.productCode())).append(",").append(csv(row.productName())).append(",")
						.append(csv(row.categoryName())).append(",").append(csv(row.baseUnitName())).append(",")
						.append(row.openingQuantityBase()).append(",").append(row.inQuantityBase()).append(",")
						.append(row.outQuantityBase()).append(",").append(row.closingQuantityBase()).append("\r\n");
			}
			return sb.toString();
		}
		switch (kind) {
			case "sales-by-product" -> {
				List<ProductSalesRowDto> rows = salesByProduct(from, to, productId, categoryId);
				sb.append("Mã nhôm,Tên sản phẩm,Loại nhôm,Đơn vị cơ sở,SL bán (cơ sở),Doanh thu,Giá vốn,"
						+ "Lãi gộp,Giá vốn chuẩn,Lãi gộp chuẩn,Chênh lệch giá nhập\r\n");
				BigDecimal sumQty = BigDecimal.ZERO;
				BigDecimal sumRev = BigDecimal.ZERO;
				BigDecimal sumCogs = BigDecimal.ZERO;
				BigDecimal sumProfit = BigDecimal.ZERO;
				BigDecimal sumStandardCogs = BigDecimal.ZERO;
				BigDecimal sumStandardProfit = BigDecimal.ZERO;
				BigDecimal sumVariance = BigDecimal.ZERO;
				for (ProductSalesRowDto r : rows) {
					sb.append(csv(r.productCode())).append(",")
							.append(csv(r.productName())).append(",")
							.append(csv(r.categoryName())).append(",")
							.append(csv(r.baseUnitName())).append(",")
							.append(r.quantityBaseSold().stripTrailingZeros().toPlainString()).append(",")
							.append(r.revenue().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(r.cogs().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(r.grossProfit().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(r.standardCogs().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(r.standardGrossProfit().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(r.priceVariance().setScale(2, ROUNDING).toPlainString()).append("\r\n");
					sumQty = sumQty.add(r.quantityBaseSold());
					sumRev = sumRev.add(r.revenue());
					sumCogs = sumCogs.add(r.cogs());
					sumProfit = sumProfit.add(r.grossProfit());
					sumStandardCogs = sumStandardCogs.add(r.standardCogs());
					sumStandardProfit = sumStandardProfit.add(r.standardGrossProfit());
					sumVariance = sumVariance.add(r.priceVariance());
				}
				sb.append("TỔNG CỘNG,,,,")
						.append(sumQty.stripTrailingZeros().toPlainString()).append(",")
						.append(sumRev.setScale(2, ROUNDING).toPlainString()).append(",")
						.append(sumCogs.setScale(2, ROUNDING).toPlainString()).append(",")
						.append(sumProfit.setScale(2, ROUNDING).toPlainString()).append(",")
						.append(sumStandardCogs.setScale(2, ROUNDING).toPlainString()).append(",")
						.append(sumStandardProfit.setScale(2, ROUNDING).toPlainString()).append(",")
						.append(sumVariance.setScale(2, ROUNDING).toPlainString()).append("\r\n");
			}
			case "price-variance" -> {
				List<PurchasePriceVarianceRowDto> rows = purchasePriceVariance(from, to, categoryId);
				sb.append("Mã nhôm,Tên sản phẩm,Loại nhôm,Đơn vị,Giá chuẩn áp dụng từ,Giá nhập chuẩn,"
						+ "SL nhập thực tế,Giá vốn thực tế bình quân,Giá trị nhập thực tế,Chênh lệch,Chênh lệch %\r\n");
				BigDecimal sumVariance = BigDecimal.ZERO;
				for (PurchasePriceVarianceRowDto r : rows) {
					sb.append(csv(r.productCode())).append(",")
							.append(csv(r.productName())).append(",")
							.append(csv(r.categoryName())).append(",")
							.append(csv(r.baseUnitName())).append(",")
							.append(r.effectiveFrom() == null ? "" : r.effectiveFrom().toString()).append(",")
							.append(r.standardPricePerBase().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(r.actualQuantityBase().stripTrailingZeros().toPlainString()).append(",")
							.append(r.actualAverageCostPerBase().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(r.actualPurchaseValue().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(r.varianceValue().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(r.variancePercent() == null ? "" : r.variancePercent().toPlainString()).append("\r\n");
					sumVariance = sumVariance.add(r.varianceValue());
				}
				sb.append("TỔNG CHÊNH LỆCH,,,,,,,,,")
						.append(sumVariance.setScale(2, ROUNDING).toPlainString()).append(",\r\n");
			}
			case "inventory" -> {
				List<InventoryReportRowDto> rows = inventoryReport(from, to, categoryId);
				sb.append("Mã nhôm,Tên sản phẩm,Loại nhôm,Đơn vị,Tồn đầu kỳ,Nhập trong kỳ,Xuất trong kỳ,Tồn cuối kỳ,Giá vốn bình quân,Giá trị tồn hiện tại\r\n");
				BigDecimal sumValue = BigDecimal.ZERO;
				for (InventoryReportRowDto r : rows) {
					sb.append(csv(r.productCode())).append(",")
							.append(csv(r.productName())).append(",")
							.append(csv(r.categoryName())).append(",")
							.append(csv(r.baseUnitName())).append(",")
							.append(r.openingQuantityBase().stripTrailingZeros().toPlainString()).append(",")
							.append(r.inQuantityBase().stripTrailingZeros().toPlainString()).append(",")
							.append(r.outQuantityBase().stripTrailingZeros().toPlainString()).append(",")
							.append(r.closingQuantityBase().stripTrailingZeros().toPlainString()).append(",")
							.append(r.averageCostPerBase().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(r.inventoryValue().setScale(2, ROUNDING).toPlainString()).append("\r\n");
					sumValue = sumValue.add(r.inventoryValue());
				}
				sb.append("TỔNG GIÁ TRỊ TỒN,,,,,,,,,").append(sumValue.setScale(2, ROUNDING).toPlainString()).append("\r\n");
			}
			case "payroll" -> {
				List<PayrollReportRowDto> rows = payrollReport(year);
				sb.append("Kỳ công,Trạng thái,Số nhân viên,Tổng công,Lương cơ bản,Tăng ca,Phụ cấp,Khấu trừ,Tổng tiền công,Đã chi\r\n");
				BigDecimal sumTotal = BigDecimal.ZERO;
				for (PayrollReportRowDto r : rows) {
					sb.append(r.month() + "/" + r.year()).append(",")
							.append(csv(r.status())).append(",")
							.append(r.employeeCount()).append(",")
							.append(r.totalCreditedDays().stripTrailingZeros().toPlainString()).append(",")
							.append(r.totalBasePay().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(r.totalOvertimePay().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(r.totalAllowances().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(r.totalDeductions().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(r.grandTotalPay().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(r.paidAmount().setScale(2, ROUNDING).toPlainString()).append("\r\n");
					sumTotal = sumTotal.add(r.grandTotalPay());
				}
				sb.append("TỔNG CỘNG,,,,,,,,").append(sumTotal.setScale(2, ROUNDING).toPlainString()).append(",\r\n");
			}
			default -> {
				ProfitReportDto p = profitReport(from, to);
				sb.append("Chỉ tiêu,Số tiền\r\n");
				sb.append("Doanh thu bán hàng,").append(p.revenue().setScale(2, ROUNDING).toPlainString()).append("\r\n");
				sb.append("Giá vốn hàng bán,").append(p.cogs().setScale(2, ROUNDING).toPlainString()).append("\r\n");
				sb.append("Lãi gộp,").append(p.grossProfit().setScale(2, ROUNDING).toPlainString()).append("\r\n");
				sb.append("Giá vốn theo giá nhập chuẩn,").append(p.standardCogs().setScale(2, ROUNDING).toPlainString()).append("\r\n");
				sb.append("Lãi gộp theo giá nhập chuẩn,").append(p.standardGrossProfit().setScale(2, ROUNDING).toPlainString()).append("\r\n");
				sb.append("Chênh lệch giá nhập (thực tế - chuẩn),").append(p.priceVariance().setScale(2, ROUNDING).toPlainString()).append("\r\n");
				sb.append("Chi phí hoạt động,").append(p.operatingExpenses().setScale(2, ROUNDING).toPlainString()).append("\r\n");
				sb.append("Lợi nhuận thuần dự kiến,").append(p.netProfit().setScale(2, ROUNDING).toPlainString()).append("\r\n");
				sb.append("Lợi nhuận thuần theo giá nhập chuẩn,").append(p.standardNetProfit().setScale(2, ROUNDING).toPlainString()).append("\r\n");
				sb.append("Tiền thực thu trong kỳ,").append(p.cashCollected().setScale(2, ROUNDING).toPlainString()).append("\r\n");
				sb.append("Tiền chi nhập kho (không tính chi phí kỳ),").append(p.purchasePaymentOutflow().setScale(2, ROUNDING).toPlainString()).append("\r\n\r\n");
				sb.append("Ngày,Số phiếu bán,Doanh thu,Giá vốn,Lãi gộp,Giá vốn chuẩn,Lãi gộp chuẩn,"
						+ "Chênh lệch giá nhập,Tiền thực thu,Chi phí hoạt động\r\n");
				for (DailyRevenueRowDto d : p.dailyRows()) {
					sb.append(d.day()).append(",")
							.append(d.saleCount()).append(",")
							.append(d.revenue().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(d.cogs().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(d.grossProfit().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(d.standardCogs().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(d.standardGrossProfit().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(d.priceVariance().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(d.cashCollected().setScale(2, ROUNDING).toPlainString()).append(",")
							.append(d.operatingExpense().setScale(2, ROUNDING).toPlainString()).append("\r\n");
				}
			}
		}
		return sb.toString();
	}

	// ---------- Helpers ----------

	private BigDecimal sumByCategory(String type, String category, DateRange range) {
		return queryMoney("""
				SELECT COALESCE(SUM(amount), 0)
				FROM financial_transactions
				WHERE transaction_type = ? AND category = ? AND status = 'CONFIRMED'
				  AND transaction_date >= ? AND transaction_date < ?
				""",
				type,
				category,
				Timestamp.valueOf(range.from().atStartOfDay()),
				Timestamp.valueOf(range.to().plusDays(1).atStartOfDay()));
	}

	private BigDecimal queryMoney(String sql, Object... args) {
		return scaleMoney(jdbc.queryForObject(shops.apply(sql), BigDecimal.class, args));
	}

	private BigDecimal sumInternal(String type, DateRange range) {
		String shop = ShopScope.all() ? "" : " AND shop_id = " + shops.currentId();
		return scaleMoney(jdbc.queryForObject("""
				SELECT COALESCE(SUM(amount), 0)
				FROM financial_transactions
				WHERE transaction_type = ? AND status = 'CONFIRMED' AND is_internal = 1
				  AND transaction_date >= ? AND transaction_date < ?
				""" + shop, BigDecimal.class,
				type,
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

	private long insert(String sql, Object... args) {
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
			for (int i = 0; i < args.length; i++) {
				ps.setObject(i + 1, args[i]);
			}
			return ps;
		}, keys);
		return keys.getKey().longValue();
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static BigDecimal scaleMoney(BigDecimal value) {
		return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, ROUNDING);
	}

	private static String csv(String value) {
		if (value == null) {
			return "";
		}
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}

	private record DateRange(LocalDate from, LocalDate to) {
	}

	private static final class DailyAccumulator {
		private int saleCount = 0;
		private BigDecimal revenue = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
		private BigDecimal cogs = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
		private BigDecimal standardCogs = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
		private BigDecimal cash = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
		private BigDecimal expense = BigDecimal.ZERO.setScale(MONEY_SCALE, ROUNDING);
	}
}
