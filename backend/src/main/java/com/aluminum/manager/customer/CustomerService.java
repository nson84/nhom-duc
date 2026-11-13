package com.aluminum.manager.customer;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.common.NameValidation;
import com.aluminum.manager.customer.CustomerDtos.CustomerDocumentRow;
import com.aluminum.manager.customer.CustomerDtos.CustomerDto;
import com.aluminum.manager.customer.CustomerDtos.CustomerRequest;
import com.aluminum.manager.customer.CustomerDtos.CustomerSummary;
import com.aluminum.manager.customer.CustomerDtos.ReminderDto;
import com.aluminum.manager.customer.CustomerDtos.ReminderRequest;
import com.aluminum.manager.security.CurrentAdmin;
import com.aluminum.manager.shop.ShopAccess;
import com.aluminum.manager.shop.ShopScope;
import com.aluminum.manager.system.AuditService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomerService {

	private static final int MONEY_SCALE = 0;
	private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

	private final JdbcTemplate jdbc;
	private final AuditService audit;
	private final ShopAccess shops;

	public CustomerService(JdbcTemplate jdbc, AuditService audit, ShopAccess shops) {
		this.jdbc = jdbc;
		this.audit = audit;
		this.shops = shops;
	}

	@Transactional(readOnly = true)
	public List<CustomerDto> list(String q, Boolean activeOnly, LocalDate from, LocalDate to) {
		StringBuilder sql = new StringBuilder("""
				SELECT c.id, c.name, c.phone, c.note, c.is_active, s.code AS shop_code, s.name AS shop_name
				FROM customers c
				JOIN shops s ON s.id = c.shop_id
				WHERE 1 = 1
				""");
		List<Object> args = new ArrayList<>();
		appendShopFilter(sql, args, "c");
		if (activeOnly == null || activeOnly) {
			sql.append(" AND c.is_active = 1");
		}
		if (q != null && !q.isBlank()) {
			sql.append(" AND (c.name LIKE ? OR IFNULL(c.phone, '') LIKE ?)");
			String like = "%" + q.trim() + "%";
			args.add(like);
			args.add(like);
		}
		if (from != null || to != null) {
			sql.append("""
					 AND EXISTS (
					    SELECT 1 FROM stock_documents d
					    WHERE d.customer_id = c.id
					      AND d.document_type = 'SALE'
					      AND d.status = 'CONFIRMED'
					      AND d.trashed_at IS NULL
					      AND d.reversal_of_document_id IS NULL
					      AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
					""");
			if (from != null) {
				sql.append(" AND d.document_date >= ?");
				args.add(java.sql.Timestamp.valueOf(from.atStartOfDay()));
			}
			if (to != null) {
				sql.append(" AND d.document_date < ?");
				args.add(java.sql.Timestamp.valueOf(to.plusDays(1).atStartOfDay()));
			}
			sql.append(")");
		}
		sql.append(" ORDER BY c.name LIMIT 500");
		return jdbc.query(sql.toString(), (rs, i) -> {
			long id = rs.getLong("id");
			DebtTotals debt = debtTotals(id, from, to);
			return new CustomerDto(
					id,
					rs.getString("name"),
					rs.getString("phone"),
					rs.getString("note"),
					rs.getBoolean("is_active"),
					rs.getString("shop_code"),
					rs.getString("shop_name"),
					debt.totalSales(),
					debt.outstanding(),
					debt.openCount(), debt.receivedAmount(), debt.deliveredKg());
		}, args.toArray());
	}

	@Transactional(readOnly = true)
	public CustomerSummary get(long id) {
		CustomerRow row = requireCustomer(id, true);
		DebtTotals debt = debtTotals(id);
		Integer saleCount = jdbc.queryForObject("""
				SELECT COUNT(*) FROM stock_documents
				WHERE customer_id = ? AND document_type = 'SALE' AND status = 'CONFIRMED'
				""", Integer.class, id);
		return new CustomerSummary(
				row.id(), row.name(), row.phone(), row.note(), row.active(),
				row.shopCode(), row.shopName(),
				debt.totalSales(), debt.outstanding(),
				saleCount == null ? 0 : saleCount,
				debt.openCount(), debt.receivedAmount(), debt.deliveredKg());
	}

	@Transactional
	public CustomerDto create(CustomerRequest request) {
		NameValidation.requireNoLeadingDigit("Tên khách hàng", request.name());
		String name = request.name().trim();
		String phone = blank(request.phone());
		String note = blank(request.note());
		boolean active = request.active() == null || request.active();
		long id;
		try {
			id = insert("""
					INSERT INTO customers (shop_id, name, phone, note, is_active)
					VALUES (?, ?, ?, ?, ?)
					""", shops.currentId(), name, phone, note, active);
		} catch (DuplicateKeyException ex) {
			throw new BusinessException(ErrorCode.DUPLICATE_PRODUCT_CODE, "Tên khách đã có trong cửa hàng này", null);
		}
		audit.record(CurrentAdmin.get().id(), "CREATE_CUSTOMER", "customers", String.valueOf(id), name);
		return toDto(requireCustomer(id, false));
	}

	@Transactional
	public CustomerDto update(long id, CustomerRequest request) {
		CustomerRow existing = requireCustomer(id, false);
		if (existing.shopId() != shops.currentId()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không sửa khách cửa hàng khác", null);
		}
		NameValidation.requireNoLeadingDigit("Tên khách hàng", request.name());
		String name = request.name().trim();
		try {
			jdbc.update("""
					UPDATE customers SET name = ?, phone = ?, note = ?, is_active = ?
					WHERE id = ?
					""", name, blank(request.phone()), blank(request.note()),
					request.active() == null || request.active(), id);
		} catch (DuplicateKeyException ex) {
			throw new BusinessException(ErrorCode.DUPLICATE_PRODUCT_CODE, "Tên khách đã có trong cửa hàng này", null);
		}
		audit.record(CurrentAdmin.get().id(), "UPDATE_CUSTOMER", "customers", String.valueOf(id), name);
		return toDto(requireCustomer(id, false));
	}

	@Transactional(readOnly = true)
	public List<CustomerDocumentRow> documents(long customerId, String paymentStatus) {
		requireCustomer(customerId, true);
		StringBuilder sql = new StringBuilder("""
				SELECT d.id, d.document_code, d.document_date, d.status, d.total_amount, d.payment_status,
				       COALESCE((SELECT SUM(p.amount) FROM sales_payments p WHERE p.document_id = d.id), 0) AS paid_amount
				FROM stock_documents d
				WHERE d.customer_id = ? AND d.document_type = 'SALE'
				""");
		List<Object> args = new ArrayList<>();
		args.add(customerId);
		if (paymentStatus != null && !paymentStatus.isBlank()) {
			if ("OPEN".equalsIgnoreCase(paymentStatus)) {
				sql.append(" AND d.payment_status IN ('UNPAID', 'PARTIAL') AND d.status = 'CONFIRMED'");
			} else {
				sql.append(" AND d.payment_status = ?");
				args.add(paymentStatus);
			}
		}
		sql.append(" ORDER BY d.document_date DESC, d.id DESC");
		return jdbc.query(sql.toString(), (rs, i) -> {
			BigDecimal total = scale(rs.getBigDecimal("total_amount"));
			BigDecimal paid = scale(rs.getBigDecimal("paid_amount"));
			BigDecimal remaining = total.subtract(paid).max(BigDecimal.ZERO);
			return new CustomerDocumentRow(
					rs.getLong("id"),
					rs.getString("document_code"),
					rs.getTimestamp("document_date").toLocalDateTime(),
					rs.getString("status"),
					total,
					paid,
					remaining,
					rs.getString("payment_status"));
		}, args.toArray());
	}

	@Transactional(readOnly = true)
	public List<ReminderDto> reminders(long customerId) {
		requireCustomer(customerId, true);
		return jdbc.query("""
				SELECT r.id, r.customer_id, c.name AS customer_name, r.document_id, d.document_code,
				       r.remind_on, r.note, r.is_done, s.code AS shop_code, s.name AS shop_name
				FROM customer_payment_reminders r
				JOIN customers c ON c.id = r.customer_id
				JOIN shops s ON s.id = c.shop_id
				LEFT JOIN stock_documents d ON d.id = r.document_id
				WHERE r.customer_id = ?
				ORDER BY r.remind_on ASC, r.id ASC
				""", (rs, i) -> mapReminder(rs), customerId);
	}

	@Transactional(readOnly = true)
	public List<ReminderDto> dueReminders() {
		StringBuilder sql = new StringBuilder("""
				SELECT r.id, r.customer_id, c.name AS customer_name, r.document_id, d.document_code,
				       r.remind_on, r.note, r.is_done, s.code AS shop_code, s.name AS shop_name
				FROM customer_payment_reminders r
				JOIN customers c ON c.id = r.customer_id
				JOIN shops s ON s.id = c.shop_id
				LEFT JOIN stock_documents d ON d.id = r.document_id
				WHERE r.is_done = 0 AND r.remind_on <= ?
				""");
		List<Object> args = new ArrayList<>();
		args.add(Date.valueOf(LocalDate.now()));
		appendShopFilter(sql, args, "c");
		sql.append(" ORDER BY r.remind_on ASC, r.id ASC LIMIT 200");
		return jdbc.query(sql.toString(), (rs, i) -> mapReminder(rs), args.toArray());
	}

	@Transactional
	public ReminderDto createReminder(long customerId, ReminderRequest request) {
		CustomerRow customer = requireCustomer(customerId, false);
		if (customer.shopId() != shops.currentId()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tạo nhắc cho khách cửa hàng khác", null);
		}
		Long documentId = request.documentId();
		if (documentId != null) {
			Integer ok = jdbc.queryForObject("""
					SELECT COUNT(*) FROM stock_documents
					WHERE id = ? AND customer_id = ? AND document_type = 'SALE'
					""", Integer.class, documentId, customerId);
			if (ok == null || ok == 0) {
				throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Phiếu không thuộc khách này", null);
			}
		}
		long id = insert("""
				INSERT INTO customer_payment_reminders (customer_id, document_id, remind_on, note, is_done)
				VALUES (?, ?, ?, ?, 0)
				""", customerId, documentId, Date.valueOf(request.remindOn()), blank(request.note()));
		audit.record(CurrentAdmin.get().id(), "CREATE_REMINDER", "customer_payment_reminders",
				String.valueOf(id), "Nhắc " + customer.name() + " ngày " + request.remindOn());
		return reminders(customerId).stream().filter(r -> r.id().equals(id)).findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR, "Không tải được nhắc", null));
	}

	@Transactional
	public ReminderDto markReminderDone(long customerId, long reminderId, boolean done) {
		requireCustomer(customerId, false);
		int updated = jdbc.update("""
				UPDATE customer_payment_reminders SET is_done = ?
				WHERE id = ? AND customer_id = ?
				""", done, reminderId, customerId);
		if (updated == 0) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy lịch nhắc", null);
		}
		audit.record(CurrentAdmin.get().id(), done ? "DONE_REMINDER" : "REOPEN_REMINDER",
				"customer_payment_reminders", String.valueOf(reminderId), null);
		return reminders(customerId).stream().filter(r -> r.id().equals(reminderId)).findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy lịch nhắc", null));
	}

	/** Gắn / gỡ khách trên phiếu bán (nháp hoặc đã xác nhận). */
	@Transactional
	public void linkDocument(long documentId, Long customerId) {
		DocLink doc = jdbc.query("""
				SELECT id, shop_id, document_type, counterparty_name FROM stock_documents WHERE id = ?
				""", (rs, i) -> new DocLink(
				rs.getLong("id"),
				rs.getLong("shop_id"),
				rs.getString("document_type"),
				rs.getString("counterparty_name")), documentId).stream().findFirst()
				.orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy phiếu", null));
		if (!"SALE".equals(doc.type())) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Chỉ gắn khách cho phiếu bán", null);
		}
		if (doc.shopId() != shops.currentId()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không sửa phiếu cửa hàng khác", null);
		}
		if (customerId == null) {
			jdbc.update("UPDATE stock_documents SET customer_id = NULL WHERE id = ?", documentId);
			return;
		}
		CustomerRow customer = requireCustomer(customerId, false);
		if (customer.shopId() != doc.shopId()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Khách không thuộc cửa hàng của phiếu", null);
		}
		jdbc.update("UPDATE stock_documents SET customer_id = ?, counterparty_name = ? WHERE id = ?",
				customerId, customer.name(), documentId);
		audit.record(CurrentAdmin.get().id(), "LINK_CUSTOMER", "stock_documents",
				String.valueOf(documentId), "Khách #" + customerId);
	}

	public Long findIdByNameInCurrentShop(String name) {
		if (name == null || name.isBlank()) {
			return null;
		}
		return jdbc.query("""
				SELECT id FROM customers WHERE shop_id = ? AND name = ? LIMIT 1
				""", (rs, i) -> rs.getLong("id"), shops.currentId(), name.trim()).stream().findFirst().orElse(null);
	}

	public void requireOwnedByCurrentShop(long customerId) {
		CustomerRow row = requireCustomer(customerId, false);
		if (row.shopId() != shops.currentId()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Khách không thuộc cửa hàng hiện tại", null);
		}
	}

	private CustomerDto toDto(CustomerRow row) {
		DebtTotals debt = debtTotals(row.id());
		return new CustomerDto(
				row.id(), row.name(), row.phone(), row.note(), row.active(),
				row.shopCode(), row.shopName(),
				debt.totalSales(), debt.outstanding(), debt.openCount(), debt.receivedAmount(), debt.deliveredKg());
	}

	/**
	 * @param forRead khi true và Admin {@code scope=all}: được đọc khách mọi shop.
	 *                Ghi luôn yêu cầu đúng shop hiện tại (trừ khi đã check riêng).
	 */
	private CustomerRow requireCustomer(long id, boolean forRead) {
		List<CustomerRow> rows = jdbc.query("""
				SELECT c.id, c.shop_id, c.name, c.phone, c.note, c.is_active, s.code AS shop_code, s.name AS shop_name
				FROM customers c
				JOIN shops s ON s.id = c.shop_id
				WHERE c.id = ?
				""", (rs, i) -> new CustomerRow(
				rs.getLong("id"),
				rs.getLong("shop_id"),
				rs.getString("name"),
				rs.getString("phone"),
				rs.getString("note"),
				rs.getBoolean("is_active"),
				rs.getString("shop_code"),
				rs.getString("shop_name")), id);
		if (rows.isEmpty()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy khách hàng", null);
		}
		CustomerRow row = rows.get(0);
		if (forRead && ShopScope.all()) {
			return row;
		}
		if (row.shopId() != shops.currentId()) {
			throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Không tìm thấy khách hàng", null);
		}
		return row;
	}

	private void appendShopFilter(StringBuilder sql, List<Object> args, String alias) {
		if (ShopScope.all()) {
			return;
		}
		sql.append(" AND ").append(alias).append(".shop_id = ?");
		args.add(shops.currentId());
	}

	private DebtTotals debtTotals(long customerId) {
		return debtTotals(customerId, null, null);
	}

	private DebtTotals debtTotals(long customerId, LocalDate from, LocalDate to) {
		var start = from == null ? null : java.sql.Timestamp.valueOf(from.atStartOfDay());
		var end = to == null ? null : java.sql.Timestamp.valueOf(to.plusDays(1).atStartOfDay());
		return jdbc.query("""
				SELECT
				  COALESCE(SUM((SELECT SUM(p.amount) FROM sales_payments p WHERE p.document_id = d.id)), 0) AS received_amount,
				  COALESCE(SUM((SELECT SUM(i.quantity_base) FROM stock_document_items i WHERE i.document_id = d.id)), 0) AS delivered_kg,
				  COALESCE(SUM(CASE WHEN d.status = 'CONFIRMED' THEN d.total_amount ELSE 0 END), 0) AS total_sales,
				  COALESCE(SUM(CASE
				      WHEN d.status = 'CONFIRMED' AND d.payment_status IN ('UNPAID', 'PARTIAL')
				      THEN d.total_amount - COALESCE((
				          SELECT SUM(p.amount) FROM sales_payments p WHERE p.document_id = d.id
				      ), 0)
				      ELSE 0 END), 0) AS outstanding,
				  COALESCE(SUM(CASE
				      WHEN d.status = 'CONFIRMED' AND d.payment_status IN ('UNPAID', 'PARTIAL') THEN 1
				      ELSE 0 END), 0) AS open_count
				FROM stock_documents d
				WHERE d.customer_id = ? AND d.document_type = 'SALE' AND d.status = 'CONFIRMED'
				  AND d.trashed_at IS NULL AND d.reversal_of_document_id IS NULL
				  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
				  AND (? IS NULL OR d.document_date >= ?)
				  AND (? IS NULL OR d.document_date < ?)
				""", (rs, i) -> new DebtTotals(
				scale(rs.getBigDecimal("total_sales")),
				scale(rs.getBigDecimal("outstanding")).max(BigDecimal.ZERO),
				rs.getInt("open_count"), scale(rs.getBigDecimal("received_amount")),
				rs.getBigDecimal("delivered_kg")), customerId, start, start, end, end).get(0);
	}

	private ReminderDto mapReminder(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new ReminderDto(
				rs.getLong("id"),
				rs.getLong("customer_id"),
				rs.getString("customer_name"),
				(Long) rs.getObject("document_id"),
				rs.getString("document_code"),
				rs.getDate("remind_on").toLocalDate(),
				rs.getString("note"),
				rs.getBoolean("is_done"),
				rs.getString("shop_code"),
				rs.getString("shop_name"));
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

	private static BigDecimal scale(BigDecimal value) {
		return (value == null ? BigDecimal.ZERO : value).setScale(MONEY_SCALE, ROUNDING);
	}

	private static String blank(String value) {
		return value == null || value.isBlank() ? null : value.trim();
	}

	private record CustomerRow(
			long id, long shopId, String name, String phone, String note, boolean active,
			String shopCode, String shopName
	) {
	}

	private record DebtTotals(BigDecimal totalSales, BigDecimal outstanding, int openCount,
			BigDecimal receivedAmount, BigDecimal deliveredKg) {
	}

	private record DocLink(long id, long shopId, String type, String counterpartyName) {
	}
}
