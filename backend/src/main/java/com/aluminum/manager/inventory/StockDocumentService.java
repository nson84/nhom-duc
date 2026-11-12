package com.aluminum.manager.inventory;

import com.aluminum.manager.catalog.CatalogService;
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
import com.aluminum.manager.inventory.StockDtos.InventoryCountItemRequest;
import com.aluminum.manager.inventory.StockDtos.InventoryCountResult;
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

	private static final Set<String> SUPPORTED = Set.of("PURCHASE", "SALE", "ADJUSTMENT_IN", "ADJUSTMENT_OUT");

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

	private static final int TRASH_RETENTION_DAYS = 90;

	/**
	 * @param filter all|unpaid|partial|paid|draft|voided|trash — mặc định all
	 */
	@Transactional(readOnly = true)
	public List<DocumentSummary> list(String type, String filter, LocalDate from, LocalDate to, String query) {
		String bucket = filter == null || filter.isBlank() ? "all" : filter.trim().toLowerCase();
		if ("trash".equals(bucket)) {
			purgeExpiredTrash();
		}
		StringBuilder sql = new StringBuilder("""
				SELECT d.id, d.document_code, d.document_type, d.status, d.document_date, d.counterparty_name,
				       d.customer_id, d.total_amount, d.payment_status, d.is_internal, d.internal_pair_document_id,
				       p.document_code AS pair_code, d.trashed_at
				FROM stock_documents d
				LEFT JOIN stock_documents p ON p.id = d.internal_pair_document_id
				LEFT JOIN customers cust ON cust.id = d.customer_id
				WHERE 1 = 1
				""" + shops.shopOnly("d"));
		List<Object> args = new ArrayList<>();
		if (type != null && !type.isBlank()) {
			sql.append(" AND d.document_type = ?");
			args.add(type);
		}
		switch (bucket) {
			case "trash" -> sql.append(" AND d.trashed_at IS NOT NULL");
			case "draft" -> sql.append(" AND d.trashed_at IS NULL AND d.status = 'DRAFT'");
			case "voided" -> sql.append(" AND d.trashed_at IS NULL AND d.status = 'CANCELLED'");
			case "partial" -> sql.append(
					" AND d.trashed_at IS NULL AND d.status = 'CONFIRMED' AND d.payment_status = 'PARTIAL'");
			case "paid" -> sql.append(
					" AND d.trashed_at IS NULL AND d.status = 'CONFIRMED' AND d.payment_status = 'PAID'");
			case "unpaid" -> sql.append(
					" AND d.trashed_at IS NULL AND d.status = 'CONFIRMED' AND d.payment_status = 'UNPAID'");
			default -> sql.append(" AND d.trashed_at IS NULL");
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
			sql.append("""
					 AND (d.document_code LIKE ?
					   OR IFNULL(d.counterparty_name, '') LIKE ?
					   OR IFNULL(cust.name, '') LIKE ?
					   OR IFNULL(cust.phone, '') LIKE ?)
					""");
			String like = "%" + query.trim() + "%";
			args.add(like);
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if ("trash".equals(bucket)) {
			sql.append(" ORDER BY d.trashed_at DESC, d.id DESC LIMIT 500");
		} else {
			sql.append(" ORDER BY d.document_date DESC, d.id DESC LIMIT 500");
		}
		return jdbc.query(sql.toString(), (rs, i) -> new DocumentSummary(
				rs.getLong("id"),
				rs.getString("document_code"),
				rs.getString("document_type"),
				rs.getString("status"),
				rs.getTimestamp("document_date").toLocalDateTime(),
				rs.getString("counterparty_name"),
				(Long) rs.getObject("customer_id"),
				rs.getBigDecimal("total_amount"),
				rs.getString("payment_status"),
				rs.getBoolean("is_internal"),
				(Long) rs.getObject("internal_pair_document_id"),
				rs.getString("pair_code"),
				rs.getTimestamp("trashed_at") == null ? null : rs.getTimestamp("trashed_at").toLocalDateTime()),
				args.toArray());
	}

	@Transactional
	public DocumentDto moveToTrash(long id) {
		DocRow doc = lock(id);
		boolean reversed = "CONFIRMED".equals(doc.status()) && reversedBy(id) != null;
		if (!"CANCELLED".equals(doc.status()) && !reversed) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Chỉ chuyển phiếu đã hủy hoặc đã đảo vào thùng rác", null);
		}
		Timestamp already = jdbc.queryForObject(
				"SELECT trashed_at FROM stock_documents WHERE id = ?", Timestamp.class, id);
		if (already != null) {
			return get(id);
		}
		jdbc.update("UPDATE stock_documents SET trashed_at = ? WHERE id = ?",
				Timestamp.valueOf(LocalDateTime.now(clock)), id);
		audit.record(CurrentAdmin.get().id(), "TRASH_DOCUMENT", "stock_documents",
				String.valueOf(id), "Chuyển thùng rác " + doc.code());
		return get(id);
	}

	@Transactional
	public DocumentDto restoreFromTrash(long id) {
		DocRow doc = lock(id);
		Timestamp trashed = jdbc.queryForObject(
				"SELECT trashed_at FROM stock_documents WHERE id = ?", Timestamp.class, id);
		if (trashed == null) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phiếu không nằm trong thùng rác", null);
		}
		jdbc.update("UPDATE stock_documents SET trashed_at = NULL WHERE id = ?", id);
		audit.record(CurrentAdmin.get().id(), "RESTORE_DOCUMENT", "stock_documents",
				String.valueOf(id), "Khôi phục khỏi thùng rác " + doc.code());
		return get(id);
	}

	/** Xóa cứng phiếu trong thùng rác quá 90 ngày (chỉ khi không còn ràng buộc kho/thu). */
	@Transactional
	public int purgeExpiredTrash() {
		LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(TRASH_RETENTION_DAYS);
		List<Long> ids = jdbc.queryForList("""
				SELECT d.id FROM stock_documents d
				WHERE d.trashed_at IS NOT NULL AND d.trashed_at < ?
				  AND d.status IN ('DRAFT', 'CANCELLED')
				  AND NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.document_id = d.id)
				  AND NOT EXISTS (SELECT 1 FROM sales_payments p WHERE p.document_id = d.id)
				""", Long.class, Timestamp.valueOf(cutoff));
		int removed = 0;
		for (Long id : ids) {
			Long owner = jdbc.queryForObject("SELECT shop_id FROM stock_documents WHERE id = ?", Long.class, id);
			if (owner == null || owner != shops.currentId()) {
				continue;
			}
			jdbc.update("DELETE FROM stock_document_items WHERE document_id = ?", id);
			jdbc.update("""
					DELETE FROM financial_transactions
					WHERE reference_type = 'STOCK_DOCUMENT' AND reference_id = ?
					""", id);
			jdbc.update("DELETE FROM stock_documents WHERE id = ?", id);
			removed++;
		}
		if (removed > 0) {
			audit.record(CurrentAdmin.get().id(), "PURGE_TRASH", "stock_documents", null,
					"Xóa cứng " + removed + " phiếu quá " + TRASH_RETENTION_DAYS + " ngày trong thùng rác");
		}
		return removed;
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
				(Long) rs.getObject("customer_id"),
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
				rs.getString("pair_code"),
				rs.getTimestamp("trashed_at") == null ? null : rs.getTimestamp("trashed_at").toLocalDateTime()), id);
		if (rows.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy phiếu", null);
		}
		return rows.get(0);
	}

	// ---------- Draft lifecycle ----------

	@Transactional
	public DocumentDto saveConfirmedSale(Long id, DocumentRequest request) {
		if (!shops.isDistribution() || !"SALE".equals(request.documentType())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ áp dụng cho phiếu bán Phân phối", null);
		}
		DocumentDto saved = id == null ? createDraft(request) : updateDraft(id, request);
		return "CONFIRMED".equals(saved.status()) ? saved : confirm(saved.id());
	}

	@Transactional
	public DocumentDto createDraft(DocumentRequest request) {
		requireSupported(request.documentType());
		requirePurchaseReceivedBy(request);
		if (!shops.isDistribution()) assertFactoryYearOpen(request.documentDate());
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
		Long customerId = resolveCustomerId(request);
		String counterparty = resolveCounterpartyName(request, customerId);
		String code = sequences.nextCode(request.documentType(), request.documentDate().toLocalDate());
		long id = insertReturningId("""
				INSERT INTO stock_documents (document_code, document_type, status, document_date,
				    counterparty_name, customer_id, issued_by, received_by, reason, subtotal, discount_amount,
				    total_amount, payment_status, payment_method, idempotency_key, notes, shop_id, is_internal)
				VALUES (?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?, ?, 'UNPAID', ?, ?, ?, ?, ?)
				""",
				code, request.documentType(), Timestamp.valueOf(request.documentDate()),
				counterparty, customerId, blank(request.issuedBy()), blank(request.receivedBy()),
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
		if (!shops.isDistribution()) {
			assertFactoryYearOpen(doc.date());
			assertFactoryYearOpen(request.documentDate());
		}
		if (!doc.type().equals(request.documentType())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không đổi loại phiếu được", null);
		}
		requirePurchaseReceivedBy(request);
		List<Line> lines = buildLines(request);
		Totals totals = totals(lines, request.discountAmount());
		boolean internal = resolveInternal(request);
		Long customerId = resolveCustomerId(request);
		String counterparty = resolveCounterpartyName(request, customerId);
		jdbc.update("""
				UPDATE stock_documents
				SET document_date = ?, counterparty_name = ?, customer_id = ?, issued_by = ?, received_by = ?, reason = ?,
				    subtotal = ?, discount_amount = ?, total_amount = ?, payment_method = ?, notes = ?,
				    is_internal = ?
				WHERE id = ?
				""",
				Timestamp.valueOf(request.documentDate()), counterparty, customerId,
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
		if (!shops.isDistribution()) assertFactoryYearOpen(doc.date());
		List<StoredItem> items = storedItems(id);
		if (items.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phiếu chưa có dòng hàng", null);
		}
		if ("SALE".equals(doc.type())) {
			Integer areaLines = jdbc.queryForObject("""
					SELECT COUNT(*) FROM stock_document_items
					WHERE document_id = ? AND (billing_unit = 'm2' OR unit_name_snapshot IN ('m²', 'm2'))
					""", Integer.class, id);
			if (areaLines != null && areaLines > 0) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR,
						"Đã ngừng bán theo m². Hãy sửa phiếu nháp thành bán theo cân trước khi xác nhận", null);
			}
		}
		items.sort(Comparator
				.comparingLong(StoredItem::productId)
				.thenComparingLong(StoredItem::categoryId)
				.thenComparingLong(StoredItem::id));
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDate documentDay = doc.date().toLocalDate();
		// Xưởng ghi khối lượng thành phẩm đã giao để quyết toán cuối năm.
		// Các phiếu cũ vẫn giữ biến động kho lịch sử; phiếu mới không trừ kho theo ngày.
		boolean periodicManufacturingSale = StockConfirmationPolicy.skipsStock(shops.isDistribution(), doc.type());
		if (periodicManufacturingSale) {
			Long customerId = jdbc.queryForObject(
					"SELECT customer_id FROM stock_documents WHERE id = ?", Long.class, id);
			if (customerId == null) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR,
						"Gắn khách mua vào phiếu để thống kê kg và công nợ theo người", null);
			}
		}
		for (StoredItem item : items) {
			if (periodicManufacturingSale) {
				jdbc.update("""
					UPDATE stock_document_items
					SET cost_per_base_snapshot = NULL, cost_total_snapshot = NULL,
					    purchase_price_id = NULL, standard_cost_per_base_snapshot = NULL,
					    standard_cost_total_snapshot = NULL
					WHERE id = ?
					""", item.id());
				continue;
			}
			Balance balance = lockBalance(item.productId(), item.categoryId());
			BigDecimal costPerBase;
			BigDecimal delta;
			BigDecimal newQuantity;
			BigDecimal newCost;
			String movementType;
			if (shops.isDistribution()) {
				boolean incoming = "PURCHASE".equals(doc.type()) || "ADJUSTMENT_IN".equals(doc.type());
				delta = incoming ? item.quantityBase() : item.quantityBase().negate();
				newQuantity = InventoryMath.applyDelta(balance.quantity(), delta);
				newCost = BigDecimal.ZERO;
				costPerBase = "PURCHASE".equals(doc.type())
						? item.lineTotal().divide(item.quantityBase(), InventoryMath.COST_SCALE, InventoryMath.ROUNDING)
						: BigDecimal.ZERO;
				movementType = incoming ? "IN" : "OUT";
			} else if ("PURCHASE".equals(doc.type()) || "ADJUSTMENT_IN".equals(doc.type())) {
				costPerBase = item.lineTotal().divide(item.quantityBase(), InventoryMath.COST_SCALE, InventoryMath.ROUNDING);
				if ("PURCHASE".equals(doc.type())) {
					newCost = InventoryMath.weightedAverageCost(
							balance.quantity(), balance.cost(), item.quantityBase(), costPerBase);
				} else {
					costPerBase = balance.cost();
					newCost = balance.cost();
				}
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
			boolean recordCost = !shops.isDistribution() || "PURCHASE".equals(doc.type());
			ResolvedPrice standard = recordCost ? purchasePrices.resolve(item.categoryId(), documentDay) : null;
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
					""", recordCost ? costPerBase : null, recordCost ? costTotal : null,
					standard == null ? null : standard.priceId(),
					standardPerBase, standardTotal, item.id());
			jdbc.update("""
					UPDATE stock_balances SET quantity_base = ?, average_cost_per_base = ?
					WHERE product_id = ? AND category_id = ?
					""", newQuantity, newCost, item.productId(), item.categoryId());
			jdbc.update("""
					INSERT INTO stock_movements (document_id, document_item_id, product_id, category_id,
					    movement_type, quantity_delta_base, unit_cost, movement_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?)
					""", id, item.id(), item.productId(), item.categoryId(), movementType, delta, costPerBase,
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
			assertFactoryYearOpen(doc.date());
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
		String originalShop = jdbc.queryForObject("""
				SELECT s.code FROM stock_documents d JOIN shops s ON s.id = d.shop_id WHERE d.id = ?
				""", String.class, id);
		if (ShopContext.MANUFACTURING.equals(originalShop)) assertFactoryYearOpen(original.date());
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
		boolean originalIncoming = "PURCHASE".equals(original.type()) || "ADJUSTMENT_IN".equals(original.type());
		String reversalType = originalIncoming ? "ADJUSTMENT_OUT" : "ADJUSTMENT_IN";
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
		items.sort(Comparator
				.comparingLong(StoredItem::productId)
				.thenComparingLong(StoredItem::categoryId)
				.thenComparingLong(StoredItem::id));
		for (StoredItem item : items) {
			long newItemId = insertReturningId("""
					INSERT INTO stock_document_items (document_id, product_id, category_id, unit_id,
					    product_code_snapshot, product_name_snapshot, category_name_snapshot,
					    unit_name_snapshot, billing_unit, width_m, height_m,
					    quantity_document, factor_to_base_snapshot,
					    quantity_base, unit_price, line_discount, line_total, cost_per_base_snapshot,
					    cost_total_snapshot, purchase_price_id, standard_cost_per_base_snapshot,
					    standard_cost_total_snapshot)
					SELECT ?, product_id, category_id, unit_id, product_code_snapshot, product_name_snapshot,
					       category_name_snapshot, unit_name_snapshot, billing_unit, width_m, height_m,
					       quantity_document, factor_to_base_snapshot, quantity_base,
					       unit_price, line_discount, line_total, cost_per_base_snapshot,
					       cost_total_snapshot, purchase_price_id, standard_cost_per_base_snapshot,
					       standard_cost_total_snapshot
					FROM stock_document_items WHERE id = ?
					""", reversalId, item.id());
			// Phiếu bán xưởng theo kiểm kê cuối kỳ không có biến động kho để đảo.
			Integer movementCount = jdbc.queryForObject(
					"SELECT COUNT(*) FROM stock_movements WHERE document_item_id = ?", Integer.class, item.id());
			if (movementCount == null || movementCount == 0) {
				continue;
			}
			Balance balance = lockBalance(item.productId(), item.categoryId());
			BigDecimal itemCost = jdbc.queryForObject(
					"SELECT cost_per_base_snapshot FROM stock_document_items WHERE id = ?", BigDecimal.class, item.id());
			BigDecimal originalDelta = originalIncoming
					? item.quantityBase() : item.quantityBase().negate();
			BigDecimal delta = InventoryMath.reversalDelta(originalDelta);
			BigDecimal newQuantity = InventoryMath.applyDelta(balance.quantity(), delta);
			BigDecimal newCost;
			if (ShopContext.DISTRIBUTION.equals(originalShop)) {
				newCost = BigDecimal.ZERO;
				itemCost = BigDecimal.ZERO;
			} else if (originalIncoming) {
				newCost = InventoryMath.averageAfterRemoval(
						balance.quantity(), balance.cost(), item.quantityBase(), itemCost);
			} else {
				newCost = InventoryMath.weightedAverageCost(
						balance.quantity(), balance.cost(), item.quantityBase(), itemCost);
			}
			jdbc.update("""
					UPDATE stock_balances SET quantity_base = ?, average_cost_per_base = ?
					WHERE product_id = ? AND category_id = ?
					""", newQuantity, newCost, item.productId(), item.categoryId());
			jdbc.update("""
					INSERT INTO stock_movements (document_id, document_item_id, product_id, category_id,
					    movement_type, quantity_delta_base, unit_cost, movement_at)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?)
					""", reversalId, newItemId, item.productId(), item.categoryId(),
					delta.signum() > 0 ? "IN" : "OUT", delta, itemCost, Timestamp.valueOf(now));
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
					"Phiếu nội bộ không ghi thu hoặc chi tiền riêng", null);
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
		// Tiền mặt VN: chỉ nhận bội 500 đ (…,000 hoặc …,500), không nhận số lẻ kiểu 1.111.
		if (amount.compareTo(remaining) != 0 && amount.remainder(BigDecimal.valueOf(500)).signum() != 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Số tiền phải là bội 500 đ (VD: 1.000 hoặc 1.500)", null);
		}
		LocalDateTime when = request.paymentDate() == null ? LocalDateTime.now(clock) : request.paymentDate();
		if (!shops.isDistribution()) assertFactoryYearOpen(when);
		String methodVi = localizePaymentMethod(request.paymentMethod());
		String userNote = blank(request.note());
		String typedCollector = blank(request.collectedBy());
		String collector = sale ? (typedCollector != null ? typedCollector : CurrentAdmin.get().username()) : typedCollector;
		String financeCode = sequences.nextCode(sale ? "FINANCE_INCOME" : "FINANCE_EXPENSE", when.toLocalDate());
		String description = (sale ? "Thu tiền phiếu " : "Chi tiền phiếu ") + doc.code()
				+ ". Hình thức thanh toán: " + methodVi
				+ (blank(request.paidBy()) == null ? "" : ". Người trả: " + request.paidBy().trim())
				+ (collector == null ? "" : ". Người thu: " + collector)
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
					    collected_by_name, financial_transaction_id, idempotency_key)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?)
					""", id, Timestamp.valueOf(when), amount, methodVi,
					userNote, collector, financeId, key);
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
				SELECT p.id AS product_id, c.id AS category_id, p.product_code, p.name,
				       c.name AS category_name, p.base_unit_name,
				       b.quantity_base, b.average_cost_per_base,
				       COALESCE(flow.purchased_kg, 0) AS purchased_kg,
				       COALESCE(flow.delivered_kg, 0) AS delivered_kg
				FROM stock_balances b
				JOIN products p ON p.id = b.product_id
				JOIN product_categories c ON c.id = b.category_id
				LEFT JOIN (
				    SELECT i.product_id, i.category_id,
				           SUM(CASE WHEN d.document_type = 'PURCHASE' THEN i.quantity_base ELSE 0 END) AS purchased_kg,
				           SUM(CASE WHEN d.document_type = 'SALE' THEN i.quantity_base ELSE 0 END) AS delivered_kg
				    FROM stock_document_items i
				    JOIN stock_documents d ON d.id = i.document_id
				    WHERE d.status = 'CONFIRMED' AND d.document_type IN ('PURCHASE', 'SALE')
				      AND d.reversal_of_document_id IS NULL
				      AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
				    GROUP BY i.product_id, i.category_id
				) flow ON flow.product_id = b.product_id AND flow.category_id = b.category_id
				WHERE 1 = 1
				""" + shops.shopOnly("p"));
		List<Object> args = new ArrayList<>();
		if (query != null && !query.isBlank()) {
			sql.append(" AND (p.product_code LIKE ? OR p.name LIKE ? OR c.name LIKE ?)");
			String like = "%" + query.trim() + "%";
			args.add(like);
			args.add(like);
			args.add(like);
		}
		if (onlyInStock) {
			sql.append(" AND b.quantity_base > 0");
		}
		sql.append(" ORDER BY p.product_code, c.name");
		return jdbc.query(sql.toString(), (rs, i) -> {
			BigDecimal quantity = rs.getBigDecimal("quantity_base");
			BigDecimal cost = rs.getBigDecimal("average_cost_per_base");
			return new StockBalanceDto(
					rs.getLong("product_id"), rs.getLong("category_id"),
					rs.getString("product_code"), rs.getString("name"),
					rs.getString("category_name"), rs.getString("base_unit_name"), quantity, cost,
					quantity.multiply(cost).setScale(InventoryMath.MONEY_SCALE, InventoryMath.ROUNDING),
					rs.getBigDecimal("purchased_kg"), rs.getBigDecimal("delivered_kg"));
		}, args.toArray());
	}

	@Transactional
	public InventoryCountResult applyPhysicalCount(List<InventoryCountItemRequest> items) {
		if (!shops.isDistribution()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ Phân phối dùng kiểm kê hao hụt tồn kho", null);
		}
		Set<String> seen = new java.util.HashSet<>();
		List<String> codes = new ArrayList<>();
		BigDecimal shortage = BigDecimal.ZERO;
		BigDecimal surplus = BigDecimal.ZERO;
		for (InventoryCountItemRequest item : items) {
			String key = item.productId() + ":" + item.categoryId();
			if (!seen.add(key)) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Dòng kiểm kê bị trùng", null);
			Integer valid = jdbc.queryForObject("""
					SELECT COUNT(*) FROM products p JOIN product_categories c ON c.id = ?
					WHERE p.id = ? AND p.shop_id = ? AND c.shop_id = p.shop_id
					""", Integer.class, item.categoryId(), item.productId(), shops.currentId());
			if (valid == null || valid == 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Sản phẩm hoặc loại nhôm không thuộc cửa hàng hiện tại", null);
			Balance balance = lockBalance(item.productId(), item.categoryId());
			BigDecimal counted = item.countedQuantityKg().setScale(InventoryMath.QTY_SCALE, InventoryMath.ROUNDING);
			BigDecimal delta = counted.subtract(balance.quantity()).setScale(InventoryMath.QTY_SCALE, InventoryMath.ROUNDING);
			if (delta.signum() == 0) continue;
			Long unitIdValue = jdbc.queryForObject("""
					SELECT id FROM product_units WHERE product_id = ? AND unit_name = 'tấn' AND is_active = 1
					""", Long.class, item.productId());
			long unitId = unitIdValue;
			BigDecimal quantityTon = delta.abs().divide(BigDecimal.valueOf(1000), 6, InventoryMath.ROUNDING);
			String type = delta.signum() < 0 ? "ADJUSTMENT_OUT" : "ADJUSTMENT_IN";
			DocumentRequest request = new DocumentRequest(type, LocalDateTime.now(clock), null, null,
					null, null, "Kiểm kê tồn kho Phân phối", BigDecimal.ZERO, null, null, null,
					List.of(new ItemRequest(item.productId(), item.categoryId(), unitId, quantityTon,
							BigDecimal.ZERO, BigDecimal.ZERO, "kg", null, null, null, null)), false);
			DocumentDto draft = createDraft(request);
			DocumentDto confirmed = confirm(draft.id());
			codes.add(confirmed.documentCode());
			if (delta.signum() < 0) shortage = shortage.add(delta.abs()); else surplus = surplus.add(delta);
		}
		if (!codes.isEmpty()) audit.record(CurrentAdmin.get().id(), "DISTRIBUTION_INVENTORY_COUNT", "stock_balances",
				String.valueOf(shops.currentId()), "Điều chỉnh " + codes.size() + " dòng sau kiểm kê");
		return new InventoryCountResult(codes.size(), shortage, surplus, codes);
	}

	@Transactional(readOnly = true)
	public List<MovementDto> movements(long productId) {
		Integer owned = jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE id = ? AND shop_id = ?",
				Integer.class, productId, shops.currentId());
		if (owned == null || owned == 0) throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy sản phẩm", null);
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
			if (item.categoryId() == null) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chọn loại nhôm cho từng dòng", null);
			}
			ProductInfo product = jdbc.query("""
					SELECT id, product_code, name, is_active, shop_id, sell_mode, kg_per_m2 FROM products WHERE id = ?
					""", (rs, i) -> new ProductInfo(
					rs.getLong("id"), rs.getString("product_code"), rs.getString("name"), rs.getBoolean("is_active"),
					rs.getLong("shop_id"), CatalogService.normalizeSellMode(rs.getString("sell_mode")),
					rs.getBigDecimal("kg_per_m2")),
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
			CategoryInfo category = jdbc.query("""
					SELECT id, name, is_active, shop_id FROM product_categories WHERE id = ?
					""", (rs, i) -> new CategoryInfo(
					rs.getLong("id"), rs.getString("name"), rs.getBoolean("is_active"), rs.getLong("shop_id")),
					item.categoryId()).stream().findFirst()
					.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Loại nhôm không tồn tại", null));
			if (category.shopId() != shops.currentId()) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR,
						"Loại nhôm " + category.name() + " không thuộc cửa hàng đang chọn", null);
			}
			if (!category.active()) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR,
						"Loại nhôm " + category.name() + " đã ngừng dùng", null);
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
			String billing = normalizeBillingUnit(item.billingUnit(), unit.name(), request.documentType());
			if ("SALE".equals(request.documentType()) && "m2".equals(billing)) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Đã ngừng bán theo m²; chỉ bán theo cân", null);
			}
			BigDecimal width = null;
			BigDecimal height = null;
			BigDecimal quantity = item.quantityDocument();
			BigDecimal factor = unit.factor();
			BigDecimal quantityBase;
			String unitNameSnapshot = unit.name();
			if ("SALE".equals(request.documentType()) && "m2".equals(billing)) {
				if (internalSale) {
					throw new BusinessException(ErrorCode.VALIDATION_ERROR,
							"Xuất nội bộ chỉ theo khối lượng (tấn), không bán theo m²", null);
				}
				unit = requireKgUnit(product.id(), product.code());
				width = item.widthM();
				height = item.heightM();
				BigDecimal stockKg = item.stockQuantityKg();
				if (width == null || height == null) {
					throw new BusinessException(ErrorCode.VALIDATION_ERROR,
							"Bán theo m² cần nhập chiều dài và chiều rộng (mét)", null);
				}
				if (stockKg == null || stockKg.signum() <= 0) {
					throw new BusinessException(ErrorCode.VALIDATION_ERROR,
							"Bán theo m² cần nhập khối lượng (kg) để trừ tồn kho", null);
				}
				quantity = width.multiply(height).setScale(InventoryMath.QTY_SCALE, InventoryMath.ROUNDING);
				if (quantity.signum() <= 0) {
					throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Diện tích phải lớn hơn 0", null);
				}
				stockKg = stockKg.setScale(InventoryMath.QTY_SCALE, InventoryMath.ROUNDING);
				factor = stockKg.divide(quantity, InventoryMath.COST_SCALE, InventoryMath.ROUNDING);
				quantityBase = stockKg;
				unitNameSnapshot = CatalogService.UNIT_AREA;
			} else if ("m2".equals(billing)) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR,
						"Chỉ phiếu bán được tính tiền theo m²", null);
			} else {
				requireUnitForShop(request.documentType(), unit.name());
				quantityBase = InventoryMath.toBaseQuantity(quantity, factor);
			}
			BigDecimal discount = BigDecimal.ZERO;
			BigDecimal unitPrice = item.unitPrice();
			if (internalSale) {
				requireMatchingManufacturingProduct(product.code(), category.name());
			}
			BigDecimal referenceCost = null;
			Long sourceItemId = null;
			if (!shops.isDistribution() && "SALE".equals(request.documentType())) {
				var prices = jdbc.query("""
						SELECT i.id, i.line_total / i.quantity_base AS cost_per_kg
						FROM stock_document_items i JOIN stock_documents d ON d.id = i.document_id
						WHERE i.category_id = ? AND d.shop_id = ?
						  AND d.document_type = 'PURCHASE' AND d.status = 'CONFIRMED'
						  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
						  AND d.document_date <= ?
						ORDER BY d.document_date DESC, i.id DESC LIMIT 1
						""", (rs, row) -> new FactoryReference(rs.getLong("id"), rs.getBigDecimal("cost_per_kg")), category.id(),
						shops.currentId(), Timestamp.valueOf(request.documentDate()));
				if (!prices.isEmpty()) {
					sourceItemId = prices.get(0).itemId();
					referenceCost = prices.get(0).costPerKg().setScale(6, InventoryMath.ROUNDING);
				}
			}
			lines.add(new Line(
					product, category, unit, billing, width, height, unitNameSnapshot, quantity, factor, quantityBase,
					unitPrice, discount, sourceItemId, referenceCost,
					InventoryMath.lineTotal(quantity, unitPrice, discount)));
		}
		return lines;
	}

	private record FactoryReference(long itemId, BigDecimal costPerKg) {}

	private UnitInfo requireKgUnit(long productId, String productCode) {
		return jdbc.query("""
				SELECT id, unit_name, factor_to_base, is_active FROM product_units
				WHERE product_id = ? AND is_base_unit = 1 AND is_active = 1
				""", (rs, i) -> new UnitInfo(
				rs.getLong("id"), rs.getString("unit_name"), rs.getBigDecimal("factor_to_base"),
				rs.getBoolean("is_active")), productId).stream().findFirst()
				.filter(u -> "kg".equalsIgnoreCase(u.name()))
				.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
						"Sản phẩm " + productCode + " thiếu đơn vị kg để trừ tồn", null));
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
					INSERT INTO stock_document_items (document_id, product_id, category_id, unit_id,
					    product_code_snapshot, product_name_snapshot, category_name_snapshot,
					    unit_name_snapshot, billing_unit, width_m, height_m,
					    quantity_document, factor_to_base_snapshot,
				    quantity_base, unit_price, line_discount, line_total,
				    factory_purchase_item_id, factory_reference_cost_per_kg)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
					""", documentId, line.product().id(), line.category().id(), line.unit().id(),
					line.product().code(), line.product().name(), line.category().name(),
					line.unitNameSnapshot(), line.billingUnit(), line.widthM(), line.heightM(),
					line.quantity(), line.factor(),
					line.quantityBase(), line.unitPrice(), line.discount(), line.lineTotal(),
					line.factoryPurchaseItemId(), line.factoryReferenceCostPerKg());
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

	private void ensureBalance(long productId, long categoryId) {
		Integer count = jdbc.queryForObject(
				"SELECT COUNT(*) FROM stock_balances WHERE product_id = ? AND category_id = ?",
				Integer.class, productId, categoryId);
		if (count == null || count == 0) {
			jdbc.update("""
					INSERT INTO stock_balances (product_id, category_id, quantity_base, average_cost_per_base)
					VALUES (?, ?, 0, 0)
					""", productId, categoryId);
		}
	}

	private Balance lockBalance(long productId, long categoryId) {
		ensureBalance(productId, categoryId);
		return jdbc.query("""
				SELECT quantity_base, average_cost_per_base FROM stock_balances
				WHERE product_id = ? AND category_id = ? FOR UPDATE
				""", (rs, i) -> new Balance(rs.getBigDecimal("quantity_base"), rs.getBigDecimal("average_cost_per_base")),
				productId, categoryId).stream().findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "Thiếu số dư tồn kho", null));
	}

	private Balance readBalance(long productId, long categoryId) {
		ensureBalance(productId, categoryId);
		return jdbc.query("""
				SELECT quantity_base, average_cost_per_base FROM stock_balances
				WHERE product_id = ? AND category_id = ?
				""", (rs, i) -> new Balance(rs.getBigDecimal("quantity_base"), rs.getBigDecimal("average_cost_per_base")),
				productId, categoryId).stream().findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "Thiếu số dư tồn kho", null));
	}

	private List<StoredItem> storedItems(long documentId) {
		return new ArrayList<>(jdbc.query("""
				SELECT id, product_id, category_id, quantity_base, line_total
				FROM stock_document_items WHERE document_id = ?
				""", (rs, i) -> new StoredItem(
				rs.getLong("id"), rs.getLong("product_id"), rs.getLong("category_id"),
				rs.getBigDecimal("quantity_base"), rs.getBigDecimal("line_total")), documentId));
	}

	private List<ItemDto> items(long documentId) {
		return jdbc.query("""
				SELECT * FROM stock_document_items WHERE document_id = ? ORDER BY id
				""", (rs, i) -> new ItemDto(
				rs.getLong("id"), rs.getLong("product_id"), rs.getLong("category_id"),
				(Long) rs.getObject("unit_id"),
				rs.getString("product_code_snapshot"), rs.getString("product_name_snapshot"),
				rs.getString("category_name_snapshot"),
				rs.getString("unit_name_snapshot"),
				rs.getString("billing_unit"),
				rs.getBigDecimal("width_m"),
				rs.getBigDecimal("height_m"),
				rs.getBigDecimal("quantity_document"),
				rs.getBigDecimal("factor_to_base_snapshot"), rs.getBigDecimal("quantity_base"),
				rs.getBigDecimal("unit_price"), rs.getBigDecimal("line_discount"), rs.getBigDecimal("line_total"),
				rs.getBigDecimal("cost_per_base_snapshot"), rs.getBigDecimal("cost_total_snapshot"),
				(Long) rs.getObject("purchase_price_id"),
				rs.getBigDecimal("standard_cost_per_base_snapshot"),
				rs.getBigDecimal("standard_cost_total_snapshot"),
				(Long) rs.getObject("factory_purchase_item_id"),
				rs.getBigDecimal("factory_reference_cost_per_kg")), documentId);
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
					SELECT id, payment_date, amount, payment_method, note, collected_by_name FROM sales_payments
					WHERE document_id = ? ORDER BY payment_date, id
					""", (rs, i) -> new PaymentDto(
					rs.getLong("id"), rs.getTimestamp("payment_date").toLocalDateTime(), rs.getBigDecimal("amount"),
					localizePaymentMethod(rs.getString("payment_method")), rs.getString("note"),
					rs.getString("collected_by_name")), documentId);
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
						extractPaymentNoteFromDescription(description),
						null);
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

	private Long resolveCustomerId(DocumentRequest request) {
		if (!"SALE".equals(request.documentType()) || request.customerId() == null) {
			return null;
		}
		Integer ok = jdbc.queryForObject(
				"SELECT COUNT(*) FROM customers WHERE id = ? AND shop_id = ? AND is_active = 1",
				Integer.class, request.customerId(), shops.currentId());
		if (ok == null || ok == 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy khách hàng", null);
		}
		return request.customerId();
	}

	private String resolveCounterpartyName(DocumentRequest request, Long customerId) {
		if (customerId != null) {
			String name = jdbc.queryForObject("SELECT name FROM customers WHERE id = ?", String.class, customerId);
			if (name != null && !name.isBlank()) {
				return name.trim();
			}
		}
		return blank(request.counterpartyName());
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private void assertFactoryYearOpen(LocalDateTime date) {
		Integer closed = jdbc.queryForObject("""
				SELECT COUNT(*) FROM factory_year_counts
				WHERE status = 'CLOSED' AND period_from <= ? AND period_to >= ?
				""", Integer.class, date.toLocalDate(), date.toLocalDate());
		if (closed != null && closed > 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Ngày phiếu thuộc kỳ đã chốt kiểm kê xưởng; không thể sửa phiếu", null);
		}
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

	private void requireMatchingManufacturingProduct(String productCode, String categoryName) {
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
		Integer categoryCount = jdbc.queryForObject("""
				SELECT COUNT(*) FROM product_categories
				WHERE shop_id = ? AND name = ? AND is_active = 1
				""", Integer.class, manufacturingShopId, categoryName);
		if (categoryCount == null || categoryCount == 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Xưởng chưa có loại nhôm cùng tên \"" + categoryName
							+ "\". Hãy tạo loại cùng tên ở xưởng trước khi chuyển nội bộ",
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
	 * cùng mã SP, cùng số tấn, theo đơn giá trên phiếu bán.
	 */
	private void createPairedManufacturingPurchase(long saleId, DocRow sale) {
		long manufacturingShopId = shops.idOf(ShopContext.MANUFACTURING);
		List<MapRow> saleLines = jdbc.query("""
				SELECT product_code_snapshot, category_name_snapshot, quantity_document, unit_price, line_total,
				       quantity_base, factor_to_base_snapshot
				FROM stock_document_items WHERE document_id = ? ORDER BY id
				""", (rs, i) -> new MapRow(
				rs.getString("product_code_snapshot"),
				rs.getString("category_name_snapshot"),
				rs.getBigDecimal("quantity_document"),
				rs.getBigDecimal("unit_price"),
				rs.getBigDecimal("line_total"),
				rs.getBigDecimal("quantity_base"),
				rs.getBigDecimal("factor_to_base_snapshot")), saleId);

		BigDecimal subtotal = BigDecimal.ZERO.setScale(InventoryMath.MONEY_SCALE, InventoryMath.ROUNDING);
		List<PairLine> pairLines = new ArrayList<>();
		for (MapRow line : saleLines) {
			requireMatchingManufacturingProduct(line.code(), line.categoryName());
			ProductInfo mfgProduct = jdbc.query("""
					SELECT id, product_code, name, is_active, shop_id, sell_mode, kg_per_m2 FROM products
					WHERE shop_id = ? AND product_code = ? AND is_active = 1
					""", (rs, i) -> new ProductInfo(
					rs.getLong("id"), rs.getString("product_code"), rs.getString("name"),
					rs.getBoolean("is_active"), rs.getLong("shop_id"),
					CatalogService.normalizeSellMode(rs.getString("sell_mode")),
					rs.getBigDecimal("kg_per_m2")),
					manufacturingShopId, line.code()).stream().findFirst()
					.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
							"Không tìm thấy sản phẩm " + line.code() + " bên xưởng", null));
			CategoryInfo mfgCategory = jdbc.query("""
					SELECT id, name, is_active, shop_id FROM product_categories
					WHERE shop_id = ? AND name = ? AND is_active = 1
					""", (rs, i) -> new CategoryInfo(
					rs.getLong("id"), rs.getString("name"), rs.getBoolean("is_active"), rs.getLong("shop_id")),
					manufacturingShopId, line.categoryName()).stream().findFirst()
					.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR,
							"Không tìm thấy loại nhôm \"" + line.categoryName() + "\" bên xưởng", null));
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
			pairLines.add(new PairLine(
					mfgProduct, mfgCategory, ton, line.quantity(), line.unitPrice(), line.lineTotal(), qtyBase));
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
				null,
				subtotal,
				subtotal,
				null,
				manufacturingShopId,
				Timestamp.valueOf(now),
				saleId);

		jdbc.update("UPDATE stock_documents SET internal_pair_document_id = ? WHERE id = ?", purchaseId, saleId);

		LocalDate documentDay = sale.date().toLocalDate();
		for (PairLine line : pairLines) {
			long itemId = insertReturningId("""
					INSERT INTO stock_document_items (document_id, product_id, category_id, unit_id,
					    product_code_snapshot, product_name_snapshot, category_name_snapshot,
					    unit_name_snapshot, quantity_document, factor_to_base_snapshot,
					    quantity_base, unit_price, line_discount, line_total)
					VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
					""", purchaseId, line.product().id(), line.category().id(), line.unit().id(),
					line.product().code(), line.product().name(), line.category().name(),
					line.unit().name(), line.quantity(), line.unit().factor(),
					line.quantityBase(), line.unitPrice(), line.lineTotal());

			Balance balance = lockBalance(line.product().id(), line.category().id());
			BigDecimal costPerBase = line.lineTotal().divide(
					line.quantityBase(), InventoryMath.COST_SCALE, InventoryMath.ROUNDING);
			BigDecimal newCost = InventoryMath.weightedAverageCost(
					balance.quantity(), balance.cost(), line.quantityBase(), costPerBase);
			BigDecimal newQuantity = InventoryMath.applyDelta(balance.quantity(), line.quantityBase());
			ResolvedPrice standard = purchasePrices.resolve(line.category().id(), documentDay);
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
			jdbc.update("""
					UPDATE stock_balances SET quantity_base = ?, average_cost_per_base = ?
					WHERE product_id = ? AND category_id = ?
					""", newQuantity, newCost, line.product().id(), line.category().id());
			jdbc.update("""
					INSERT INTO stock_movements (document_id, document_item_id, product_id, category_id,
					    movement_type, quantity_delta_base, unit_cost, movement_at)
					VALUES (?, ?, ?, ?, 'IN', ?, ?, ?)
					""", purchaseId, itemId, line.product().id(), line.category().id(),
					line.quantityBase(), costPerBase, Timestamp.valueOf(sale.date()));
		}
		audit.record(CurrentAdmin.get().id(), "CONFIRM_PURCHASE", "stock_documents",
				String.valueOf(purchaseId),
				"Tự tạo phiếu nhập nội bộ xưởng " + purchaseCode + " từ " + sale.code());
	}

	private record MapRow(
			String code, String categoryName, BigDecimal quantity, BigDecimal unitPrice, BigDecimal lineTotal,
			BigDecimal quantityBase, BigDecimal factor) {
	}

	private record PairLine(
			ProductInfo product, CategoryInfo category, UnitInfo unit, BigDecimal quantity, BigDecimal unitPrice,
			BigDecimal lineTotal, BigDecimal quantityBase) {
	}

	private void requireUnitForShop(String documentType, String unitName) {
		String unit = unitName == null ? "" : unitName.trim();
		if (isAreaUnit(unit)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Đã ngừng dùng đơn vị m² trên phiếu nhập/bán mới", null);
		}
		if (shops.isDistribution()) {
			if (!"tấn".equals(unit)) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phân phối chỉ nhập và bán theo tấn", null);
			}
			return;
		}
		if ("SALE".equals(documentType)) {
			if (!"kg".equalsIgnoreCase(unit) && !"tạ".equals(unit) && !"tấn".equals(unit)) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Xưởng chỉ bán theo kg, tạ hoặc tấn", null);
			}
			return;
		}
		if ("PURCHASE".equals(documentType) && !"tấn".equals(unit) && !"tạ".equals(unit)) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Xưởng chỉ nhập theo tạ hoặc tấn", null);
		}
	}

	private static boolean isAreaUnit(String unitName) {
		if (unitName == null) {
			return false;
		}
		String u = unitName.trim();
		return CatalogService.UNIT_AREA.equals(u) || "m2".equalsIgnoreCase(u);
	}

	private static String normalizeBillingUnit(String billingUnit, String unitName, String documentType) {
		if (billingUnit != null && !billingUnit.isBlank()) {
			String b = billingUnit.trim().toLowerCase();
			if ("m2".equals(b) || "m²".equals(b)) {
				return "m2";
			}
			if ("kg".equals(b) || "tấn".equals(b) || "tạ".equals(b)) {
				return "kg";
			}
			throw new BusinessException(ErrorCode.VALIDATION_ERROR,
					"Đơn vị tính tiền chỉ nhận kg hoặc m2", null);
		}
		if ("SALE".equals(documentType) && isAreaUnit(unitName)) {
			return "m2";
		}
		return "kg";
	}

	private record Balance(BigDecimal quantity, BigDecimal cost) {
	}


	private record StoredItem(
			long id, long productId, long categoryId, BigDecimal quantityBase, BigDecimal lineTotal) {
	}

	private record ProductInfo(
			long id, String code, String name, boolean active, long shopId, String sellMode, BigDecimal kgPerM2) {
	}

	private record CategoryInfo(long id, String name, boolean active, long shopId) {
	}

	private record UnitInfo(long id, String name, BigDecimal factor, boolean active) {
	}

	private record Line(
			ProductInfo product, CategoryInfo category, UnitInfo unit, String billingUnit,
			BigDecimal widthM, BigDecimal heightM,
			String unitNameSnapshot, BigDecimal quantity, BigDecimal factor, BigDecimal quantityBase,
			BigDecimal unitPrice, BigDecimal discount, Long factoryPurchaseItemId,
			BigDecimal factoryReferenceCostPerKg, BigDecimal lineTotal) {
	}

	private record Totals(BigDecimal subtotal, BigDecimal discount, BigDecimal total) {
	}
}
