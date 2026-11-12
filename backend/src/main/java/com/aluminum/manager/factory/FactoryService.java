package com.aluminum.manager.factory;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.dashboard.YearEndBalanceService;
import com.aluminum.manager.factory.FactoryDtos.CustomerYearDto;
import com.aluminum.manager.factory.FactoryDtos.CustomerYearRow;
import com.aluminum.manager.factory.FactoryDtos.CustomerSettlementRow;
import com.aluminum.manager.factory.FactoryDtos.SettlementInvoiceRow;
import com.aluminum.manager.factory.FactoryDtos.SettlementReportDto;
import com.aluminum.manager.factory.FactoryDtos.ScrapPaymentDto;
import com.aluminum.manager.factory.FactoryDtos.ScrapPaymentRequest;
import com.aluminum.manager.factory.FactoryDtos.ScrapSaleDto;
import com.aluminum.manager.factory.FactoryDtos.ScrapSaleRequest;
import com.aluminum.manager.factory.FactoryDtos.RawCountDto;
import com.aluminum.manager.factory.FactoryDtos.RawCountRequest;
import com.aluminum.manager.factory.FactoryDtos.RawLayerDto;
import com.aluminum.manager.factory.FactoryDtos.PurchaseCostOption;
import com.aluminum.manager.factory.FactoryDtos.SaleCostReport;
import com.aluminum.manager.factory.FactoryDtos.SaleCostRow;
import com.aluminum.manager.factory.FactoryDtos.YearCountDto;
import com.aluminum.manager.factory.FactoryDtos.YearCountRequest;
import com.aluminum.manager.factory.FactoryDtos.YearReportDto;
import com.aluminum.manager.inventory.DocumentSequenceService;
import com.aluminum.manager.security.CurrentAdmin;
import com.aluminum.manager.shop.ShopAccess;
import com.aluminum.manager.system.AuditService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.HashSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FactoryService {
	private static final BigDecimal ZERO = BigDecimal.ZERO;
	private final JdbcTemplate jdbc;
	private final ShopAccess shops;
	private final DocumentSequenceService sequences;
	private final AuditService audit;
	private final Clock clock;
	private final YearEndBalanceService yearEndBalances;

	public FactoryService(JdbcTemplate jdbc, ShopAccess shops, DocumentSequenceService sequences,
			AuditService audit, Clock clock, YearEndBalanceService yearEndBalances) {
		this.jdbc = jdbc;
		this.shops = shops;
		this.sequences = sequences;
		this.audit = audit;
		this.clock = clock;
		this.yearEndBalances = yearEndBalances;
	}

	@Transactional(readOnly = true)
	public List<ScrapSaleDto> scrapSales(int year) {
		requireFactoryYear(year);
		return jdbc.query("""
				SELECT * FROM factory_scrap_sales
				WHERE shop_id = ? AND sale_date >= ? AND sale_date < ?
				ORDER BY sale_date DESC, id DESC
				""", (rs, i) -> scrapDto(rs), shops.currentId(), periodStart(year), periodEnd(year));
	}

	@Transactional
	public ScrapSaleDto createScrapSale(ScrapSaleRequest request) {
		shops.requireManufacturing();
		String key = blank(request.idempotencyKey());
		if (key != null) {
			List<Long> existing = jdbc.queryForList("""
					SELECT id FROM factory_scrap_sales WHERE idempotency_key = ? AND shop_id = ?
					""", Long.class, key, shops.currentId());
			if (!existing.isEmpty()) return scrapSale(existing.get(0));
		}
		if (request.saleDate().isAfter(LocalDateTime.now(clock))) {
			throw invalid("Ngày bán phế liệu không được ở tương lai");
		}
		ensureOpen(request.saleDate());
		BigDecimal kg = request.quantityKg().setScale(6, RoundingMode.HALF_UP);
		BigDecimal price = request.unitPrice().setScale(4, RoundingMode.HALF_UP);
		if (kg.signum() <= 0 || price.signum() < 0) {
			throw invalid("Khối lượng và đơn giá phế liệu không hợp lệ");
		}
		BigDecimal total = kg.multiply(price).setScale(4, RoundingMode.HALF_UP);
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO factory_scrap_sales (shop_id, sale_date, buyer_name, seller_name,
					    quantity_kg, unit_price, total_amount, notes, idempotency_key)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setLong(1, shops.currentId());
			ps.setTimestamp(2, Timestamp.valueOf(request.saleDate()));
			ps.setString(3, request.buyerName().trim());
			ps.setString(4, request.sellerName().trim());
			ps.setBigDecimal(5, kg);
			ps.setBigDecimal(6, price);
			ps.setBigDecimal(7, total);
			ps.setString(8, blank(request.notes()));
			ps.setString(9, key);
			return ps;
		}, keys);
		long id = keys.getKey().longValue();
		audit.record(CurrentAdmin.get().id(), "CREATE_SCRAP_SALE", "factory_scrap_sales",
				String.valueOf(id), "Bán " + kg + " kg phế liệu");
		return scrapSale(id);
	}

	@Transactional(readOnly = true)
	public ScrapSaleDto scrapSale(long id) {
		shops.requireManufacturing();
		List<ScrapSaleDto> rows = jdbc.query("""
				SELECT * FROM factory_scrap_sales WHERE id = ? AND shop_id = ?
				""", (rs, i) -> scrapDto(rs), id, shops.currentId());
		if (rows.isEmpty()) throw invalid("Không tìm thấy phiếu bán phế liệu");
		return rows.get(0);
	}

	@Transactional
	public ScrapSaleDto payScrap(long id, ScrapPaymentRequest request) {
		ScrapSaleDto sale = scrapSale(id);
		String key = blank(request.idempotencyKey());
		if (key != null) {
			List<Long> existing = jdbc.queryForList("""
					SELECT scrap_sale_id FROM factory_scrap_payments WHERE idempotency_key = ?
					""", Long.class, key);
			if (!existing.isEmpty()) {
				if (existing.get(0) != id) throw invalid("Mã lần thu đã dùng cho phiếu khác");
				return sale;
			}
		}
		if (!"CONFIRMED".equals(sale.status())) throw invalid("Phiếu phế liệu đã hủy");
		if (request.paymentDate().isBefore(sale.saleDate())) {
			throw invalid("Ngày thu tiền không được trước ngày bán phế liệu");
		}
		if (request.paymentDate().isAfter(LocalDateTime.now(clock))) {
			throw invalid("Ngày thu tiền không được ở tương lai");
		}
		ensureOpen(request.paymentDate());
		BigDecimal amount = request.amount().setScale(4, RoundingMode.HALF_UP);
		if (amount.signum() <= 0 || amount.compareTo(sale.totalAmount().subtract(sale.paidAmount())) > 0) {
			throw invalid("Số tiền thu vượt số còn phải thu");
		}
		String code = sequences.nextCode("FINANCE_INCOME", request.paymentDate().toLocalDate());
		GeneratedKeyHolder keys = new GeneratedKeyHolder();
		jdbc.update(connection -> {
			PreparedStatement ps = connection.prepareStatement("""
					INSERT INTO financial_transactions
					(transaction_code, transaction_date, transaction_type, category, amount,
					 description, reference_type, reference_id, status, shop_id, is_internal)
					VALUES (?, ?, 'INCOME', 'OTHER_INCOME', ?, ?, 'SCRAP_SALE', ?, 'CONFIRMED', ?, 0)
					""", Statement.RETURN_GENERATED_KEYS);
			ps.setString(1, code);
			ps.setTimestamp(2, Timestamp.valueOf(request.paymentDate()));
			ps.setBigDecimal(3, amount);
			ps.setString(4, "Thu bán phế liệu #" + id + " - " + sale.buyerName());
			ps.setLong(5, id);
			ps.setLong(6, shops.currentId());
			return ps;
		}, keys);
		long financeId = keys.getKey().longValue();
		jdbc.update("""
				INSERT INTO factory_scrap_payments
				(scrap_sale_id, payment_date, amount, payment_method, collected_by,
				 financial_transaction_id, idempotency_key)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", id, Timestamp.valueOf(request.paymentDate()), amount,
				request.paymentMethod().trim(), request.collectedBy().trim(), financeId, key);
		audit.record(CurrentAdmin.get().id(), "PAY_SCRAP_SALE", "factory_scrap_sales",
				String.valueOf(id), "Thu " + amount + " cho phiếu phế liệu #" + id);
		return scrapSale(id);
	}

	@Transactional
	public ScrapSaleDto voidScrap(long id, String reason) {
		ScrapSaleDto sale = scrapSale(id);
		if (!"CONFIRMED".equals(sale.status())) throw invalid("Phiếu đã hủy");
		ensureOpen(sale.saleDate());
		if (sale.paidAmount().signum() > 0) {
			throw invalid("Phiếu đã thu tiền; cần đối soát tiền trước khi hủy");
		}
		if (reason == null || reason.isBlank()) throw invalid("Nhập lý do hủy");
		if (reason.length() > 500) throw invalid("Lý do hủy tối đa 500 ký tự");
		jdbc.update("UPDATE factory_scrap_sales SET status = 'VOID', void_reason = ? WHERE id = ?",
				reason.trim(), id);
		audit.record(CurrentAdmin.get().id(), "VOID_SCRAP_SALE", "factory_scrap_sales",
				String.valueOf(id), reason.trim());
		return scrapSale(id);
	}

	@Transactional(readOnly = true)
	public YearCountDto yearCount(int year) {
		requireFactoryYear(year);
		List<YearCountDto> rows = jdbc.query("SELECT * FROM factory_year_counts WHERE year_number = ?",
				(rs, i) -> countDto(rs), year);
		if (!rows.isEmpty()) return rows.get(0);
		YearCountDto prior = priorCount(year);
		LocalDate from = prior == null ? LocalDate.of(year, 1, 1) : prior.periodTo().plusDays(1);
		return new YearCountDto(year, from, from.plusYears(1).minusDays(1),
				prior == null ? ZERO : closingKg(prior), prior == null ? ZERO : closingValue(prior),
				ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, ZERO, "MISSING", null, null);
	}

	@Transactional(readOnly = true)
	public List<RawCountDto> rawCounts(int year) {
		requireFactoryYear(year);
		return jdbc.query("""
				SELECT r.category_id, c.name, r.quantity_kg, r.value_amount
				FROM factory_raw_counts r JOIN product_categories c ON c.id = r.category_id
				WHERE r.year_number = ? ORDER BY c.name
				""", (rs, i) -> new RawCountDto(rs.getLong(1), rs.getString(2),
				rs.getBigDecimal(3), rs.getBigDecimal(4), jdbc.query("""
					SELECT l.purchase_item_id, d.document_code, d.document_date,
					       l.quantity_kg, l.unit_cost_per_kg, l.value_amount
					FROM factory_raw_count_layers l
					JOIN stock_document_items i ON i.id = l.purchase_item_id
					JOIN stock_documents d ON d.id = i.document_id
					WHERE l.year_number = ? AND l.category_id = ?
					ORDER BY d.document_date DESC, i.id DESC
					""", (lr, j) -> new RawLayerDto(lr.getLong(1), lr.getString(2),
					lr.getTimestamp(3).toLocalDateTime(), lr.getBigDecimal(4),
					lr.getBigDecimal(5), lr.getBigDecimal(6)), year, rs.getLong(1))), year);
	}

	@Transactional(readOnly = true)
	public List<PurchaseCostOption> purchaseCosts() {
		shops.requireManufacturing();
		return jdbc.query("""
				SELECT i.id, i.category_id, c.name, d.document_code, d.document_date,
				       i.quantity_base, i.line_total / i.quantity_base
				FROM stock_document_items i JOIN stock_documents d ON d.id = i.document_id
				JOIN product_categories c ON c.id = i.category_id
				WHERE d.shop_id = ? AND d.document_type = 'PURCHASE' AND d.status = 'CONFIRMED'
				  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
				ORDER BY d.document_date DESC, i.id DESC
				""", (rs, i) -> new PurchaseCostOption(rs.getLong(1), rs.getLong(2),
				rs.getString(3), rs.getString(4), rs.getTimestamp(5).toLocalDateTime(),
				rs.getBigDecimal(6), rs.getBigDecimal(7)), shops.currentId());
	}

	@Transactional(readOnly = true)
	public SaleCostReport saleCosts(int year, Long categoryId, BigDecimal costPerKg) {
		requireFactoryYear(year);
		List<SaleCostRow> rows = jdbc.query("""
				SELECT i.id, d.id, d.document_code, d.document_date, COALESCE(cu.name, d.counterparty_name),
				       i.product_name_snapshot, i.category_id, i.category_name_snapshot, i.quantity_base,
				       i.factory_reference_cost_per_kg, i.line_total, i.quantity_document, i.unit_name_snapshot, i.unit_price
				FROM stock_document_items i JOIN stock_documents d ON d.id = i.document_id
				LEFT JOIN customers cu ON cu.id = d.customer_id
				WHERE d.shop_id = ? AND d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
				  AND d.is_internal = 0 AND d.document_date >= ? AND d.document_date < ?
				  AND (? IS NULL OR i.category_id = ?)
				  AND (? IS NULL OR i.factory_reference_cost_per_kg = ?)
				ORDER BY d.document_date DESC, i.id DESC
				""", (rs, i) -> {
				BigDecimal cost = rs.getBigDecimal(10);
				BigDecimal kg = rs.getBigDecimal(9);
				return new SaleCostRow(rs.getLong(1), rs.getLong(2), rs.getString(3),
					rs.getTimestamp(4).toLocalDateTime(), rs.getString(5), rs.getString(6),
					rs.getLong(7), rs.getString(8), kg, cost,
					cost == null ? null : kg.multiply(cost).setScale(4, RoundingMode.HALF_UP), rs.getBigDecimal(11),
					rs.getBigDecimal(12), rs.getString(13), rs.getBigDecimal(14));
			}, shops.currentId(), periodStart(year), periodEnd(year), categoryId, categoryId, costPerKg, costPerKg);
		BigDecimal kg = rows.stream().map(SaleCostRow::quantityKg).reduce(ZERO, BigDecimal::add);
		BigDecimal value = rows.stream().map(SaleCostRow::referenceValue)
				.filter(v -> v != null).reduce(ZERO, BigDecimal::add);
		BigDecimal sales = rows.stream().map(SaleCostRow::salesAmount).reduce(ZERO, BigDecimal::add);
		return new SaleCostReport(kg, value, sales, rows);
	}

	@Transactional
	public SaleCostReport attachSaleCost(long saleItemId, long purchaseItemId) {
		shops.requireManufacturing();
		List<LocalDateTime> saleDates = jdbc.query("""
				SELECT d.document_date FROM stock_document_items i
				JOIN stock_documents d ON d.id = i.document_id
				WHERE i.id = ? AND d.shop_id = ? AND d.document_type = 'SALE'
				  AND d.status = 'CONFIRMED' AND d.is_internal = 0
				  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
				""", (rs, i) -> rs.getTimestamp(1).toLocalDateTime(), saleItemId, shops.currentId());
		if (saleDates.isEmpty()) throw invalid("Không tìm thấy dòng phiếu bán xưởng đang hiệu lực");
		LocalDateTime saleDate = saleDates.get(0);
		ensureOpen(saleDate);
		List<BigDecimal> costs = jdbc.query("""
				SELECT i.line_total / i.quantity_base FROM stock_document_items i
				JOIN stock_documents d ON d.id = i.document_id
				JOIN stock_document_items sale ON sale.id = ? AND sale.category_id = i.category_id
				WHERE i.id = ? AND d.shop_id = ? AND d.document_type = 'PURCHASE'
				  AND d.status = 'CONFIRMED' AND d.document_date <= ?
				  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
				""", (rs, i) -> rs.getBigDecimal(1), saleItemId, purchaseItemId,
				shops.currentId(), Timestamp.valueOf(saleDate));
		if (costs.isEmpty()) throw invalid("Phiếu nhập không hợp lệ cho loại nhôm và ngày bán này");
		jdbc.update("""
				UPDATE stock_document_items
				SET factory_purchase_item_id = ?, factory_reference_cost_per_kg = ? WHERE id = ?
				""", purchaseItemId, costs.get(0).setScale(6, RoundingMode.HALF_UP), saleItemId);
		audit.record(CurrentAdmin.get().id(), "SET_FACTORY_SALE_COST", "stock_document_items",
				String.valueOf(saleItemId), "Gắn giá tham chiếu từ dòng nhập #" + purchaseItemId);
		return saleCosts(periodYear(saleDate.toLocalDate()), null, null);
	}

	@Transactional
	public List<RawCountDto> saveRawCounts(int year, RawCountRequest request) {
		requireFactoryYear(year);
		YearCountDto current = yearCount(year);
		if ("CLOSED".equals(current.status())) throw invalid("Năm đã chốt, không sửa kiểm kê");
		HashSet<Long> seen = new HashSet<>();
		List<RawCountDto> calculated = new ArrayList<>();
		for (var input : request.items()) {
			if (!seen.add(input.categoryId())) throw invalid("Một loại nhôm chỉ được kiểm kê một lần");
			String name = jdbc.query("SELECT name FROM product_categories WHERE id = ? AND shop_id = ?",
					(rs, i) -> rs.getString(1), input.categoryId(), shops.currentId())
					.stream().findFirst().orElseThrow(() -> invalid("Loại nhôm không thuộc xưởng"));
			List<RawLayerDto> purchases = jdbc.query("""
					SELECT i.id, d.document_code, d.document_date, i.quantity_base,
					       i.line_total / i.quantity_base AS unit_cost, i.line_total
					FROM stock_document_items i JOIN stock_documents d ON d.id = i.document_id
					WHERE d.shop_id = ? AND d.document_type = 'PURCHASE' AND d.status = 'CONFIRMED'
					  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
					  AND i.category_id = ? AND d.document_date < ?
					ORDER BY d.document_date DESC, i.id DESC
					""", (rs, i) -> new RawLayerDto(rs.getLong(1), rs.getString(2),
					rs.getTimestamp(3).toLocalDateTime(), rs.getBigDecimal(4),
					rs.getBigDecimal(5), rs.getBigDecimal(6)), shops.currentId(), input.categoryId(), periodEnd(year));
			List<RawLayerDto> layers;
			try {
				layers = FactoryLayerAllocator.allocate(input.quantityKg(), purchases);
			} catch (IllegalArgumentException ex) {
				throw invalid("Không đủ phiếu nhập cho " + name + ": " + ex.getMessage());
			}
			BigDecimal value = layers.stream().map(RawLayerDto::valueAmount).reduce(ZERO, BigDecimal::add);
			calculated.add(new RawCountDto(input.categoryId(), name, input.quantityKg(), value, layers));
		}
		jdbc.update("DELETE FROM factory_raw_counts WHERE year_number = ?", year);
		for (RawCountDto row : calculated) {
			jdbc.update("INSERT INTO factory_raw_counts (year_number, category_id, quantity_kg, value_amount) VALUES (?, ?, ?, ?)",
					year, row.categoryId(), row.quantityKg(), row.valueAmount());
			for (RawLayerDto layer : row.layers()) jdbc.update("""
					INSERT INTO factory_raw_count_layers
					(year_number, category_id, purchase_item_id, quantity_kg, unit_cost_per_kg, value_amount)
					VALUES (?, ?, ?, ?, ?, ?)
					""", year, row.categoryId(), layer.purchaseItemId(), layer.quantityKg(),
					layer.unitCostPerKg(), layer.valueAmount());
		}
		BigDecimal kg = calculated.stream().map(RawCountDto::quantityKg).reduce(ZERO, BigDecimal::add);
		BigDecimal amount = calculated.stream().map(RawCountDto::valueAmount).reduce(ZERO, BigDecimal::add);
		jdbc.update("""
				INSERT INTO factory_year_counts (year_number, period_from, period_to, raw_kg, raw_value, opening_kg, opening_value) VALUES (?, ?, ?, ?, ?, ?, ?)
				ON DUPLICATE KEY UPDATE raw_kg = VALUES(raw_kg), raw_value = VALUES(raw_value)
				""", year, current.periodFrom(), current.periodTo(), kg, amount, current.openingKg(), current.openingValue());
		return rawCounts(year);
	}

	@Transactional
	public YearCountDto saveYearCount(int year, YearCountRequest request) {
		requireFactoryYear(year);
		YearCountDto existing = yearCount(year);
		if ("CLOSED".equals(existing.status())) throw invalid("Năm đã chốt, không sửa kiểm kê");
		YearCountDto prior = priorCount(year);
		LocalDate periodFrom = request.periodFrom() == null ? existing.periodFrom() : request.periodFrom();
		LocalDate periodTo = request.periodTo() == null ? existing.periodTo() : request.periodTo();
		if (periodFrom.isAfter(periodTo) || periodFrom.plusDays(400).isBefore(periodTo))
			throw invalid("Khoảng ngày chốt năm không hợp lệ");
		if (Math.abs(year - periodFrom.getYear()) > 1)
			throw invalid("Tên năm chốt không khớp ngày đầu kỳ");
		if (prior != null && !periodFrom.equals(prior.periodTo().plusDays(1)))
			throw invalid("Kỳ mới phải bắt đầu ngay sau ngày chốt năm trước");
		BigDecimal openingKg = prior == null ? request.openingKg() : closingKg(prior);
		BigDecimal openingValue = prior == null ? request.openingValue() : closingValue(prior);
		Integer rawRows = jdbc.queryForObject("SELECT COUNT(*) FROM factory_raw_counts WHERE year_number = ?", Integer.class, year);
		BigDecimal rawKg = rawRows != null && rawRows > 0
				? scalar("SELECT COALESCE(SUM(quantity_kg), 0) FROM factory_raw_counts WHERE year_number = ?", year)
				: existing.rawKg();
		BigDecimal rawValue = rawRows != null && rawRows > 0
				? scalar("SELECT COALESCE(SUM(value_amount), 0) FROM factory_raw_counts WHERE year_number = ?", year)
				: existing.rawValue();
		jdbc.update("""
				INSERT INTO factory_year_counts
				(year_number, period_from, period_to, opening_kg, opening_value, raw_kg, raw_value, scrap_kg, scrap_value,
				 work_in_progress_kg, work_in_progress_value, finished_kg, finished_value, notes)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
				ON DUPLICATE KEY UPDATE opening_kg = VALUES(opening_kg), opening_value = VALUES(opening_value),
				 period_from = VALUES(period_from), period_to = VALUES(period_to),
				 raw_kg = VALUES(raw_kg), raw_value = VALUES(raw_value),
				 scrap_kg = VALUES(scrap_kg), scrap_value = VALUES(scrap_value),
				 work_in_progress_kg = VALUES(work_in_progress_kg),
				 work_in_progress_value = VALUES(work_in_progress_value),
				 finished_kg = VALUES(finished_kg), finished_value = VALUES(finished_value), notes = VALUES(notes)
				""", year, periodFrom, periodTo, openingKg, openingValue, rawKg, rawValue,
				request.scrapKg(), request.scrapValue(), request.workInProgressKg(),
				request.workInProgressValue(), request.finishedKg(), request.finishedValue(),
				blank(request.notes()));
		audit.record(CurrentAdmin.get().id(), "SAVE_FACTORY_YEAR_COUNT", "factory_year_counts",
				String.valueOf(year), "Lưu kiểm kê cuối năm " + year);
		return yearCount(year);
	}

	@Transactional
	public YearCountDto saveInventory(int year, FactoryDtos.InventorySaveRequest request, boolean close) {
		saveYearCount(year, request.count());
		saveRawCounts(year, new RawCountRequest(request.items()));
		saveYearCount(year, request.count());
		return close ? closeYear(year) : yearCount(year);
	}

	@Transactional
	public YearCountDto closeYear(int year) {
		YearCountDto count = yearCount(year);
		if (count.periodTo().isAfter(LocalDate.now(clock))) throw invalid("Không thể chốt ngày tương lai");
		if ("MISSING".equals(count.status())) throw invalid("Lưu kiểm kê trước khi chốt năm");
		if (count.rawKg().signum() > 0 && rawCounts(year).isEmpty())
			throw invalid("Kiểm kê nguyên liệu phải theo từng loại nhôm");
		if ("CLOSED".equals(count.status())) throw invalid("Năm đã chốt");
		YearCountDto prior = priorCount(year);
		if (prior != null && !"CLOSED".equals(prior.status())) {
			throw invalid("Cần chốt năm trước trước khi chốt năm này");
		}
		List<FactoryDtos.RawCountInput> rawInputs = rawCounts(year).stream()
				.map(r -> new FactoryDtos.RawCountInput(r.categoryId(), r.quantityKg())).toList();
		if (!rawInputs.isEmpty()) {
			saveRawCounts(year, new RawCountRequest(rawInputs));
			count = yearCount(year);
		}
		YearReportDto report = yearReport(year);
		if (report.estimatedLossKg().signum() < 0) {
			throw invalid("Kiểm kê lớn hơn lượng nhôm có thể còn; hãy kiểm tra lại số kg");
		}
		Integer legacyArea = jdbc.queryForObject("""
				SELECT COUNT(*) FROM stock_document_items i
				JOIN stock_documents d ON d.id = i.document_id
				WHERE d.shop_id = ? AND d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND d.document_date >= ? AND d.document_date < ?
				  AND (i.billing_unit = 'm2' OR i.unit_name_snapshot IN ('m2', 'm²'))
				""", Integer.class, shops.currentId(), periodStart(year), periodEnd(year));
		if (legacyArea != null && legacyArea > 0) {
			throw invalid("Năm có phiếu m² cũ; cần đối chiếu kg nhôm thực giao trước khi chốt");
		}
		jdbc.update("UPDATE factory_year_counts SET status = 'CLOSED', closed_at = ?, closed_payroll_cost = ? WHERE year_number = ?",
				Timestamp.valueOf(LocalDateTime.now(clock)), report.payrollCost(), year);
		yearEndBalances.save(year, count.periodFrom(), count.periodTo(), closingKg(count), closingValue(count), report.estimatedProfit());
		jdbc.update("""
				UPDATE factory_year_counts SET opening_kg = ?, opening_value = ?
				WHERE year_number > ? AND status = 'DRAFT' AND period_from = ?
				""", closingKg(count), closingValue(count), year, count.periodTo().plusDays(1));
		audit.record(CurrentAdmin.get().id(), "CLOSE_FACTORY_YEAR", "factory_year_counts",
				String.valueOf(year), "Chốt kiểm kê năm " + year);
		return yearCount(year);
	}

	@Transactional(readOnly = true)
	public YearReportDto yearReport(int year) {
		YearCountDto count = yearCount(year);
		BigDecimal purchasedKg = scalar("""
				SELECT COALESCE(SUM(i.quantity_base), 0) FROM stock_document_items i
				JOIN stock_documents d ON d.id = i.document_id
				WHERE d.shop_id = ? AND d.document_type = 'PURCHASE' AND d.status = 'CONFIRMED'
				  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
				  AND d.document_date >= ? AND d.document_date < ?
				""", shops.currentId(), periodStart(year), periodEnd(year));
		BigDecimal purchaseValue = scalar("""
				SELECT COALESCE(SUM(d.total_amount), 0) FROM stock_documents d
				WHERE d.shop_id = ? AND d.document_type = 'PURCHASE' AND d.status = 'CONFIRMED'
				  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
				  AND d.document_date >= ? AND d.document_date < ?
				""", shops.currentId(), periodStart(year), periodEnd(year));
		BigDecimal finishedSoldKg = scalar("""
				SELECT COALESCE(SUM(i.quantity_base), 0) FROM stock_document_items i
				JOIN stock_documents d ON d.id = i.document_id
				WHERE d.shop_id = ? AND d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
				  AND d.is_internal = 0 AND d.document_date >= ? AND d.document_date < ?
				  AND COALESCE(i.billing_unit, 'kg') <> 'm2'
				""", shops.currentId(), periodStart(year), periodEnd(year));
		BigDecimal finishedRevenue = scalar("""
				SELECT COALESCE(SUM(d.total_amount), 0) FROM stock_documents d
				WHERE d.shop_id = ? AND d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
				  AND d.is_internal = 0 AND d.document_date >= ? AND d.document_date < ?
				""", shops.currentId(), periodStart(year), periodEnd(year));
		BigDecimal scrapSoldKg = scalar("""
				SELECT COALESCE(SUM(quantity_kg), 0) FROM factory_scrap_sales
				WHERE shop_id = ? AND status = 'CONFIRMED' AND sale_date >= ? AND sale_date < ?
				""", shops.currentId(), periodStart(year), periodEnd(year));
		BigDecimal scrapRevenue = scalar("""
				SELECT COALESCE(SUM(total_amount), 0) FROM factory_scrap_sales
				WHERE shop_id = ? AND status = 'CONFIRMED' AND sale_date >= ? AND sale_date < ?
				""", shops.currentId(), periodStart(year), periodEnd(year));
		BigDecimal expenses = scalar("""
				SELECT COALESCE(SUM(amount), 0) FROM financial_transactions
				WHERE shop_id = ? AND transaction_type = 'EXPENSE' AND status = 'CONFIRMED'
				  AND (category IN ('OPERATING_EXPENSE', 'MAINTENANCE', 'OTHER_EXPENSE')
				      OR (category = 'PAYROLL' AND reference_type = 'MANUAL'))
				  AND transaction_date >= ? AND transaction_date < ?
				""", shops.currentId(), periodStart(year), periodEnd(year));
		BigDecimal payrollCost = scalar("""
				SELECT COALESCE(SUM(GREATEST(pd.base_pay + pd.overtime_pay + pd.bonus_pay + pd.allowances - pd.deductions, 0)
				                    - pd.holiday_bonus_pay), 0) FROM payroll_details pd
				JOIN payroll_periods pp ON pp.id = pd.payroll_period_id
				JOIN employees e ON e.id = pd.employee_id
				WHERE e.shop_id = ?
				  AND LAST_DAY(MAKEDATE(pp.period_year, 1) + INTERVAL (pp.period_month - 1) MONTH) >= ?
				  AND LAST_DAY(MAKEDATE(pp.period_year, 1) + INTERVAL (pp.period_month - 1) MONTH) < ?
				""", shops.currentId(), count.periodFrom(), count.periodTo().plusDays(1)).add(scalar("""
				SELECT COALESCE(SUM(h.bonus_amount), 0)
				FROM holiday_employee_bonuses b
				JOIN holidays h ON h.id = b.holiday_id
				JOIN employees e ON e.id = b.employee_id
				WHERE e.shop_id = ? AND h.holiday_date >= ? AND h.holiday_date < ?
				""", shops.currentId(), periodStart(year), periodEnd(year)));
		if ("CLOSED".equals(count.status())) {
			BigDecimal snapshot = jdbc.queryForObject("SELECT closed_payroll_cost FROM factory_year_counts WHERE year_number = ?",
					BigDecimal.class, year);
			if (snapshot != null) payrollCost = snapshot;
		}
		BigDecimal finishedCash = scalar("""
				SELECT COALESCE(SUM(p.amount), 0) FROM sales_payments p
				JOIN stock_documents d ON d.id = p.document_id
				WHERE d.shop_id = ? AND d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND d.is_internal = 0 AND p.payment_date >= ? AND p.payment_date < ?
				""", shops.currentId(), periodStart(year), periodEnd(year));
		BigDecimal scrapCash = scalar("""
				SELECT COALESCE(SUM(p.amount), 0) FROM factory_scrap_payments p
				JOIN factory_scrap_sales s ON s.id = p.scrap_sale_id
				WHERE s.shop_id = ? AND s.status = 'CONFIRMED'
				  AND p.payment_date >= ? AND p.payment_date < ?
				""", shops.currentId(), periodStart(year), periodEnd(year));
		BigDecimal initial = FactoryMath.discrepancy(count.openingKg(), purchasedKg, finishedSoldKg);
		boolean hasCount = !"MISSING".equals(count.status());
		BigDecimal closingKg = hasCount ? closingKg(count) : null;
		BigDecimal closingValue = hasCount ? closingValue(count) : null;
		BigDecimal loss = hasCount ? FactoryMath.estimatedLoss(count.openingKg(), purchasedKg,
				finishedSoldKg, scrapSoldKg, closingKg) : null;
		BigDecimal materialCost = hasCount ? FactoryMath.materialCost(count.openingValue(),
				purchaseValue, closingValue) : null;
		BigDecimal profit = hasCount ? FactoryMath.profit(finishedRevenue, scrapRevenue,
				materialCost, expenses.add(payrollCost)) : null;
		return new YearReportDto(year, count.status(), count.openingKg(), purchasedKg,
				finishedSoldKg, scrapSoldKg, closingKg, initial, loss,
				count.openingValue(), purchaseValue, closingValue, materialCost,
				finishedRevenue, scrapRevenue, payrollCost, expenses, profit, finishedCash, scrapCash);
	}

	@Transactional(readOnly = true)
	public CustomerYearDto customerYear(long customerId, int year) {
		requireFactoryYear(year);
		List<String> names = jdbc.query("""
				SELECT name FROM customers WHERE id = ? AND shop_id = ?
				""", (rs, i) -> rs.getString(1), customerId, shops.currentId());
		if (names.isEmpty()) throw invalid("Không tìm thấy khách hàng xưởng");
		List<CustomerYearRow> rows = jdbc.query("""
				SELECT d.id AS document_id, d.document_code, d.document_date, d.issued_by,
				       d.total_amount, COALESCE((SELECT SUM(p.amount) FROM sales_payments p
				           WHERE p.document_id = d.id AND p.payment_date < ?), 0) AS paid_amount,
				       i.product_name_snapshot, i.unit_name_snapshot, i.quantity_base, i.unit_price, i.line_total
				FROM stock_documents d
				JOIN stock_document_items i ON i.document_id = d.id
				WHERE d.customer_id = ? AND d.shop_id = ? AND d.document_type = 'SALE'
				  AND d.status = 'CONFIRMED' AND d.is_internal = 0
				  AND d.document_date >= ? AND d.document_date < ?
				  AND COALESCE(i.billing_unit, 'kg') <> 'm2'
				ORDER BY d.document_date DESC, d.id DESC, i.id
				""", (rs, i) -> new CustomerYearRow(rs.getLong("document_id"),
					rs.getString("document_code"), rs.getTimestamp("document_date").toLocalDateTime(),
					rs.getString("product_name_snapshot"), rs.getString("unit_name_snapshot"),
					rs.getBigDecimal("quantity_base"),
					rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_total"),
					rs.getString("issued_by"), rs.getBigDecimal("total_amount"),
					rs.getBigDecimal("paid_amount")),
				periodEnd(year), customerId, shops.currentId(), periodStart(year), periodEnd(year));
		BigDecimal kg = rows.stream().map(CustomerYearRow::quantityKg).reduce(ZERO, BigDecimal::add);
		BigDecimal revenue = scalar("""
				SELECT COALESCE(SUM(total_amount), 0) FROM stock_documents
				WHERE customer_id = ? AND shop_id = ? AND document_type = 'SALE'
				  AND status = 'CONFIRMED' AND is_internal = 0
				  AND document_date >= ? AND document_date < ?
				""", customerId, shops.currentId(), periodStart(year), periodEnd(year));
		BigDecimal paid = scalar("""
				SELECT COALESCE(SUM(p.amount), 0) FROM sales_payments p
				JOIN stock_documents d ON d.id = p.document_id
				WHERE d.customer_id = ? AND d.shop_id = ? AND d.document_type = 'SALE'
				  AND d.status = 'CONFIRMED' AND d.is_internal = 0
				  AND d.document_date >= ? AND d.document_date < ?
				  AND p.payment_date < ?
				""", customerId, shops.currentId(), periodStart(year), periodEnd(year), periodEnd(year));
		int documentCount = jdbc.queryForObject("""
				SELECT COUNT(*) FROM stock_documents
				WHERE customer_id = ? AND shop_id = ? AND document_type = 'SALE'
				  AND status = 'CONFIRMED' AND is_internal = 0
				  AND document_date >= ? AND document_date < ?
				""", Integer.class, customerId, shops.currentId(), periodStart(year), periodEnd(year));
		return new CustomerYearDto(customerId, names.get(0), year, kg, revenue,
				paid, revenue.subtract(paid), documentCount, rows);
	}

	/** Công nợ tại đúng cuối tháng/năm, giữ riêng doanh thu và tiền thu trong kỳ. */
	@Transactional(readOnly = true)
	public SettlementReportDto settlement(int year, Integer month, Integer monthYear) {
		requireFactoryYear(year);
		if (month != null && (month < 1 || month > 12)) throw invalid("Tháng không hợp lệ");
		LocalDateTime periodFrom = yearCount(year).periodFrom().atStartOfDay();
		LocalDateTime periodTo = yearCount(year).periodTo().plusDays(1).atStartOfDay();
		int selectedYear = monthYear == null ? year : monthYear;
		if (selectedYear < 2000 || selectedYear > 2100) throw invalid("Năm của tháng không hợp lệ");
		LocalDateTime monthFrom = month == null ? periodFrom : LocalDateTime.of(selectedYear, month, 1, 0, 0);
		LocalDateTime monthTo = month == null ? periodTo : monthFrom.plusMonths(1);
		LocalDateTime from = monthFrom.isBefore(periodFrom) ? periodFrom : monthFrom;
		LocalDateTime to = monthTo.isAfter(periodTo) ? periodTo : monthTo;
		if (!from.isBefore(to)) throw invalid("Tháng không thuộc kỳ quyết toán");
		Timestamp fromTs = Timestamp.valueOf(from);
		Timestamp toTs = Timestamp.valueOf(to);
		List<CustomerSettlementRow> rows = new ArrayList<>();
		List<CustomerRef> customers = jdbc.query("""
				SELECT id, name FROM customers WHERE shop_id = ? ORDER BY name
				""", (rs, i) -> new CustomerRef(rs.getLong("id"), rs.getString("name")), shops.currentId());
		for (CustomerRef customer : customers) {
			long id = customer.id();
			BigDecimal openingSales = customerSalesBefore(id, fromTs);
			BigDecimal openingPaid = customerPaymentsBefore(id, fromTs);
			BigDecimal closingSales = customerSalesBefore(id, toTs);
			BigDecimal closingPaid = customerPaymentsBefore(id, toTs);
			BigDecimal opening = openingSales.subtract(openingPaid);
			BigDecimal sales = closingSales.subtract(openingSales);
			BigDecimal receipts = closingPaid.subtract(openingPaid);
			BigDecimal closing = closingSales.subtract(closingPaid);
			if (opening.signum() == 0 && sales.signum() == 0 && receipts.signum() == 0 && closing.signum() == 0) {
				continue;
			}
			List<SettlementInvoiceRow> invoices = jdbc.query("""
					SELECT d.id, d.document_code, d.document_date, d.issued_by, d.total_amount,
					       COALESCE((SELECT SUM(p.amount) FROM sales_payments p
					          WHERE p.document_id = d.id AND p.payment_date < ?), 0) AS paid_amount,
					       COALESCE((SELECT SUM(i.quantity_base) FROM stock_document_items i
					          WHERE i.document_id = d.id AND COALESCE(i.billing_unit, 'kg') <> 'm2'), 0) AS quantity_kg,
					       COALESCE((SELECT GROUP_CONCAT(DISTINCT i.product_name_snapshot SEPARATOR ', ')
					          FROM stock_document_items i WHERE i.document_id = d.id), '') AS products
					FROM stock_documents d
					WHERE d.customer_id = ? AND d.shop_id = ? AND d.document_type = 'SALE'
					  AND d.status = 'CONFIRMED' AND d.is_internal = 0 AND d.document_date < ?
					  AND (d.document_date >= ? OR d.total_amount > COALESCE((SELECT SUM(p.amount)
					      FROM sales_payments p WHERE p.document_id = d.id AND p.payment_date < ?), 0)
					      OR EXISTS (SELECT 1 FROM sales_payments p WHERE p.document_id = d.id
					          AND p.payment_date >= ? AND p.payment_date < ?))
					ORDER BY d.document_date, d.id
					""", (rs, i) -> {
					BigDecimal total = rs.getBigDecimal("total_amount");
					BigDecimal paid = rs.getBigDecimal("paid_amount");
					return new SettlementInvoiceRow(rs.getLong("id"), rs.getString("document_code"),
							rs.getTimestamp("document_date").toLocalDateTime(), rs.getString("products"),
							rs.getString("issued_by"), rs.getBigDecimal("quantity_kg"), total, paid,
							total.subtract(paid));
				}, toTs, id, shops.currentId(), toTs, fromTs, toTs, fromTs, toTs);
			rows.add(new CustomerSettlementRow(id, customer.name(), opening, sales, receipts,
					closing, invoices));
		}
		BigDecimal totalOpening = rows.stream().map(CustomerSettlementRow::openingDebt).reduce(ZERO, BigDecimal::add);
		BigDecimal totalSales = rows.stream().map(CustomerSettlementRow::periodSales).reduce(ZERO, BigDecimal::add);
		BigDecimal totalReceipts = rows.stream().map(CustomerSettlementRow::periodReceipts).reduce(ZERO, BigDecimal::add);
		BigDecimal totalClosing = rows.stream().map(CustomerSettlementRow::closingDebt).reduce(ZERO, BigDecimal::add);
		Integer unlinkedCount = jdbc.queryForObject("""
				SELECT COUNT(*) FROM stock_documents
				WHERE shop_id = ? AND customer_id IS NULL AND document_type = 'SALE'
				  AND status = 'CONFIRMED' AND is_internal = 0 AND document_date < ?
				""", Integer.class, shops.currentId(), toTs);
		BigDecimal unlinkedValue = scalar("""
				SELECT COALESCE(SUM(total_amount), 0) FROM stock_documents
				WHERE shop_id = ? AND customer_id IS NULL AND document_type = 'SALE'
				  AND status = 'CONFIRMED' AND is_internal = 0 AND document_date < ?
				""", shops.currentId(), toTs);
		return new SettlementReportDto(year, month, from, to, totalOpening, totalSales,
				totalReceipts, totalClosing, unlinkedCount == null ? 0 : unlinkedCount,
				unlinkedValue, rows);
	}

	private BigDecimal customerSalesBefore(long customerId, Timestamp cutoff) {
		return scalar("""
				SELECT COALESCE(SUM(total_amount), 0) FROM stock_documents
				WHERE customer_id = ? AND shop_id = ? AND document_type = 'SALE'
				  AND status = 'CONFIRMED' AND is_internal = 0 AND document_date < ?
				""", customerId, shops.currentId(), cutoff);
	}

	private BigDecimal customerPaymentsBefore(long customerId, Timestamp cutoff) {
		return scalar("""
				SELECT COALESCE(SUM(p.amount), 0) FROM sales_payments p
				JOIN stock_documents d ON d.id = p.document_id
				WHERE d.customer_id = ? AND d.shop_id = ? AND d.document_type = 'SALE'
				  AND d.status = 'CONFIRMED' AND d.is_internal = 0
				  AND d.document_date < ? AND p.payment_date < ?
				""", customerId, shops.currentId(), cutoff, cutoff);
	}

	private record CustomerRef(long id, String name) {
	}

	private ScrapSaleDto scrapDto(ResultSet rs) throws SQLException {
		long id = rs.getLong("id");
		List<ScrapPaymentDto> payments = jdbc.query("""
				SELECT * FROM factory_scrap_payments WHERE scrap_sale_id = ? ORDER BY payment_date, id
				""", (pr, i) -> new ScrapPaymentDto(pr.getLong("id"),
					pr.getTimestamp("payment_date").toLocalDateTime(), pr.getBigDecimal("amount"),
					pr.getString("payment_method"), pr.getString("collected_by")), id);
		BigDecimal paid = payments.stream().map(ScrapPaymentDto::amount).reduce(ZERO, BigDecimal::add);
		return new ScrapSaleDto(id, rs.getTimestamp("sale_date").toLocalDateTime(),
				rs.getString("buyer_name"), rs.getString("seller_name"),
				rs.getBigDecimal("quantity_kg"), rs.getBigDecimal("unit_price"),
				rs.getBigDecimal("total_amount"), paid, rs.getString("status"),
				rs.getString("notes"), payments);
	}

	private YearCountDto priorCount(int year) {
		List<YearCountDto> rows = jdbc.query("""
				SELECT * FROM factory_year_counts WHERE year_number < ? AND status = 'CLOSED'
				ORDER BY year_number DESC LIMIT 1
				""", (rs, i) -> countDto(rs), year);
		return rows.isEmpty() ? null : rows.get(0);
	}

	private static YearCountDto countDto(ResultSet rs) throws SQLException {
		Timestamp closed = rs.getTimestamp("closed_at");
		return new YearCountDto(rs.getInt("year_number"), rs.getDate("period_from").toLocalDate(),
				rs.getDate("period_to").toLocalDate(), rs.getBigDecimal("opening_kg"),
				rs.getBigDecimal("opening_value"), rs.getBigDecimal("raw_kg"),
				rs.getBigDecimal("raw_value"), rs.getBigDecimal("scrap_kg"),
				rs.getBigDecimal("scrap_value"), rs.getBigDecimal("work_in_progress_kg"),
				rs.getBigDecimal("work_in_progress_value"), rs.getBigDecimal("finished_kg"),
				rs.getBigDecimal("finished_value"), rs.getString("status"), rs.getString("notes"),
				closed == null ? null : closed.toLocalDateTime());
	}

	private static BigDecimal closingKg(YearCountDto c) {
		return c.rawKg().add(c.scrapKg()).add(c.workInProgressKg()).add(c.finishedKg());
	}

	private static BigDecimal closingValue(YearCountDto c) {
		return c.rawValue().add(c.scrapValue()).add(c.workInProgressValue()).add(c.finishedValue());
	}

	private BigDecimal scalar(String sql, Object... args) {
		BigDecimal value = jdbc.queryForObject(sql, BigDecimal.class, args);
		return value == null ? ZERO : value;
	}

	private void requireFactoryYear(int year) {
		shops.requireManufacturing();
		if (year < 2000 || year > 2100) throw invalid("Năm không hợp lệ");
	}

	private void ensureOpen(LocalDateTime date) {
		Integer closed = jdbc.queryForObject("""
				SELECT COUNT(*) FROM factory_year_counts
				WHERE status = 'CLOSED' AND period_from <= ? AND period_to >= ?
				""", Integer.class, date.toLocalDate(), date.toLocalDate());
		if (closed != null && closed > 0) throw invalid("Năm đã chốt kiểm kê; không ghi thêm phiếu vào năm này");
	}

	private Timestamp periodStart(int year) {
		return Timestamp.valueOf(yearCount(year).periodFrom().atStartOfDay());
	}

	private Timestamp periodEnd(int year) {
		return Timestamp.valueOf(yearCount(year).periodTo().plusDays(1).atStartOfDay());
	}

	private int periodYear(LocalDate date) {
		return jdbc.query("""
				SELECT year_number FROM factory_year_counts
				WHERE period_from <= ? AND period_to >= ? ORDER BY year_number DESC LIMIT 1
				""", (rs, i) -> rs.getInt(1), date, date).stream().findFirst().orElse(date.getYear());
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private static BusinessException invalid(String message) {
		return new BusinessException(ErrorCode.VALIDATION_ERROR, message, null);
	}
}
