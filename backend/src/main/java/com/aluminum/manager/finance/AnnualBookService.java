package com.aluminum.manager.finance;

import com.aluminum.manager.auth.UserRole;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.security.CurrentAdmin;
import com.aluminum.manager.shop.ShopAccess;
import com.aluminum.manager.shop.ShopContext;
import com.aluminum.manager.dashboard.YearEndBalanceService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnnualBookService {
    private final JdbcTemplate jdbc;
    private final ShopAccess shops;
    private final YearEndBalanceService yearPeriods;

    public AnnualBookService(JdbcTemplate jdbc, ShopAccess shops, YearEndBalanceService yearPeriods) {
        this.jdbc = jdbc;
        this.shops = shops;
        this.yearPeriods = yearPeriods;
    }

    public record Customer(long id, String name, String phone, String note, BigDecimal openingDebt,
                           BigDecimal sales, BigDecimal receipts, BigDecimal closingDebt) {}
    public record Sale(long id, Long customerId, String customerName, String code, LocalDateTime date,
                       String seller, String receiver, BigDecimal total, BigDecimal paidAsOf,
                       BigDecimal openingPaid, String notes) {}
    public record SaleItem(long documentId, String product, String category, BigDecimal quantity,
                           String unit, BigDecimal unitPrice, BigDecimal amount) {}
    public record Purchase(long id, String supplier, String code, LocalDateTime date, BigDecimal total,
                           BigDecimal paidAsOf, String notes) {}
    public record Payment(long documentId, LocalDateTime date, BigDecimal amount, String method,
                          String collector, String note) {}
    public record Transaction(String code, LocalDateTime date, String category, BigDecimal amount,
                              String counterparty, String spentBy, String description, String reference) {}
    public record Stock(String product, String category, BigDecimal quantityKg, BigDecimal value) {}
    public record Wage(int year, int month, String employee, BigDecimal days, BigDecimal basePay,
                       BigDecimal overtimePay, BigDecimal bonusPay, BigDecimal allowances,
                       BigDecimal deductions, BigDecimal totalPay) {}
    public record Book(int year, LocalDate periodFrom, LocalDate periodTo, String shopCode, String shopName, List<Customer> customers,
                       List<Sale> sales, List<SaleItem> saleItems, List<Payment> payments,
                       List<Purchase> purchases, List<SaleItem> purchaseItems,
                       List<Transaction> receipts, List<Transaction> expenses, List<Stock> stock, List<Wage> wages,
                       BigDecimal inventoryValue, BigDecimal profit, boolean closed) {}

    @Transactional(readOnly = true)
    public Book book(int year, String code) {
        if (year < 2000 || year > 2100) throw invalid("Năm không hợp lệ");
        if (!ShopContext.DISTRIBUTION.equals(code) && !ShopContext.MANUFACTURING.equals(code))
            throw invalid("Cửa hàng không hợp lệ");
        var user = CurrentAdmin.get();
        if (user.role() != UserRole.ADMIN && !code.equals(user.shopCode()))
            throw new BusinessException(ErrorCode.FORBIDDEN, "Không được xem sổ của cửa hàng khác", null);
        long shopId = shops.idOf(code);
        var selectedPeriod = yearPeriods.period(year, code);
        LocalDate[] period = {selectedPeriod.periodFrom(), selectedPeriod.periodTo()};
        Timestamp start = Timestamp.valueOf(period[0].atStartOfDay());
        Timestamp end = Timestamp.valueOf(period[1].plusDays(1).atStartOfDay());

        List<Customer> customers = jdbc.query("""
                SELECT c.id, c.name, c.phone, c.note,
                  COALESCE((SELECT SUM(d.total_amount) FROM stock_documents d
                    WHERE d.customer_id = c.id AND d.document_type = 'SALE' AND d.status = 'CONFIRMED'
                      AND d.is_internal = 0 AND d.document_date < ?), 0)
                  - COALESCE((SELECT SUM(p.amount) FROM sales_payments p JOIN stock_documents d ON d.id = p.document_id
                    WHERE d.customer_id = c.id AND d.document_type = 'SALE' AND d.status = 'CONFIRMED'
                      AND d.is_internal = 0 AND d.document_date < ? AND p.payment_date < ?), 0) AS opening_debt,
                  COALESCE((SELECT SUM(d.total_amount) FROM stock_documents d
                    WHERE d.customer_id = c.id AND d.document_type = 'SALE' AND d.status = 'CONFIRMED'
                      AND d.is_internal = 0 AND d.document_date >= ? AND d.document_date < ?), 0) AS sales,
                  COALESCE((SELECT SUM(p.amount) FROM sales_payments p JOIN stock_documents d ON d.id = p.document_id
                    WHERE d.customer_id = c.id AND d.document_type = 'SALE' AND d.status = 'CONFIRMED'
                      AND d.is_internal = 0 AND d.document_date < ? AND p.payment_date >= ? AND p.payment_date < ?), 0) AS receipts
                FROM customers c WHERE c.shop_id = ? AND c.created_at < ? ORDER BY c.name
                """, (rs, i) -> {
            BigDecimal opening = rs.getBigDecimal("opening_debt");
            BigDecimal sales = rs.getBigDecimal("sales");
            BigDecimal receipts = rs.getBigDecimal("receipts");
            return new Customer(rs.getLong("id"), rs.getString("name"), rs.getString("phone"), rs.getString("note"),
                    opening, sales, receipts, opening.add(sales).subtract(receipts));
        }, start, start, start, start, end, end, start, end, shopId, end);

        List<Sale> sales = jdbc.query("""
                SELECT d.id, d.customer_id, COALESCE(c.name, d.counterparty_name, '') AS customer_name,
                  d.document_code, d.document_date, d.issued_by, d.received_by, d.total_amount, d.notes,
                  COALESCE((SELECT SUM(p.amount) FROM sales_payments p
                    WHERE p.document_id = d.id AND p.payment_date < ?), 0) AS paid_as_of,
                  COALESCE((SELECT SUM(p.amount) FROM sales_payments p
                    WHERE p.document_id = d.id AND p.payment_date < ?), 0) AS opening_paid
                FROM stock_documents d LEFT JOIN customers c ON c.id = d.customer_id
                WHERE d.shop_id = ? AND d.document_type = 'SALE' AND d.status = 'CONFIRMED'
                  AND d.is_internal = 0 AND d.document_date < ?
                  AND (d.document_date >= ? OR d.total_amount > COALESCE((SELECT SUM(p.amount)
                    FROM sales_payments p WHERE p.document_id = d.id AND p.payment_date < ?), 0)
                    OR EXISTS (SELECT 1 FROM sales_payments p WHERE p.document_id = d.id
                      AND p.payment_date >= ? AND p.payment_date < ?))
                ORDER BY COALESCE(c.name, d.counterparty_name), d.document_date, d.id
                """, (rs, i) -> new Sale(rs.getLong("id"), rs.getObject("customer_id", Long.class),
                rs.getString("customer_name"), rs.getString("document_code"),
                rs.getTimestamp("document_date").toLocalDateTime(), rs.getString("issued_by"),
                rs.getString("received_by"), rs.getBigDecimal("total_amount"), rs.getBigDecimal("paid_as_of"),
                rs.getBigDecimal("opening_paid"), rs.getString("notes")), end, start, shopId, end, start, end, start, end);

        List<SaleItem> items = jdbc.query("""
                SELECT i.document_id, i.product_name_snapshot, COALESCE(i.category_name_snapshot, ''), i.quantity_document,
                  i.unit_name_snapshot, i.unit_price, i.line_total
                FROM stock_document_items i JOIN stock_documents d ON d.id = i.document_id
                WHERE d.shop_id = ? AND d.document_type = 'SALE' AND d.status = 'CONFIRMED'
                  AND d.is_internal = 0 AND d.document_date < ? ORDER BY i.document_id, i.id
                """, (rs, i) -> new SaleItem(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getBigDecimal(4), rs.getString(5), rs.getBigDecimal(6), rs.getBigDecimal(7)), shopId, end);

        List<Payment> payments = jdbc.query("""
                SELECT p.document_id, p.payment_date, p.amount, p.payment_method, p.collected_by_name, p.note
                FROM sales_payments p JOIN stock_documents d ON d.id = p.document_id
                WHERE d.shop_id = ? AND d.document_type = 'SALE' AND d.status = 'CONFIRMED'
                  AND d.is_internal = 0 AND d.document_date < ? AND p.payment_date < ?
                ORDER BY p.document_id, p.payment_date, p.id
                """, (rs, i) -> new Payment(rs.getLong(1), rs.getTimestamp(2).toLocalDateTime(),
                rs.getBigDecimal(3), rs.getString(4), rs.getString(5), rs.getString(6)), shopId, end, end);

        List<Purchase> purchases = jdbc.query("""
                SELECT d.id, d.counterparty_name, d.document_code, d.document_date, d.total_amount, d.notes,
                  COALESCE((SELECT SUM(f.amount) FROM financial_transactions f
                    WHERE f.reference_type = 'STOCK_DOCUMENT' AND f.reference_id = d.id
                      AND f.category = 'PURCHASE_PAYMENT' AND f.status = 'CONFIRMED'
                      AND f.transaction_date < ?), 0) AS paid_as_of
                FROM stock_documents d
                WHERE d.shop_id = ? AND d.document_type = 'PURCHASE' AND d.status = 'CONFIRMED'
                  AND d.is_internal = 0 AND d.document_date < ?
                  AND (d.document_date >= ? OR d.total_amount > COALESCE((SELECT SUM(f.amount)
                    FROM financial_transactions f WHERE f.reference_type = 'STOCK_DOCUMENT'
                      AND f.reference_id = d.id AND f.category = 'PURCHASE_PAYMENT'
                      AND f.status = 'CONFIRMED' AND f.transaction_date < ?), 0)
                    OR EXISTS (SELECT 1 FROM financial_transactions f
                      WHERE f.reference_type = 'STOCK_DOCUMENT' AND f.reference_id = d.id
                        AND f.category = 'PURCHASE_PAYMENT' AND f.status = 'CONFIRMED'
                        AND f.transaction_date >= ? AND f.transaction_date < ?))
                ORDER BY d.counterparty_name, d.document_date, d.id
                """, (rs, i) -> new Purchase(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getTimestamp(4).toLocalDateTime(), rs.getBigDecimal(5), rs.getBigDecimal(7),
                rs.getString(6)), end, shopId, end, start, end, start, end);

        List<SaleItem> purchaseItems = jdbc.query("""
                SELECT i.document_id, i.product_name_snapshot, COALESCE(i.category_name_snapshot, ''),
                  i.quantity_document, i.unit_name_snapshot, i.unit_price, i.line_total
                FROM stock_document_items i JOIN stock_documents d ON d.id = i.document_id
                WHERE d.shop_id = ? AND d.document_type = 'PURCHASE' AND d.status = 'CONFIRMED'
                  AND d.is_internal = 0 AND d.document_date < ? ORDER BY i.document_id, i.id
                """, (rs, i) -> new SaleItem(rs.getLong(1), rs.getString(2), rs.getString(3),
                rs.getBigDecimal(4), rs.getString(5), rs.getBigDecimal(6), rs.getBigDecimal(7)), shopId, end);

        List<Transaction> receipts = transactions(shopId, start, end, "INCOME");
        List<Transaction> expenses = transactions(shopId, start, end, "EXPENSE");
        List<Stock> stock = ShopContext.DISTRIBUTION.equals(code) ? distributionStock(shopId, end) : factoryStock(year);
        List<Wage> wages = ShopContext.MANUFACTURING.equals(code) ? wages(shopId, period[0], period[1]) : List.of();
        var closing = jdbc.query("""
                SELECT inventory_value, profit FROM year_end_balances WHERE shop_id = ? AND year_number = ?
                """, (rs, i) -> new BigDecimal[]{rs.getBigDecimal(1), rs.getBigDecimal(2)}, shopId, year)
                .stream().findFirst().orElse(null);
        return new Book(year, period[0], period[1], code, shops.shopName(code), customers, sales, items, payments, purchases, purchaseItems,
                receipts, expenses, stock, wages, closing == null ? null : closing[0],
                closing == null ? null : closing[1], closing != null);
    }

    private List<Transaction> transactions(long shopId, Timestamp start, Timestamp end, String type) {
        return jdbc.query("""
                SELECT f.transaction_code, f.transaction_date, f.category, f.amount,
                  COALESCE(c.name, d.counterparty_name, s.buyer_name, '') AS counterparty,
                  f.spent_by, f.description,
                  COALESCE(d.document_code, CASE WHEN s.id IS NOT NULL THEN CONCAT('Phế liệu #', s.id) ELSE '' END) AS reference
                FROM financial_transactions f
                LEFT JOIN stock_documents d ON f.reference_type = 'STOCK_DOCUMENT' AND f.reference_id = d.id
                LEFT JOIN customers c ON c.id = d.customer_id
                LEFT JOIN factory_scrap_sales s ON f.reference_type = 'SCRAP_SALE' AND f.reference_id = s.id
                WHERE f.shop_id = ? AND f.transaction_type = ? AND f.status = 'CONFIRMED'
                  AND f.is_internal = 0 AND f.transaction_date >= ? AND f.transaction_date < ?
                ORDER BY f.transaction_date, f.id
                """, (rs, i) -> new Transaction(rs.getString(1), rs.getTimestamp(2).toLocalDateTime(),
                rs.getString(3), rs.getBigDecimal(4), rs.getString(5), rs.getString(6),
                rs.getString(7), rs.getString(8)), shopId, type, start, end);
    }

    private List<Stock> distributionStock(long shopId, Timestamp end) {
        return jdbc.query("""
                SELECT p.name, c.name, SUM(m.quantity_delta_base) AS quantity
                FROM stock_movements m JOIN products p ON p.id = m.product_id
                JOIN product_categories c ON c.id = m.category_id
                WHERE p.shop_id = ? AND m.movement_at < ?
                GROUP BY p.id, c.id HAVING SUM(m.quantity_delta_base) <> 0
                ORDER BY p.name, c.name
                """, (rs, i) -> new Stock(rs.getString(1), rs.getString(2), rs.getBigDecimal(3), null), shopId, end);
    }

    private List<Stock> factoryStock(int year) {
        return jdbc.query("""
                SELECT 'Nguyên liệu', c.name, r.quantity_kg, r.value_amount
                FROM factory_raw_counts r JOIN product_categories c ON c.id = r.category_id
                WHERE r.year_number = ?
                UNION ALL SELECT 'Phế liệu chưa bán', '', scrap_kg, scrap_value
                  FROM factory_year_counts WHERE year_number = ? AND scrap_kg > 0
                UNION ALL SELECT 'Hàng đang làm', '', work_in_progress_kg, work_in_progress_value
                  FROM factory_year_counts WHERE year_number = ? AND work_in_progress_kg > 0
                UNION ALL SELECT 'Thành phẩm chưa bán', '', finished_kg, finished_value
                  FROM factory_year_counts WHERE year_number = ? AND finished_kg > 0
                ORDER BY 1, 2
                """, (rs, i) -> new Stock(rs.getString(1), rs.getString(2), rs.getBigDecimal(3), rs.getBigDecimal(4)),
                year, year, year, year);
    }

    private List<Wage> wages(long shopId, LocalDate from, LocalDate to) {
        return jdbc.query("""
                SELECT pp.period_year, pp.period_month, e.full_name, pd.credited_days, pd.base_pay, pd.overtime_pay,
                  pd.bonus_pay, pd.allowances, pd.deductions, pd.total_pay
                FROM payroll_details pd JOIN payroll_periods pp ON pp.id = pd.payroll_period_id
                JOIN employees e ON e.id = pd.employee_id
                WHERE e.shop_id = ?
                  AND LAST_DAY(MAKEDATE(pp.period_year, 1) + INTERVAL (pp.period_month - 1) MONTH) >= ?
                  AND LAST_DAY(MAKEDATE(pp.period_year, 1) + INTERVAL (pp.period_month - 1) MONTH) <= ?
                ORDER BY pp.period_year, pp.period_month, e.full_name
                """, (rs, i) -> new Wage(rs.getInt(1), rs.getInt(2), rs.getString(3), rs.getBigDecimal(4),
                rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7),
                rs.getBigDecimal(8), rs.getBigDecimal(9), rs.getBigDecimal(10)), shopId, from, to);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, null);
    }
}
