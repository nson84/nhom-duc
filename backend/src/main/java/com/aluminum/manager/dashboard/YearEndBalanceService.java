package com.aluminum.manager.dashboard;

import com.aluminum.manager.shop.ShopAccess;
import com.aluminum.manager.shop.ShopContext;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

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

    public record Period(int year, String periodName, LocalDate periodFrom, LocalDate periodTo, boolean closed,
                         boolean started, boolean endSaved, LocalDate plannedEnd) {}

    public Period selectedWorkingPeriod() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request)) return null;
        String year = request.getRequest().getParameter("workingYear");
        if (year == null || year.isBlank()) return null;
        if (!year.matches("20\\d{2}|2100")) throw invalid("Năm làm việc không hợp lệ");
        return period(Integer.parseInt(year));
    }

    public void requireWorkingDate(LocalDateTime date) {
        String monthStatus = monthStatus(YearMonth.from(date));
        if (!"OPEN".equals(monthStatus))
            throw invalid("Tháng " + YearMonth.from(date) + ("CLOSED".equals(monthStatus)
                    ? " đã khóa" : " chưa mở") + "; không thể ghi phiếu");
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request) {
            String workingMonth = request.getRequest().getParameter("workingMonth");
            if (workingMonth != null && !workingMonth.isBlank()) {
                YearMonth selectedMonth;
                try { selectedMonth = YearMonth.parse(workingMonth); }
                catch (RuntimeException ex) { throw invalid("Tháng làm việc không hợp lệ"); }
                if (!YearMonth.from(date).equals(selectedMonth))
                    throw invalid("Ngày phiếu phải thuộc tháng " + selectedMonth);
            }
        }
        var latestStart = jdbc.query("""
                SELECT year_number FROM year_period_starts
                WHERE shop_id = ? AND period_from <= ? ORDER BY period_from DESC LIMIT 1
                """, (rs, i) -> rs.getInt(1), shops.currentId(), date.toLocalDate())
                .stream().findFirst().orElse(null);
        if (latestStart != null) {
            Period matching = period(latestStart);
            if (matching.closed() || date.toLocalDate().isAfter(matching.periodTo()))
                throw invalid("Ngày phiếu nằm ngoài kỳ đang làm việc hoặc kỳ đã chốt");
        } else {
            Integer started = jdbc.queryForObject("SELECT COUNT(*) FROM year_period_starts WHERE shop_id = ?",
                    Integer.class, shops.currentId());
            if (started != null && started > 0) throw invalid("Ngày phiếu trước ngày bắt đầu kỳ đầu tiên");
        }
        Period selected = selectedWorkingPeriod();
        if (selected == null) return;
        if (!selected.started()) throw invalid("Hãy lưu ngày bắt đầu năm " + selected.year() + " ở Tổng quan");
        if (selected.closed()) throw invalid("Năm " + selected.year() + " đã chốt; không thể thêm hoặc sửa phiếu");
        if (date.toLocalDate().isBefore(selected.periodFrom()))
            throw invalid("Ngày phiếu không được trước " + selected.periodFrom());
        if (date.toLocalDate().isAfter(selected.periodTo()))
            throw invalid("Ngày phiếu không được sau " + selected.periodTo());
    }

    public String monthStatus(YearMonth month) {
        return jdbc.query("SELECT closed_at FROM shop_month_periods WHERE shop_id = ? AND month_start = ?",
                (rs, i) -> rs.getTimestamp(1) == null ? "OPEN" : "CLOSED", shops.currentId(), month.atDay(1))
                .stream().findFirst().orElse("NOT_OPEN");
    }

    public boolean isMonthClosed(YearMonth month) {
        return "CLOSED".equals(monthStatus(month));
    }

    @Transactional
    public String openMonth(int year, YearMonth month) {
        Period selected = period(year);
        if (!selected.started() || selected.closed()) throw invalid("Hãy mở kỳ năm trước khi mở tháng");
        LocalDate first = month.atDay(1).isBefore(selected.periodFrom()) ? selected.periodFrom() : month.atDay(1);
        LocalDate last = month.atEndOfMonth().isAfter(selected.periodTo()) ? selected.periodTo() : month.atEndOfMonth();
        if (first.isAfter(last)) throw invalid("Tháng này nằm ngoài kỳ năm đang chọn");
        String status = monthStatus(month);
        if ("CLOSED".equals(status)) throw invalid("Tháng đã khóa; không thể mở lại");
        if ("OPEN".equals(status)) return status;
        Integer otherOpen = jdbc.queryForObject("SELECT COUNT(*) FROM shop_month_periods WHERE shop_id = ? AND closed_at IS NULL",
                Integer.class, shops.currentId());
        if (otherOpen != null && otherOpen > 0) throw invalid("Hãy khóa tháng đang mở trước khi mở tháng khác");
        YearMonth firstMonth = YearMonth.from(selected.periodFrom());
        if (!month.equals(firstMonth)) {
            YearMonth prior = month.minusMonths(1);
            if (!"CLOSED".equals(monthStatus(prior)))
                throw invalid("Hãy khóa tháng " + prior + " trước khi mở tháng " + month);
        }
        jdbc.update("INSERT INTO shop_month_periods (shop_id, month_start, period_year, opened_at) VALUES (?, ?, ?, NOW(3))",
                shops.currentId(), month.atDay(1), year);
        return "OPEN";
    }

    @Transactional
    public boolean closeMonth(int year, YearMonth month) {
        Period selected = period(year);
        if (!selected.started() || selected.closed()) throw invalid("Kỳ năm chưa bắt đầu hoặc đã chốt");
        if (!"OPEN".equals(monthStatus(month))) throw invalid("Chỉ khóa được tháng đã mở và chưa khóa");
        LocalDate first = month.atDay(1).isBefore(selected.periodFrom()) ? selected.periodFrom() : month.atDay(1);
        LocalDate last = month.atEndOfMonth().isAfter(selected.periodTo()) ? selected.periodTo() : month.atEndOfMonth();
        if (first.isAfter(last)) throw invalid("Tháng này nằm ngoài kỳ năm đang chọn");
        if (last.isAfter(LocalDate.now()) || (!selected.endSaved() && last.isBefore(month.atEndOfMonth())))
            throw invalid("Chỉ chốt tháng sau khi hết tháng hoặc đã lưu ngày kết thúc kỳ");
        Integer drafts = jdbc.queryForObject("""
                SELECT COUNT(*) FROM stock_documents WHERE shop_id = ? AND status = 'DRAFT'
                  AND document_date >= ? AND document_date < ?
                """, Integer.class, shops.currentId(), first.atStartOfDay(), last.plusDays(1).atStartOfDay());
        if (drafts != null && drafts > 0) throw invalid("Còn phiếu nháp trong tháng; hãy xác nhận hoặc hủy trước khi chốt");
        if (ShopContext.MANUFACTURING.equals(ShopContext.code())) {
            Integer payrollClosed = jdbc.queryForObject("SELECT COUNT(*) FROM payroll_periods WHERE period_year = ? AND period_month = ? AND status = 'CLOSED'",
                    Integer.class, month.getYear(), month.getMonthValue());
            if (payrollClosed == null || payrollClosed == 0)
                throw invalid("Hãy chốt kỳ công tháng " + month + " trước khi chốt tháng Xưởng");
        }
        jdbc.update("UPDATE shop_month_periods SET closed_at = NOW(3) WHERE shop_id = ? AND month_start = ? AND closed_at IS NULL",
                shops.currentId(), month.atDay(1));
        return true;
    }

    public Period period(int year) {
        return period(year, ShopContext.code());
    }

    public Period period(int year, String shopCode) {
        if (year < 2000 || year > 2100) throw invalid("Năm không hợp lệ");
        long shopId = shops.idOf(shopCode);
        var closed = jdbc.query("""
                SELECT b.period_from, b.period_to, COALESCE(s.period_name, CONCAT('Năm ', b.year_number))
                FROM year_end_balances b LEFT JOIN year_period_starts s
                  ON s.shop_id = b.shop_id AND s.year_number = b.year_number
                WHERE b.shop_id = ? AND b.year_number = ?
                """, (rs, i) -> new Period(year, rs.getString(3), rs.getDate(1).toLocalDate(), rs.getDate(2).toLocalDate(), true, true, true, rs.getDate(2).toLocalDate()), shopId, year)
                .stream().findFirst().orElse(null);
        if (closed != null) return closed;
        var start = jdbc.query("SELECT period_from FROM year_period_starts WHERE shop_id = ? AND year_number = ?",
                (rs, i) -> rs.getDate(1).toLocalDate(), shopId, year)
                .stream().findFirst().orElse(null);
        if (start != null) {
            var end = jdbc.query("SELECT period_to FROM year_period_drafts WHERE shop_id = ? AND year_number = ?",
                    (rs, i) -> rs.getDate(1).toLocalDate(), shopId, year)
                    .stream().findFirst().orElse(null);
            if (end == null && ShopContext.MANUFACTURING.equals(shopCode)) {
                end = jdbc.query("SELECT period_to FROM factory_year_counts WHERE year_number = ?",
                        (rs, i) -> rs.getDate(1).toLocalDate(), year).stream().findFirst().orElse(null);
            }
            return new Period(year, periodName(shopId, year), start, activeEnd(start), false, true, end != null, end);
        }
        if (ShopContext.MANUFACTURING.equals(shopCode)) {
            var draft = jdbc.query("SELECT period_from, period_to FROM factory_year_counts WHERE year_number = ?",
                    (rs, i) -> new Period(year, "Năm " + year, rs.getDate(1).toLocalDate(), activeEnd(rs.getDate(1).toLocalDate()), false, true, true, rs.getDate(2).toLocalDate()), year)
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
        return new Period(year, "Năm " + year, from, from.plusYears(1).minusDays(1), false, false, false, null);
    }

    private String periodName(long shopId, int year) {
        return jdbc.query("SELECT period_name FROM year_period_starts WHERE shop_id = ? AND year_number = ?",
                (rs, i) -> rs.getString(1), shopId, year).stream().findFirst().orElse("Năm " + year);
    }

    private static LocalDate activeEnd(LocalDate from) {
        LocalDate today = LocalDate.now();
        if (today.isBefore(from)) return from;
        LocalDate latestAllowed = from.plusDays(400);
        return today.isAfter(latestAllowed) ? latestAllowed : today;
    }

    @Transactional
    public Period saveStart(int year, LocalDate from, String name) {
        if (year < 2000 || year > 2100 || from == null || Math.abs(year - from.getYear()) > 1)
            throw invalid("Tên năm hoặc ngày bắt đầu không hợp lệ");
        if (name == null || name.isBlank() || name.trim().length() > 100)
            throw invalid("Tên kỳ phải có từ 1 đến 100 ký tự");
        if (from.isAfter(LocalDate.now())) throw invalid("Không thể bắt đầu kỳ ở ngày tương lai");
        long shopId = shops.currentId();
        if (get(year) != null) throw invalid("Năm đã chốt, không thể đổi ngày bắt đầu");
        Integer closedMonths = jdbc.queryForObject("SELECT COUNT(*) FROM shop_month_periods WHERE shop_id = ? AND period_year = ?",
                Integer.class, shopId, year);
        if (closedMonths != null && closedMonths > 0) throw invalid("Kỳ đã có tháng chốt; không thể đổi mốc bắt đầu");
        LocalDate priorEnd = jdbc.query("SELECT period_to FROM year_end_balances WHERE shop_id = ? AND year_number = ?",
                (rs, i) -> rs.getDate(1).toLocalDate(), shopId, year - 1).stream().findFirst().orElse(null);
        Integer priorOpen = jdbc.queryForObject("SELECT COUNT(*) FROM year_period_starts WHERE shop_id = ? AND year_number = ?",
                Integer.class, shopId, year - 1);
        if (priorEnd == null && priorOpen != null && priorOpen > 0)
            throw invalid("Hãy chốt kỳ trước trước khi bắt đầu kỳ tiếp theo");
        if (priorEnd != null && !from.equals(priorEnd.plusDays(1)))
            throw invalid("Kỳ mới phải bắt đầu ngay sau ngày chốt kỳ trước");
        var end = jdbc.query("SELECT period_to FROM year_period_drafts WHERE shop_id = ? AND year_number = ?",
                (rs, i) -> rs.getDate(1).toLocalDate(), shopId, year).stream().findFirst().orElse(null);
        if (end != null) validateNamedPeriod(year, from, end);
        var nextStart = jdbc.query("SELECT period_from FROM year_end_balances WHERE shop_id = ? AND year_number = ?",
                (rs, i) -> rs.getDate(1).toLocalDate(), shopId, year + 1).stream().findFirst().orElse(null);
        if (nextStart == null) nextStart = jdbc.query("SELECT period_from FROM year_period_starts WHERE shop_id = ? AND year_number = ?",
                (rs, i) -> rs.getDate(1).toLocalDate(), shopId, year + 1).stream().findFirst().orElse(null);
        if (nextStart != null && !from.isBefore(nextStart))
            throw invalid("Ngày bắt đầu kỳ này phải trước kỳ sau");
        if (nextStart != null && end != null && !end.plusDays(1).equals(nextStart))
            throw invalid("Ngày kết thúc kỳ này phải sát ngày bắt đầu kỳ sau");
        jdbc.update("""
                INSERT INTO year_period_starts (shop_id, year_number, period_from, period_name) VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE period_from = VALUES(period_from), period_name = VALUES(period_name), updated_at = NOW(3)
                """, shopId, year, from, name.trim());
        jdbc.update("UPDATE year_period_drafts SET period_from = ?, updated_at = NOW(3) WHERE shop_id = ? AND year_number = ?",
                from, shopId, year);
        if (ShopContext.MANUFACTURING.equals(ShopContext.code()))
            jdbc.update("UPDATE factory_year_counts SET period_from = ? WHERE year_number = ? AND status <> 'CLOSED'", from, year);
        return period(year);
    }

    @Transactional
    public Period saveDraftPeriod(int year, LocalDate from, LocalDate to) {
        validateNamedPeriod(year, from, to);
        long shopId = shops.currentId();
        if (get(year) != null) throw invalid("Năm đã chốt, không thể đổi ngày kỳ");
        var savedStart = jdbc.query("SELECT period_from FROM year_period_starts WHERE shop_id = ? AND year_number = ?",
                (rs, i) -> rs.getDate(1).toLocalDate(), shopId, year).stream().findFirst().orElse(null);
        if (savedStart == null) throw invalid("Hãy lưu ngày bắt đầu trước");
        if (!savedStart.equals(from)) throw invalid("Ngày bắt đầu khác ngày đã lưu");
        LocalDate priorEnd = jdbc.query("SELECT period_to FROM year_end_balances WHERE shop_id = ? AND year_number = ?",
                (rs, i) -> rs.getDate(1).toLocalDate(), shopId, year - 1).stream().findFirst().orElse(null);
        Integer priorDraft = jdbc.queryForObject("SELECT COUNT(*) FROM year_period_starts WHERE shop_id = ? AND year_number = ?",
                Integer.class, shopId, year - 1);
        if (priorEnd == null && priorDraft != null && priorDraft > 0)
            throw invalid("Hãy chốt kỳ trước trước khi đặt ngày kỳ tiếp theo");
        LocalDate nextStart = jdbc.query("SELECT period_from FROM year_end_balances WHERE shop_id = ? AND year_number = ?",
                (rs, i) -> rs.getDate(1).toLocalDate(), shopId, year + 1).stream().findFirst().orElse(null);
        if (nextStart == null) nextStart = jdbc.query("SELECT period_from FROM year_period_starts WHERE shop_id = ? AND year_number = ?",
                (rs, i) -> rs.getDate(1).toLocalDate(), shopId, year + 1).stream().findFirst().orElse(null);
        validatePeriod(from, to, priorEnd, nextStart);
        Integer overlap = jdbc.queryForObject("""
                SELECT COUNT(*) FROM year_end_balances
                WHERE shop_id = ? AND year_number <> ? AND period_from <= ? AND period_to >= ?
                """, Integer.class, shopId, year, to, from);
        if (overlap != null && overlap > 0) throw invalid("Khoảng ngày trùng kỳ đã chốt");
        jdbc.update("""
                INSERT INTO year_period_drafts (shop_id, year_number, period_from, period_to)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE period_from = VALUES(period_from), period_to = VALUES(period_to), updated_at = NOW(3)
                """, shopId, year, from, to);
        if (ShopContext.MANUFACTURING.equals(ShopContext.code())) {
            jdbc.update("UPDATE factory_year_counts SET period_from = ?, period_to = ? WHERE year_number = ? AND status <> 'CLOSED'",
                    from, to, year);
        }
        return period(year);
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

    @Transactional
    public void save(int year, LocalDate from, LocalDate to, BigDecimal inventoryKg, BigDecimal inventoryValue, BigDecimal profit) {
        jdbc.queryForObject("SELECT id FROM shops WHERE id = ? FOR UPDATE", Long.class, shops.currentId());
        Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM year_end_balances WHERE shop_id = ? AND year_number = ?",
                Integer.class, shops.currentId(), year);
        if (existing != null && existing > 0) throw invalid("Kỳ đã chốt; bản chốt lịch sử được giữ nguyên");
        validatePeriod(from, to, null, null);
        if (to.isAfter(LocalDate.now())) throw invalid("Không thể chốt ngày tương lai");
        Integer drafts = jdbc.queryForObject("""
                SELECT COUNT(*) FROM stock_documents WHERE shop_id = ? AND status = 'DRAFT'
                  AND document_date >= ? AND document_date < ?
                """, Integer.class, shops.currentId(), from.atStartOfDay(), to.plusDays(1).atStartOfDay());
        if (drafts != null && drafts > 0)
            throw invalid("Còn phiếu nháp trong kỳ; hãy xác nhận hoặc hủy trước khi chốt năm");
        var savedStart = jdbc.query("SELECT period_from FROM year_period_starts WHERE shop_id = ? AND year_number = ?",
                (rs, i) -> rs.getDate(1).toLocalDate(), shops.currentId(), year)
                .stream().findFirst().orElse(null);
        if (savedStart == null || !from.equals(savedStart))
            throw invalid("Hãy lưu ngày bắt đầu kỳ trước khi chốt");
        var configured = jdbc.query("SELECT period_from, period_to FROM year_period_drafts WHERE shop_id = ? AND year_number = ?",
                (rs, i) -> new LocalDate[] {rs.getDate(1).toLocalDate(), rs.getDate(2).toLocalDate()}, shops.currentId(), year)
                .stream().findFirst().orElse(null);
        if (configured == null || !from.equals(configured[0]) || !to.equals(configured[1]))
            throw invalid("Hãy lưu ngày kết thúc kỳ trước khi chốt");
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
        if (shops.isDistribution()) {
            jdbc.update("""
                    INSERT INTO year_end_inventory_categories (shop_id, year_number, category_id, category_name, raw_kg)
                    SELECT ?, ?, c.id, c.name, SUM(m.quantity_delta_base)
                    FROM stock_movements m JOIN products p ON p.id = m.product_id
                    JOIN product_categories c ON c.id = m.category_id
                    WHERE p.shop_id = ? AND m.movement_at < ?
                    GROUP BY c.id, c.name
                    """, shops.currentId(), year, shops.currentId(), cutoff);
        } else {
            jdbc.update("""
                    INSERT INTO year_end_inventory_categories (shop_id, year_number, category_id, category_name, raw_kg)
                    SELECT ?, ?, c.id, c.name, r.quantity_kg
                    FROM factory_raw_counts r JOIN product_categories c ON c.id = r.category_id
                    WHERE r.year_number = ? AND c.shop_id = ?
                    """, shops.currentId(), year, year, shops.currentId());
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, null);
    }

    static void validateNamedPeriod(int year, LocalDate from, LocalDate to) {
        if (year < 2000 || year > 2100) throw invalid("Năm không hợp lệ");
        validatePeriod(from, to, null, null);
        if (Math.abs(year - from.getYear()) > 1) throw invalid("Tên năm không khớp ngày đầu kỳ");
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
