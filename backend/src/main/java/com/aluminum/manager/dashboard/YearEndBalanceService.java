package com.aluminum.manager.dashboard;

import com.aluminum.manager.shop.ShopAccess;
import com.aluminum.manager.shop.ShopContext;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class YearEndBalanceService {
    private final JdbcTemplate jdbc;
    private final ShopAccess shops;

    public YearEndBalanceService(JdbcTemplate jdbc, ShopAccess shops) {
        this.jdbc = jdbc;
        this.shops = shops;
    }

    public record Balance(int year, LocalDate periodFrom, LocalDate periodTo, BigDecimal inventoryKg, BigDecimal inventoryValue,
                          BigDecimal customerDebt, BigDecimal supplierDebt, BigDecimal profit,
                          LocalDateTime closedAt) {}

    public record Period(int year, LocalDate periodFrom, LocalDate periodTo, boolean closed) {}

    public Period period(int year) {
        return period(year, ShopContext.code());
    }

    public Period period(int year, String shopCode) {
        if (year < 2000 || year > 2100) throw invalid("Năm không hợp lệ");
        long shopId = shops.idOf(shopCode);
        var closed = jdbc.query("SELECT period_from, period_to FROM year_end_balances WHERE shop_id = ? AND year_number = ?",
                (rs, i) -> new Period(year, rs.getDate(1).toLocalDate(), rs.getDate(2).toLocalDate(), true), shopId, year)
                .stream().findFirst().orElse(null);
        if (closed != null) return closed;
        if (ShopContext.MANUFACTURING.equals(shopCode)) {
            var draft = jdbc.query("SELECT period_from, period_to FROM factory_year_counts WHERE year_number = ?",
                    (rs, i) -> new Period(year, rs.getDate(1).toLocalDate(), rs.getDate(2).toLocalDate(), false), year)
                    .stream().findFirst().orElse(null);
            if (draft != null) return draft;
        }
        LocalDate priorEnd = jdbc.query("""
                SELECT period_to FROM year_end_balances WHERE shop_id = ? AND year_number = ?
                """, (rs, i) -> rs.getDate(1).toLocalDate(), shopId, year - 1)
                .stream().findFirst().orElse(null);
        return upcomingPeriod(year, priorEnd);
    }

    static Period upcomingPeriod(int year, LocalDate priorEnd) {
        LocalDate from = priorEnd == null ? LocalDate.of(year, 1, 1) : priorEnd.plusDays(1);
        return new Period(year, from, from.plusYears(1).minusDays(1), false);
    }

    public Balance get(int year) {
        return jdbc.query("""
                SELECT year_number, period_from, period_to, inventory_kg, inventory_value, customer_debt, supplier_debt, profit, closed_at
                FROM year_end_balances WHERE shop_id = ? AND year_number = ?
                """, (rs, i) -> new Balance(rs.getInt(1), rs.getDate(2).toLocalDate(), rs.getDate(3).toLocalDate(),
                rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8),
                rs.getTimestamp(9).toLocalDateTime()), shops.currentId(), year)
                .stream().findFirst().orElse(null);
    }

    public void save(int year, LocalDate from, LocalDate to, BigDecimal inventoryKg, BigDecimal inventoryValue, BigDecimal profit) {
        validatePeriod(from, to, null, null);
        if (Math.abs(year - from.getYear()) > 1) throw invalid("Tên năm chốt không khớp khoảng ngày đã chọn");
        Integer overlapping = jdbc.queryForObject("""
                SELECT COUNT(*) FROM year_end_balances
                WHERE shop_id = ? AND year_number <> ? AND period_from <= ? AND period_to >= ?
                """, Integer.class, shops.currentId(), year, to, from);
        if (overlapping != null && overlapping > 0) throw invalid("Kỳ chốt năm bị trùng với năm đã chốt");
        LocalDate priorEnd = jdbc.query("""
                SELECT period_to FROM year_end_balances WHERE shop_id = ? AND year_number < ?
                ORDER BY year_number DESC LIMIT 1
                """, (rs, i) -> rs.getDate(1).toLocalDate(), shops.currentId(), year)
                .stream().findFirst().orElse(null);
        LocalDate nextStart = jdbc.query("""
                SELECT period_from FROM year_end_balances WHERE shop_id = ? AND year_number > ?
                ORDER BY year_number LIMIT 1
                """, (rs, i) -> rs.getDate(1).toLocalDate(), shops.currentId(), year)
                .stream().findFirst().orElse(null);
        validatePeriod(from, to, priorEnd, nextStart);
        Timestamp cutoff = Timestamp.valueOf(to.plusDays(1).atStartOfDay());
        BigDecimal customerDebt = debt("SALE", cutoff);
        BigDecimal supplierDebt = debt("PURCHASE", cutoff);
        jdbc.update("""
                INSERT INTO year_end_balances
                (shop_id, year_number, period_from, period_to, inventory_kg, inventory_value, customer_debt, supplier_debt, profit, closed_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(3))
                ON DUPLICATE KEY UPDATE inventory_kg = VALUES(inventory_kg),
                  inventory_value = VALUES(inventory_value), customer_debt = VALUES(customer_debt),
                  supplier_debt = VALUES(supplier_debt), profit = VALUES(profit),
                  period_from = VALUES(period_from), period_to = VALUES(period_to), closed_at = NOW(3)
                """, shops.currentId(), year, from, to, inventoryKg, inventoryValue, customerDebt, supplierDebt, profit);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, null);
    }

    static void validatePeriod(LocalDate from, LocalDate to, LocalDate priorEnd, LocalDate nextStart) {
        if (from == null || to == null || from.isAfter(to) || from.plusDays(400).isBefore(to))
            throw invalid("Khoảng ngày chốt năm phải dài tối đa 400 ngày");
        if (priorEnd != null && !from.equals(priorEnd.plusDays(1)))
            throw invalid("Kỳ mới phải bắt đầu ngay sau ngày chốt năm trước");
        if (nextStart != null && !to.plusDays(1).equals(nextStart))
            throw invalid("Ngày chốt phải sát với kỳ năm sau đã chốt");
    }

    private BigDecimal debt(String type, Timestamp cutoff) {
        String payment = "SALE".equals(type)
                ? "SELECT SUM(p.amount) FROM sales_payments p WHERE p.document_id = d.id AND p.payment_date < ?"
                : "SELECT SUM(f.amount) FROM financial_transactions f WHERE f.reference_type = 'STOCK_DOCUMENT' "
                + "AND f.reference_id = d.id AND f.category = 'PURCHASE_PAYMENT' "
                + "AND f.status = 'CONFIRMED' AND f.transaction_date < ?";
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(GREATEST(d.total_amount - COALESCE((
                """ + payment + """
                ), 0), 0)), 0) FROM stock_documents d
                WHERE d.shop_id = ? AND d.document_type = ? AND d.status = 'CONFIRMED'
                  AND d.is_internal = 0 AND d.document_date < ?
                """, BigDecimal.class, cutoff, shops.currentId(), type, cutoff);
    }
}
