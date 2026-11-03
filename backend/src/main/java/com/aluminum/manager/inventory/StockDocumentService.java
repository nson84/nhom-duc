package com.aluminum.manager.inventory;

import com.aluminum.manager.catalog.PurchasePriceService;
import com.aluminum.manager.catalog.PurchasePriceService.ResolvedPrice;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.common.NameValidation;
import com.aluminum.manager.inventory.StockDtos.DocumentDto;
import com.aluminum.manager.inventory.StockDtos.DocumentRequest;
import com.aluminum.manager.inventory.StockDtos.DocumentSummary;
import com.aluminum.manager.inventory.StockDtos.ItemDto;
import com.aluminum.manager.inventory.StockDtos.ItemRequest;
import com.aluminum.manager.inventory.StockDtos.MovementDto;
import com.aluminum.manager.inventory.StockDtos.PaymentDto;
import com.aluminum.manager.inventory.StockDtos.PaymentRequest;
import com.aluminum.manager.inventory.StockDtos.StockBalanceDto;
import com.aluminum.manager.security.CurrentAdmin;
import com.aluminum.manager.shop.ShopAccess;
import com.aluminum.manager.shop.ShopContext;
import com.aluminum.manager.system.AuditService;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StockDocumentService {

	private static final Set<String> SUPPORTED = Set.of("PURCHASE", "SALE");

	private final JdbcTemplate jdbc;
	private final DocumentSequenceService sequences;
	private final AuditService audit;
	private final PurchasePriceService purchasePrices;
	private final Clock clock;
	private final ShopAccess shops;

	public StockDocumentService(
			JdbcTemplate jdbc,
			DocumentSequenceService sequences,
			AuditService audit,
			PurchasePriceService purchasePrices,
			Clock clock,
			ShopAccess shops) {
		this.jdbc = jdbc;
		this.sequences = sequences;
		this.audit = audit;
		this.purchasePrices = purchasePrices;
		this.clock = clock;
		this.shops = shops;
	}

	// ---------- Doc queries ----------

	@Transactional(readOnly = true)
	public List<DocumentSummary> list(String type, String status, LocalDate from, LocalDate to, String query) {
		StringBuilder sql = new StringBuilder("""
				SELECT d.id, d.document_code, d.document_type, d.status, d.document_date, d.counterparty_name,
				       d.total_amount, d.payment_status, d.is_internal, d.internal_pair_document_id,
				       p.document_code AS pair_code
				FROM stock_documents d
				LEFT JOIN stock_documents p ON p.id = d.internal_pair_document_id
				WHERE 1 = 1
				""" + shops.shopOnly("d"));
		List<Object> args = new ArrayList<>();
		if (type != null && !type.isBlank()) {
			sql.append(" AND d.document_type = ?");
			args.add(type);
		}
		if (status != null && !status.isBlank()) {
			sql.append(" AND d.status = ?");
			args.add(status);
		}
		if (from != null) {
			sql.append(" AND d.document_date >= ?");
			args.add(Timestamp.valueOf(from.atStartOfDay()));
		}
		if (to != null) {
			sql.append(" AND d.document_date < ?");
			args.add(Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
		}
		if (query != null && !query.isBlank()) {
			sql.append(" AND (d.document_code LIKE ? OR d.counterparty_name LIKE ?)");
			String like = "%" + query.trim() + "%";
			args.add(like);
			args.add(like);
		}
		sql.append(" ORDER BY d.document_date DESC, d.id DESC LIMIT 500");
		return jdbc.query(sql.toString(), (rs, i) -> new DocumentSummary(
				rs.getLong("id"),
				rs.getString("document_code"),
				rs.getString("document_type"),
				rs.getString("status"),
				rs.getTimestamp("document_date").toLocalDateTime(),
				rs.getString("counterparty_name"),
				rs.getBigDecimal("total_amount"),
				rs.getString("payment_status"),
				rs.getBoolean("is_internal"),
				(Long) rs.getObject("internal_pair_document_id"),
				rs.getString("pair_code")), args.toArray());
	}

	@Transactional(readOnly = true)
	public DocumentDto get(long id) {
		DocumentDto dto = loadDocument(id);
		Long owner = jdbc.queryForObject("SELECT shop_id FROM stock_documents WHERE id = ?", Long.class, id);
		if (owner == null || owner != shops.currentId()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy phiếu", null);
		}
		return dto;
	}

	private DocumentDto loadDocument(long id) {
		List<DocumentDto> rows = jdbc.query("""
				SELECT d.*, p.document_code AS pair_code
				FROM stock_documents d
				LEFT JOIN stock_documents p ON p.id = d.internal_pair_document_id
				WHERE d.id = ?
				""", (rs, i) -> new DocumentDto(
				rs.getLong("id"),
				rs.getString("document_code"),
				rs.getString("document_type"),
				rs.getString("status"),
				rs.getTimestamp("document_date").toLocalDateTime(),
				rs.getString("counterparty_name"),
				rs.getString("issued_by"),
				rs.getString("received_by"),
				rs.getString("reason"),
				rs.getBigDecimal("subtotal"),
				rs.getBigDecimal("discount_amount"),
				rs.getBigDecimal("total_amount"),
				paid(rs.getLong("id"), rs.getString("document_type")),
				rs.getString("payment_status"),
				rs.getString("payment_method"),
				(Long) rs.getObject("reversal_of_document_id"),
				reversedBy(rs.getLong("id")),
				rs.getTimestamp("confirmed_at") == null ? null : rs.getTimestamp("confirmed_at").toLocalDateTime(),
				rs.getString("notes"),
				items(rs.getLong("id")),
				payments(rs.getLong("id"), rs.getString("document_type")),
				rs.getBoolean("is_internal"),
				(Long) rs.getObject("internal_pair_document_id"),
				rs.getString("pair_code")), id);
		if (rows.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy phiếu", null);
		}
		return rows.get(0);
	}

	// ---------- Draft lifecycle ----------

	@Transactional
	public DocumentDto createDraft(DocumentRequest request) {
		requireSupported(request.documentType());
		requirePurchaseReceivedBy(request);
		if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
			List<Long> existing = jdbc.queryForList(
					"SELECT id FROM stock_documents WHERE idempotency_key = ?", Long.class, request.idempotencyKey());
			if (!existing.isEmpty()) {
				return get(existing.get(0));
			}
		}
		List<Line> lines = buildLines(request);
		Totals totals = totals(lines, request.discountAmount());
		boolean internal = resolveInternal(request);
		String code = sequences.nextCode(request.documentType(), request.documentDate().toLocalDate());
		long id = insertReturningId("""
				INSERT INTO stock_documents (document_code, document_type, status, document_date,
				    counterparty_name, issued_by, received_by, reason, subtotal, discount_amount,
				    total_amount, payment_status, payment_method, idempotency_key, notes, shop_id, is_internal)
				VALUES (?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?, 'UNPAID', ?, ?, ?, ?, ?)
				""",
				code, request.documentType(), Timestamp.valueOf(request.documentDate()),
				blank(request.counterpartyName()), blank(request.issuedBy()), blank(request.receivedBy()),
				blank(request.reason()), totals.subtotal(), totals.discount(), totals.total(),
				blank(request.paymentMethod()), blank(request.idempotencyKey()), blank(request.notes()),
				shops.currentId(), internal);
		insertLines(id, lines);
		audit.record(CurrentAdmin.get().id(), "CREATE_" + request.documentType(), "stock_documents",
				String.valueOf(id), "Tạo phiếu nháp " + code);
		return get(id);
	}

	@Transactional
	public DocumentDto updateDraft(long id, DocumentRequest request) {
		DocRow doc = lock(id);
		StockConfirmationPolicy.requireDraft(doc.status());
		if (!doc.type().equals(request.documentType())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không đổi loại phiếu được", null);
		}
		requirePurchaseReceivedBy(request);
		List<Line> lines = buildLines(request);
		Totals totals = totals(lines, request.discountAmount());
		boolean internal = resolveInternal(request);
		jdbc.update("""
				UPDATE stock_documents
				SET document_date = ?, counterparty_name = ?, issued_by = ?, received_by = ?, reason = ?,
				    subtotal = ?, discount_amount = ?, total_amount = ?, payment_method = ?, notes = ?,
				    is_internal = ?
				WHERE id = ?
				""",
				Timestamp.valueOf(request.documentDate()), blank(request.counterpartyName()),
				blank(request.issuedBy()), blank(request.receivedBy()), blank(request.reason()),
				totals.subtotal(), totals.discount(), totals.total(), blank(request.paymentMethod()),
				blank(request.notes()), internal, id);
		jdbc.update("DELETE FROM stock_document_items WHERE document_id = ?", id);
		insertLines(id, lines);
		audit.record(CurrentAdmin.get().id(), "UPDATE_" + doc.type(), "stock_documents",
				String.valueOf(id), "Sửa phiếu nháp " + doc.code());
		return get(id);
	}

	@Transactional
	public DocumentDto cancelDraft(long id) {
		DocRow doc = lock(id);
		StockConfirmationPolicy.requireDraft(doc.status());
		jdbc.update("UPDATE stock_documents SET status = 'CANCELLED', cancelled_at = ? WHERE id = ?",
				Timestamp.valueOf(LocalDateTime.now(clock)), id);
		audit.record(CurrentAdmin.get().id(), "CANCEL_DRAFT", "stock_documents",
				String.valueOf(id), "Hủy phiếu nháp " + doc.code());
		return get(id);
	}

	// ---------- Confirm ----------

	@Transactional
	public DocumentDto confirm(long id) {
		DocRow doc = lock(id);
		StockConfirmationPolicy.requireDraft(doc.status());
		List<StoredItem> items = storedItems(id);
		if (items.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phiếu chưa có dòng hàng", null);
		}
		items.sort(Comparator.comparingLong(StoredItem::productId).thenComparingLong(StoredItem::id));
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDate documentDay = doc.date().toLocalDate();
		for (StoredItem item : items) {
			Balance balance = lockBalance(item.productId());
			BigDecimal costPerBase;
			BigDecimal delta;
			BigDecimal newQuantity;
			BigDecimal newCost;
			String movementType;
			if ("PURCHASE".equals(doc.type())) {
				costPerBase = item.lineTotal().divide(item.quantityBase(), InventoryMath.COST_SCALE, InventoryMath.ROUNDING);
				newCost = InventoryMath.weightedAverageCost(
						balance.quantity(), balance.cost(), item.quantityBase(), costPerBase);
				newQuantity = InventoryMath.applyDelta(balance.quantity(), item.quantityBase());
				delta = item.quantityBase();
				movementType = "IN";
			} else {
				costPerBase = balance.cost();
				newQuantity = InventoryMath.applyDelta(balance.quantity(), item.quantityBase().negate());
				newCost = balance.cost();
				delta = item.quantityBase().negate();
				movementType = "OUT";
			}
			BigDecimal costTotal = "PURCHASE".equals(doc.type())
					? item.lineTotal()
					: InventoryMath.costTotal(item.quantityBase(), costPerBase);
			// Chi phí chuẩn chốt theo bảng giá nhập có hiệu lực đúng ngày phiếu, không theo giá hôm nay.
			// Nhờ vậy sửa giá nhập về sau không làm lệch lãi chuẩn của phiếu đã xác nhận.
			ResolvedPrice standard = purchasePrices.resolve(item.productId(), documentDay);
			BigDecimal standardPerBase = standard == null ? null : standard.pricePerBase();
			BigDecimal standardTotal = standardPerBase == null
					? null
					: InventoryMath.costTotal(item.quantityBase(), standardPerBase);
			jdbc.update("""
					UPDATE stock_document_items
					SET cost_per_base_snapshot = ?, cost_total_snapshot = ?,
					    purchase_price_id = ?, standard_cost_per_base_snapshot = ?,
					    standard_cost_total_snapshot = ?
					WHERE id = ?
					""", costPerBase, costTotal,
					standard == null ? null : standard.priceId(),
					standardPerBase, standardTotal, item.id());
			jdbc.update("UPDATE stock_balances SET quantity_base = ?, average_cost_per_base = ? WHERE product_id = ?",
					newQuantity, newCost, item.productId());
			jdbc.update("""
					INSERT INTO stock_movements (document_id, document_item_id, product_id, movement_type,
					    quantity_delta_base, unit_cost, movement_at)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					""", id, item.id(), item.productId(), movementType, delta, costPerBase,
					Timestamp.valueOf(doc.date()));
		}
		boolean internal = Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT is_internal FROM stock_documents WHERE id = ?", Boolean.class, id));
		if (internal) {
			jdbc.update("""
					UPDATE stock_documents
					SET status = 'CONFIRMED', confirmed_at = ?, version = version + 1, payment_status = 'PAID'
					WHERE id = ?
					""", Timestamp.valueOf(now), id);
		} else {
			jdbc.update("""
					UPDATE stock_documents
					SET status = 'CONFIRMED', confirmed_at = ?, version = version + 1,
					    payment_status = CASE WHEN total_amount = 0 THEN 'PAID' ELSE payment_status END
					WHERE id = ?
					""", Timestamp.valueOf(now), id);
		}
		audit.record(CurrentAdmin.get().id(), "CONFIRM_" + doc.type(), "stock_documents",
				String.valueOf(id), "Xác nhận phiếu " + doc.code());
		if (internal && "SALE".equals(doc.type()) && shops.isDistribution()) {
			createPairedManufacturingPurchase(id, doc);
		}
		return get(id);
	}

	// ---------- Reverse ----------

	@Transactional
	public DocumentDto reverse(long id, String reason) {
		DocRow original = lock(id);
		requireSupported(original.type());
		Long pairId = jdbc.queryForObject(
				"SELECT internal_pair_document_id FROM stock_documents WHERE id = ?", Long.class, id);
		if (pairId != null) {
			String thisType = original.type();
			long purchaseId = "PURCHASE".equals(thisType) ? id : pairId;
			long saleId = "SALE".equals(thisType) ? id : pairId;
			if ("CONFIRMED".equals(statusOf(purchaseId))) {
				reverseOne(purchaseId, reason);
			}
			if ("CONFIRMED".equals(statusOf(saleId))) {
				reverseOne(saleId, reason);
			}
			return get(findReversalId(id));
		}
		return get(reverseOne(id, reason));
	}

	private String statusOf(long documentId) {
		return jdbc.queryForObject("SELECT status FROM stock_documents WHERE id = ?", String.class, documentId);
	}

	private long findReversalId(long originalId) {
		List<Long> ids = jdbc.queryForList(
				"SELECT id FROM stock_documents WHERE reversal_of_document_id = ?", Long.class, originalId);
		if (ids.isEmpty()) {
			throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Không tìm thấy phiếu đảo", null);
		}
		return ids.get(0);
	}

	private long reverseOne(long id, String reason) {
		DocRow original = lockAny(id);
		Integer reversed = jdbc.queryForObject(
				"SELECT COUNT(*) FROM stock_documents WHERE reversal_of_document_id = ?", Integer.class, id);
		StockConfirmationPolicy.requireReversible(original.status(), reversed != null && reversed > 0);
		boolean internal = Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT is_internal FROM stock_documents WHERE id = ?", Boolean.class, id));
		if (!internal && paid(id, original.type()).signum() > 0) {
			throw new BusinessException(ErrorCode.DOCUMENT_NOT_REVERSIBLE,
					"Phiếu đã có thanh toán nên chưa đảo được. Chỉ đảo phiếu chưa thu hoặc chưa chi tiền", null);
		}
		String shopCode = jdbc.queryForObject("""
				SELECT s.code FROM stock_documents d JOIN shops s ON s.id = d.shop_id WHERE d.id = ?
				""", String.class, id);
		String reversalType = "PURCHASE".equals(original.type()) ? "ADJUSTMENT_OUT" : "ADJUSTMENT_IN";
		LocalDateTime now = LocalDateTime.now(clock);
		String code = sequences.nextCode(reversalType, now.toLocalDate(), shopCode);
		long reversalId = insertReturningId("""
				INSERT INTO stock_documents (document_code, document_type, status, document_date,
				    counterparty_name, reason, subtotal, discount_amount, total_amount, payment_status,
				    reversal_of_document_id, confirmed_at, notes, shop_id, is_internal)
				SELECT ?, ?, 'CONFIRMED', ?, counterparty_name, ?, subtotal, discount_amount, total_amount,
				       'UNPAID', id, ?, ?, shop_id, is_internal
				FROM stock_documents WHERE id = ?
				""", code, reversalType, Timestamp.valueOf(now), reason.trim(), Timestamp.valueOf(now),
				"Đảo phiếu " + original.code(), id);
		List<StoredItem> items = storedItems(id);
		items.sort(Comparator.comparingLong(StoredItem::productId).thenComparingLong(StoredItem::id));
		for (StoredItem item : items) {
			long newItemId = insertReturningId("""
					INSERT INTO stock_document_items (document_id, product_id, unit_id, product_code_snapshot,
					    product_name_snapshot, unit_name_snapshot, quantity_document, factor_to_base_snapshot,
					    quantity_base, unit_price, line_discount, line_total, cost_per_base_snapshot,
					    cost_total_snapshot, purchase_price_id, standard_cost_per_base_snapshot,
					    standard_cost_total_snapshot)
					SELECT ?, product_id, unit_id, product_code_snapshot, product_name_snapshot,
					       unit_name_snapshot, quantity_document, factor_to_base_snapshot, quantity_base,
					       unit_price, line_discount, line_total, cost_per_base_snapshot,
					       cost_total_snapshot, purchase_price_id, standard_cost_per_base_snapshot,
					       standard_cost_total_snapshot
					FROM stock_document_items WHERE id = ?
					""", reversalId, item.id());
			Balance balance = lockBalance(item.productId());
			BigDecimal itemCost = jdbc.queryForObject(
					"SELECT cost_per_base_snapshot FROM stock_document_items WHERE id = ?", BigDecimal.class, item.id());
			BigDecimal originalDelta = "PURCHASE".equals(original.type())
					? item.quantityBase() : item.quantityBase().negate();
			BigDecimal delta = InventoryMath.reversalDelta(originalDelta);
			BigDecimal newQuantity = InventoryMath.applyDelta(balance.quantity(), delta);
			BigDecimal newCost;
			if ("PURCHASE".equals(original.type())) {
				newCost = InventoryMath.averageAfterRemoval(
						balance.quantity(), balance.cost(), item.quantityBase(), itemCost);
			} else {
				newCost = InventoryMath.weightedAverageCost(
						balance.quantity(), balance.cost(), item.quantityBase(), itemCost);
			}
			jdbc.update("UPDATE stock_balances SET quantity_base = ?, average_cost_per_base = ? WHERE product_id = ?",
					newQuantity, newCost, item.productId());
			jdbc.update("""
					INSERT INTO stock_movements (document_id, document_item_id, product_id, movement_type,
					    quantity_delta_base, unit_cost, movement_at)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					""", reversalId, newItemId, item.productId(), delta.signum() > 0 ? "IN" : "OUT",
					delta, itemCost, Timestamp.valueOf(now));
		}
		jdbc.update("UPDATE stock_documents SET status = 'CANCELLED', cancelled_at = ? WHERE id = ?",
				Timestamp.valueOf(now), id);
		audit.record(CurrentAdmin.get().id(), "REVERSE_" + original.type(), "stock_documents",
				String.valueOf(id), "Đảo phiếu " + original.code() + " bằng " + code + ". Lý do: " + reason.trim());
		return reversalId;
	}

	// ---------- Payments ----------

	@Transactional
	public DocumentDto addPayment(long id, PaymentRequest request) {
		DocRow doc = lock(id);
		if (!"CONFIRMED".equals(doc.status())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ thu hoặc chi tiền cho phiếu đã xác nhận", null);
		}
		Boolean internal = jdbc.queryForObject(
				"SELECT is_internal FROM stock_documents WHERE id = ?", Boolean.class, id);
		if (Boolean.TRUE.equals(internal)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Phiếu nội bộ đã quyết toán giá vốn, không thu/chi tiền riêng", null);
		}
		requireSupported(doc.type());
		boolean sale = "SALE".equals(doc.type());
		String key = blank(request.idempotencyKey());
		if (key != null) {
			Integer duplicate = jdbc.queryForObject(sale
					? "SELECT COUNT(*) FROM sales_payments WHERE idempotency_key = ?"
					: "SELECT COUNT(*) FROM financial_transactions WHERE idempotency_key = ?", Integer.class, key);
			if (duplicate != null && duplicate > 0) {
				return get(id);
			}
		}
		BigDecimal total = jdbc.queryForObject(
				"SELECT total_amount FROM stock_documents WHERE id = ?", BigDecimal.class, id);
		BigDecimal alreadyPaid = paid(id, doc.type());
		BigDecimal amount = InventoryMath.snapshotMoney(request.amount());
		BigDecimal remaining = total.subtract(alreadyPaid);
		if (amount.compareTo(remaining) > 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Số tiền vượt phần còn lại của phiếu (" + remaining.stripTrailingZeros().toPlainString() + ")", null);
		}
		LocalDateTime when = request.paymentDate() == null ? LocalDateTime.now(clock) : request.paymentDate();
		String methodVi = localizePaymentMethod(request.paymentMethod());
		String userNote = blank(request.note());
		String financeCode = sequences.nextCode(sale ? "FINANCE_INCOME" : "FINANCE_EXPENSE", when.toLocalDate());
		String description = (sale ? "Thu tiền phiếu " : "Chi tiền phiếu ") + doc.code()
				+ ". Hình thức thanh toán: " + methodVi
				+ (userNote == null ? "" : ". Ghi chú: " + userNote);
		long financeId = insertReturningId("""
				INSERT INTO financial_transactions (transaction_code, transaction_date, transaction_type,
				    category, amount, description, reference_type, reference_id, status, idempotency_key,
				    shop_id, is_internal)
				VALUES (?, ?, ?, ?, ?, ?, 'STOCK_DOCUMENT', ?, 'CONFIRMED', ?, ?, ?)
				""", financeCode, Timestamp.valueOf(when), sale ? "INCOME" : "EXPENSE",
				sale ? "SALE_RECEIPT" : "PURCHASE_PAYMENT", amount, description, id, sale ? null : key,
				shops.currentId(), false);
		if (sale) {
			jdbc.update("""
					INSERT INTO sales_payments (document_id, payment_date, amount, payment_method, note,
					    financial_transaction_id, idempotency_key)
					VALUES (?, ?, ?, ?, ?, ?, ?)
					""", id, Timestamp.valueOf(when), amount, methodVi,
					userNote, financeId, key);
		}
		BigDecimal newPaid = alreadyPaid.add(amount);
		String status = newPaid.compareTo(total) >= 0 ? "PAID" : "PARTIAL";
		jdbc.update("UPDATE stock_documents SET payment_status = ? WHERE id = ?", status, id);
		audit.record(CurrentAdmin.get().id(), sale ? "RECEIVE_PAYMENT" : "MAKE_PAYMENT", "stock_documents",
				String.valueOf(id), description);
		return get(id);
	}

	// ---------- Stock views ----------

	@Transactional(readOnly = true)
	public List<StockBalanceDto> balances(String query, boolean onlyInStock) {
		StringBuilder sql = new StringBuilder("""
				SELECT p.id, p.product_code, p.name, c.name AS category_name, p.base_unit_name,
				       b.quantity_base, b.average_cost_per_base
				FROM products p
				JOIN product_categories c ON c.id = p.category_id
				JOIN stock_balances b ON b.product_id = p.id
				WHERE 1 = 1
				""" + shops.shopOnly("p"));
		List<Object> args = new ArrayList<>();
		if (query != null && !query.isBlank()) {
			sql.append(" AND (p.product_code LIKE ? OR p.name LIKE ?)");
			String like = "%" + query.trim() + "%";
			args.add(like);
			args.add(like);
		}
		if (onlyInStock) {
			sql.append(" AND b.quantity_base > 0");
		}
		sql.append(" ORDER BY p.product_code");
		return jdbc.query(sql.toString(), (rs, i) -> {
			BigDecimal quantity = rs.getBigDecimal("quantity_base");
			BigDecimal cost = rs.getBigDecimal("average_cost_per_base");
			return new StockBalanceDto(
					rs.getLong("id"), rs.getString("product_code"), rs.getString("name"),
					rs.getString("category_name"), rs.getString("base_unit_name"), quantity, cost,
					quantity.multiply(cost).setScale(InventoryMath.MONEY_SCALE, InventoryMath.ROUNDING));
		}, args.toArray());
	}

	@Transactional(readOnly = true)
	public List<MovementDto> movements(long productId) {
		return jdbc.query("""
				SELECT m.id, m.movement_at, d.document_code, m.movement_type, m.quantity_delta_base, m.unit_cost
				FROM stock_movements m
				JOIN stock_documents d ON d.id = m.document_id
				WHERE m.product_id = ?
				ORDER BY m.movement_at DESC, m.id DESC
				LIMIT 500
				""", (rs, i) -> new MovementDto(
				rs.getLong("id"), rs.getTimestamp("movement_at").toLocalDateTime(), rs.getString("document_code"),
				rs.getString("movement_type"), rs.getBigDecimal("quantity_delta_base"), rs.getBigDecimal("unit_cost")),
				productId);
	}

	// ---------- Internals ----------

	private List<Line> buildLines(DocumentRequest request) {
		boolean internalSale = Boolean.TRUE.equals(request.internal())
				&& "SALE".equals(request.documentType())
				&& shops.isDistribution();
		List<Line> lines = new ArrayList<>();
		for (ItemRequest item : request.items()) {
			ProductInfo product = jdbc.query("""
					SELECT id, product_code, name, is_active, shop_id FROM products WHERE id = ?
					""", (rs, i) -> new ProductInfo(
					rs.getLong("id"), rs.getString("product_code"), rs.getString("name"), rs.getBoolean("is_active"),
					rs.getLong("shop_id")),
					item.productId()).stream().findFirst()
					.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Sản phẩm không tồn tại", null));
			if (product.shopId() != shops.currentId()) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR,
						"Sản phẩm " + product.code() + " không thuộc cửa hàng đang chọn", null);
			}
			if (!product.active()) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR,
						"Sản phẩm " + product.code() + " đã ngừng dùng", null);
			}
			UnitInfo unit = jdbc.query("""
					SELECT id, unit_name, factor_to_base, is_active FROM product_units
					WHERE id = ? AND product_id = ?
					""", (rs, i) -> new UnitInfo(
					rs.getLong("id"), rs.getString("unit_name"), rs.getBigDecimal("factor_to_base"),
					rs.getBoolean("is_active")), item.unitId(), item.productId()).stream().findFirst()
					.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
							"Đơn vị không thuộc sản phẩm " + product.code(), null));
			if (!unit.active()) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Đơn vị " + unit.name() + " đã ngừng dùng", null);
			}
			requireUnitForShop(request.documentType(), unit.name());
			BigDecimal quantity = item.quantityDocument();
			BigDecimal discount = BigDecimal.ZERO;
			BigDecimal unitPrice = item.unitPrice();
			if (internalSale) {
				requireMatchingManufacturingProduct(product.code());
				Balance balance = readBalance(product.id());
				unitPrice = InventoryMath.roundVndToThousand(
						InventoryMath.suggestedUnitPrice(balance.cost(), unit.factor()));
				if (unitPrice.signum() <= 0) {
					throw new BusinessException(ErrorCode.VALIDATION_ERROR,
							"Sản phẩm " + product.code() + " chưa có giá vốn để chuyển nội bộ", null);
				}
			}
			lines.add(new Line(
					product, unit, quantity, unit.factor(),
					InventoryMath.toBaseQuantity(quantity, unit.factor()),
					unitPrice, discount,
					InventoryMath.lineTotal(quantity, unitPrice, discount)));
		}
		return lines;
	}

	private Totals totals(List<Line> lines, BigDecimal discountAmount) {
		BigDecimal subtotal = lines.stream().map(Line::lineTotal)
				.reduce(BigDecimal.ZERO, BigDecimal::add)
				.setScale(InventoryMath.MONEY_SCALE, InventoryMath.ROUNDING);
		BigDecimal discount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
		BigDecimal total = InventoryMath.documentTotal(subtotal, discount);
		return new Totals(subtotal, discount.setScale(InventoryMath.MONEY_SCALE, InventoryMath.ROUNDING), total);
	}

	private void insertLines(long documentId, List<Line> lines) {
		for (Line line : lines) {
			jdbc.update("""
					INSERT INTO stock_document_items (document_id, product_id, unit_id, product_code_snapshot,
					    product_name_snapshot, unit_name_snapshot, quantity_document, factor_to_base_snapshot,
					    quantity_base, unit_price, line_discount, line_total)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", documentId, line.product().id(), line.unit().id(), line.product().code(),
					line.product().name(), line.unit().name(), line.quantity(), line.factor(),
					line.quantityBase(), line.unitPrice(), line.discount(), line.lineTotal());
		}
	}

	private DocRow lock(long id) {
		return jdbc.query("""
				SELECT id, document_code, document_type, status, document_date
				FROM stock_documents WHERE id = ? AND shop_id = ? FOR UPDATE
				""", (rs, i) -> new DocRow(
				rs.getLong("id"), rs.getString("document_code"), rs.getString("document_type"),
				rs.getString("status"), rs.getTimestamp("document_date").toLocalDateTime()), id, shops.currentId())
				.stream().findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy phiếu", null));
	}

	private DocRow lockAny(long id) {
		return jdbc.query("""
				SELECT id, document_code, document_type, status, document_date
				FROM stock_documents WHERE id = ? FOR UPDATE
				""", (rs, i) -> new DocRow(
				rs.getLong("id"), rs.getString("document_code"), rs.getString("document_type"),
				rs.getString("status"), rs.getTimestamp("document_date").toLocalDateTime()), id)
				.stream().findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy phiếu", null));
	}

	private Balance lockBalance(long productId) {
		return jdbc.query("""
				SELECT quantity_base, average_cost_per_base FROM stock_balances WHERE product_id = ? FOR UPDATE
				""", (rs, i) -> new Balance(rs.getBigDecimal("quantity_base"), rs.getBigDecimal("average_cost_per_base")),
				productId).stream().findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "Thiếu số dư tồn kho", null));
	}

	private Balance readBalance(long productId) {
		return jdbc.query("""
				SELECT quantity_base, average_cost_per_base FROM stock_balances WHERE product_id = ?
				""", (rs, i) -> new Balance(rs.getBigDecimal("quantity_base"), rs.getBigDecimal("average_cost_per_base")),
				productId).stream().findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "Thiếu số dư tồn kho", null));
	}

	private List<StoredItem> storedItems(long documentId) {
		return new ArrayList<>(jdbc.query("""
				SELECT id, product_id, quantity_base, line_total FROM stock_document_items WHERE document_id = ?
				""", (rs, i) -> new StoredItem(
				rs.getLong("id"), rs.getLong("product_id"), rs.getBigDecimal("quantity_base"),
				rs.getBigDecimal("line_total")), documentId));
	}

	private List<ItemDto> items(long documentId) {
		return jdbc.query("""
				SELECT * FROM stock_document_items WHERE document_id = ? ORDER BY id
				""", (rs, i) -> new ItemDto(
				rs.getLong("id"), rs.getLong("product_id"), (Long) rs.getObject("unit_id"),
				rs.getString("product_code_snapshot"), rs.getString("product_name_snapshot"),
				rs.getString("unit_name_snapshot"), rs.getBigDecimal("quantity_document"),
				rs.getBigDecimal("factor_to_base_snapshot"), rs.getBigDecimal("quantity_base"),
				rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_discount"), rs.getBigDecimal("line_total"),
				rs.getBigDecimal("cost_per_base_snapshot"), rs.getBigDecimal("cost_total_snapshot"),
				(Long) rs.getObject("purchase_price_id"),
				rs.getBigDecimal("standard_cost_per_base_snapshot"),
				rs.getBigDecimal("standard_cost_total_snapshot")), documentId);
	}

	private BigDecimal paid(long documentId, String type) {
		BigDecimal value = "SALE".equals(type)
				? jdbc.queryForObject("SELECT COALESCE(SUM(amount), 0) FROM sales_payments WHERE document_id = ?",
						BigDecimal.class, documentId)
				: "PURCHASE".equals(type)
						? jdbc.queryForObject("""
								SELECT COALESCE(SUM(amount), 0) FROM financial_transactions
								WHERE reference_type = 'STOCK_DOCUMENT' AND reference_id = ?
								  AND category = 'PURCHASE_PAYMENT' AND status = 'CONFIRMED'
								""", BigDecimal.class, documentId)
						: BigDecimal.ZERO;
		return value == null ? BigDecimal.ZERO : value;
	}

	private List<PaymentDto> payments(long documentId, String type) {
		if ("SALE".equals(type)) {
			return jdbc.query("""
					SELECT id, payment_date, amount, payment_method, note FROM sales_payments
					WHERE document_id = ? ORDER BY payment_date, id
					""", (rs, i) -> new PaymentDto(
					rs.getLong("id"), rs.getTimestamp("payment_date").toLocalDateTime(), rs.getBigDecimal("amount"),
					localizePaymentMethod(rs.getString("payment_method")), rs.getString("note")), documentId);
		}
		if ("PURCHASE".equals(type)) {
			return jdbc.query("""
					SELECT id, transaction_date, amount, description FROM financial_transactions
					WHERE reference_type = 'STOCK_DOCUMENT' AND reference_id = ?
					  AND category = 'PURCHASE_PAYMENT' AND status = 'CONFIRMED'
					ORDER BY transaction_date, id
					""", (rs, i) -> {
				String description = rs.getString("description");
				return new PaymentDto(
						rs.getLong("id"),
						rs.getTimestamp("transaction_date").toLocalDateTime(),
						rs.getBigDecimal("amount"),
						extractPaymentMethodFromDescription(description),
						extractPaymentNoteFromDescription(description));
			}, documentId);
		}
		return List.of();
	}

	private static String localizePaymentMethod(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String value = raw.trim();
		String upper = value.toUpperCase();
		if ("CASH".equals(upper) || "TIỀN MẶT".equalsIgnoreCase(value) || value.equalsIgnoreCase("Tien mat")) {
			return "Tiền mặt";
		}
		if ("TRANSFER".equals(upper) || "CHUYỂN KHOẢN".equalsIgnoreCase(value)
				|| value.equalsIgnoreCase("Chuyen khoan")) {
			return "Chuyển khoản";
		}
		if (upper.contains("CASH")) {
			return "Tiền mặt";
		}
		if (upper.contains("TRANSFER")) {
			return "Chuyển khoản";
		}
		return value;
	}

	private static String extractPaymentMethodFromDescription(String description) {
		if (description == null || description.isBlank()) {
			return null;
		}
		String marker = "Hình thức thanh toán: ";
		int start = description.indexOf(marker);
		if (start >= 0) {
			String rest = description.substring(start + marker.length());
			int end = rest.indexOf(". Ghi chú:");
			String raw = end >= 0 ? rest.substring(0, end) : rest;
			return localizePaymentMethod(raw.trim());
		}
		return localizePaymentMethod(description);
	}

	private static String extractPaymentNoteFromDescription(String description) {
		if (description == null || description.isBlank()) {
			return null;
		}
		String marker = "Ghi chú: ";
		int start = description.indexOf(marker);
		if (start < 0) {
			return null;
		}
		return description.substring(start + marker.length()).trim();
	}

	private Long reversedBy(long documentId) {
		List<Long> ids = jdbc.queryForList(
				"SELECT id FROM stock_documents WHERE reversal_of_document_id = ?", Long.class, documentId);
		return ids.isEmpty() ? null : ids.get(0);
	}

	private long insertReturningId(String sql, Object... args) {
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

	private static void requireSupported(String type) {
		if (!SUPPORTED.contains(type)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Loại phiếu không hỗ trợ", null);
		}
	}

	/** Phiếu nhập bắt buộc người nhập; phiếu bán bắt buộc người xuất. Tên không bắt đầu bằng số. */
	private static void requirePurchaseReceivedBy(DocumentRequest request) {
		NameValidation.requireNoLeadingDigit(
				"PURCHASE".equals(request.documentType()) ? "Nhà cung cấp" : "Khách hàng",
				request.counterpartyName());
		NameValidation.requireNoLeadingDigit(
				"PURCHASE".equals(request.documentType()) ? "Người giao" : "Người xuất",
				request.issuedBy());
		NameValidation.requireNoLeadingDigit(
				"PURCHASE".equals(request.documentType()) ? "Người nhập" : "Người nhận",
				request.receivedBy());
		if ("SALE".equals(request.documentType())) {
			String issuedBy = blank(request.issuedBy());
			if (issuedBy == null) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Nhập người xuất", null);
			}
			if (issuedBy.length() > 150) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Người xuất tối đa 150 ký tự", null);
			}
			return;
		}
		if (!"PURCHASE".equals(request.documentType())) {
			return;
		}
		String receivedBy = blank(request.receivedBy());
		if (receivedBy == null) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Nhập người nhập", null);
		}
		if (receivedBy.length() > 50) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Người nhập tối đa 50 ký tự", null);
		}
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private record DocRow(long id, String code, String type, String status, LocalDateTime date) {
	}

	private boolean resolveInternal(DocumentRequest request) {
		if (!Boolean.TRUE.equals(request.internal())) {
			return false;
		}
		if (!(shops.isDistribution() && "SALE".equals(request.documentType()))) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Chỉ phiếu bán của phân phối được đánh dấu giao dịch nội bộ sang xưởng", null);
		}
		return true;
	}

	private void requireMatchingManufacturingProduct(String productCode) {
		long manufacturingShopId = shops.idOf(ShopContext.MANUFACTURING);
		Integer count = jdbc.queryForObject("""
				SELECT COUNT(*) FROM products
				WHERE shop_id = ? AND product_code = ? AND is_active = 1
				""", Integer.class, manufacturingShopId, productCode);
		if (count == null || count == 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Xưởng chưa có sản phẩm cùng mã " + productCode
							+ ". Hãy tạo cùng mã ở danh mục xưởng trước khi chuyển nội bộ",
					null);
		}
		Integer tonUnit = jdbc.queryForObject("""
				SELECT COUNT(*) FROM products p
				JOIN product_units u ON u.product_id = p.id
				WHERE p.shop_id = ? AND p.product_code = ? AND p.is_active = 1
				  AND u.unit_name = 'tấn' AND u.is_active = 1
				""", Integer.class, manufacturingShopId, productCode);
		if (tonUnit == null || tonUnit == 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Sản phẩm " + productCode + " bên xưởng cần có đơn vị tấn đang dùng để nhận hàng nội bộ",
					null);
		}
	}

	/**
	 * Sau khi xác nhận bán nội bộ ở phân phối: tự tạo + xác nhận phiếu nhập xưởng
	 * cùng mã SP, cùng số tấn, đúng giá gốc (đã làm tròn hàng nghìn).
	 */
	private void createPairedManufacturingPurchase(long saleId, DocRow sale) {
		long manufacturingShopId = shops.idOf(ShopContext.MANUFACTURING);
		List<MapRow> saleLines = jdbc.query("""
				SELECT product_code_snapshot, quantity_document, unit_price, line_total, quantity_base,
				       factor_to_base_snapshot
				FROM stock_document_items WHERE document_id = ? ORDER BY id
				""", (rs, i) -> new MapRow(
				rs.getString("product_code_snapshot"),
				rs.getBigDecimal("quantity_document"),
				rs.getBigDecimal("unit_price"),
				rs.getBigDecimal("line_total"),
				rs.getBigDecimal("quantity_base"),
				rs.getBigDecimal("factor_to_base_snapshot")), saleId);

		BigDecimal subtotal = BigDecimal.ZERO.setScale(InventoryMath.MONEY_SCALE, InventoryMath.ROUNDING);
		List<PairLine> pairLines = new ArrayList<>();
		for (MapRow line : saleLines) {
			requireMatchingManufacturingProduct(line.code());
			ProductInfo mfgProduct = jdbc.query("""
					SELECT id, product_code, name, is_active, shop_id FROM products
					WHERE shop_id = ? AND product_code = ? AND is_active = 1
					""", (rs, i) -> new ProductInfo(
					rs.getLong("id"), rs.getString("product_code"), rs.getString("name"),
					rs.getBoolean("is_active"), rs.getLong("shop_id")),
					manufacturingShopId, line.code()).stream().findFirst()
					.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
							"Không tìm thấy sản phẩm " + line.code() + " bên xưởng", null));
			UnitInfo ton = jdbc.query("""
					SELECT id, unit_name, factor_to_base, is_active FROM product_units
					WHERE product_id = ? AND unit_name = 'tấn' AND is_active = 1
					""", (rs, i) -> new UnitInfo(
					rs.getLong("id"), rs.getString("unit_name"), rs.getBigDecimal("factor_to_base"),
					rs.getBoolean("is_active")), mfgProduct.id()).stream().findFirst()
					.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
							"Sản phẩm " + line.code() + " bên xưởng thiếu đơn vị tấn", null));
			BigDecimal qtyBase = InventoryMath.toBaseQuantity(line.quantity(), ton.factor());
			if (qtyBase.compareTo(line.quantityBase()) != 0) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR,
						"Hệ số tấn→kg của " + line.code() + " ở xưởng khác phân phối, không chuyển nội bộ được",
						null);
			}
			pairLines.add(new PairLine(mfgProduct, ton, line.quantity(), line.unitPrice(), line.lineTotal(), qtyBase));
			subtotal = subtotal.add(line.lineTotal());
		}

		String purchaseCode = sequences.nextCode("PURCHASE", sale.date().toLocalDate(), ShopContext.MANUFACTURING);
		LocalDateTime now = LocalDateTime.now(clock);
		long purchaseId = insertReturningId("""
				INSERT INTO stock_documents (document_code, document_type, status, document_date,
				    counterparty_name, issued_by, received_by, reason, subtotal, discount_amount,
				    total_amount, payment_status, notes, shop_id, is_internal, confirmed_at,
				    internal_pair_document_id)
				VALUES (?, 'PURCHASE', 'CONFIRMED', ?, ?, ?, ?, ?, ?, 0, ?, 'PAID', ?, ?, 1, ?, ?)
				""",
				purchaseCode,
				Timestamp.valueOf(sale.date()),
				"Phân phối nhôm khối (nội bộ)",
				"Phân phối",
				"Hệ thống",
				"Nhận hàng nội bộ từ phân phối",
				subtotal,
				subtotal,
				"Tự tạo từ phiếu bán nội bộ " + sale.code() + ". Giá = giá vốn gốc.",
				manufacturingShopId,
				Timestamp.valueOf(now),
				saleId);

		jdbc.update("UPDATE stock_documents SET internal_pair_document_id = ? WHERE id = ?", purchaseId, saleId);

		LocalDate documentDay = sale.date().toLocalDate();
		for (PairLine line : pairLines) {
			long itemId = insertReturningId("""
					INSERT INTO stock_document_items (document_id, product_id, unit_id, product_code_snapshot,
					    product_name_snapshot, unit_name_snapshot, quantity_document, factor_to_base_snapshot,
					    quantity_base, unit_price, line_discount, line_total)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
					""", purchaseId, line.product().id(), line.unit().id(), line.product().code(),
					line.product().name(), line.unit().name(), line.quantity(), line.unit().factor(),
					line.quantityBase(), line.unitPrice(), line.lineTotal());

			Balance balance = lockBalance(line.product().id());
			BigDecimal costPerBase = line.lineTotal().divide(
					line.quantityBase(), InventoryMath.COST_SCALE, InventoryMath.ROUNDING);
			BigDecimal newCost = InventoryMath.weightedAverageCost(
					balance.quantity(), balance.cost(), line.quantityBase(), costPerBase);
			BigDecimal newQuantity = InventoryMath.applyDelta(balance.quantity(), line.quantityBase());
			ResolvedPrice standard = purchasePrices.resolve(line.product().id(), documentDay);
			BigDecimal standardPerBase = standard == null ? null : standard.pricePerBase();
			BigDecimal standardTotal = standardPerBase == null
					? null
					: InventoryMath.costTotal(line.quantityBase(), standardPerBase);
			jdbc.update("""
					UPDATE stock_document_items
					SET cost_per_base_snapshot = ?, cost_total_snapshot = ?,
					    purchase_price_id = ?, standard_cost_per_base_snapshot = ?,
					    standard_cost_total_snapshot = ?
					WHERE id = ?
					""", costPerBase, line.lineTotal(),
					standard == null ? null : standard.priceId(),
					standardPerBase, standardTotal, itemId);
			jdbc.update("UPDATE stock_balances SET quantity_base = ?, average_cost_per_base = ? WHERE product_id = ?",
					newQuantity, newCost, line.product().id());
			jdbc.update("""
					INSERT INTO stock_movements (document_id, document_item_id, product_id, movement_type,
					    quantity_delta_base, unit_cost, movement_at)
					VALUES (?, ?, ?, 'IN', ?, ?, ?)
					""", purchaseId, itemId, line.product().id(), line.quantityBase(), costPerBase,
					Timestamp.valueOf(sale.date()));
		}
		audit.record(CurrentAdmin.get().id(), "CONFIRM_PURCHASE", "stock_documents",
				String.valueOf(purchaseId),
				"Tự tạo phiếu nhập nội bộ xưởng " + purchaseCode + " từ " + sale.code());
	}

	private record MapRow(
			String code, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal,
			BigDecimal quantityBase, BigDecimal factor) {
	}

	private record PairLine(
			ProductInfo product, UnitInfo unit, BigDecimal quantity, BigDecimal unitPrice,
			BigDecimal lineTotal, BigDecimal quantityBase) {
	}

	private void requireUnitForShop(String documentType, String unitName) {
		String unit = unitName == null ? "" : unitName.trim();
		if (shops.isDistribution()) {
			if (!"tấn".equals(unit)) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phân phối chỉ nhập và bán theo tấn", null);
			}
			return;
		}
		if ("SALE".equals(documentType)) {
			if (!"kg".equalsIgnoreCase(unit)) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Xưởng chỉ bán theo kg", null);
			}
			return;
		}
		if ("PURCHASE".equals(documentType) && !"tấn".equals(unit) && !"tạ".equals(unit)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Xưởng chỉ nhập theo tạ hoặc tấn", null);
		}
	}

	private record Balance(BigDecimal quantity, BigDecimal cost) {
	}

	private record StoredItem(long id, long productId, BigDecimal quantityBase, BigDecimal lineTotal) {
	}

	private record ProductInfo(long id, String code, String name, boolean active, long shopId) {
	}

	private record UnitInfo(long id, String name, BigDecimal factor, boolean active) {
	}

	private record Line(
			ProductInfo product, UnitInfo unit, BigDecimal quantity, BigDecimal factor, BigDecimal quantityBase,
			BigDecimal unitPrice, BigDecimal discount, BigDecimal lineTotal) {
	}

	private record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal total) {
	}
}
