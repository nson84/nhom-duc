package com.aluminum.manager.dashboard;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.shop.ShopAccess;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Opening inventory is a view of the preceding close, never a new stock receipt. */
@Service
public class InventoryCarryoverService {
    private final JdbcTemplate jdbc;
    private final ShopAccess shops;
    private final YearEndBalanceService periods;
    private final ObjectMapper mapper;

    public InventoryCarryoverService(JdbcTemplate jdbc, ShopAccess shops, YearEndBalanceService periods, ObjectMapper mapper) {
        this.jdbc = jdbc; this.shops = shops; this.periods = periods; this.mapper = mapper;
    }

    public record Row(long categoryId, String category, BigDecimal rawKg, BigDecimal scrapKg) {}
    public record Opening(LocalDate from, LocalDate sourceTo, String source, boolean available,
                          List<Row> rows, BigDecimal totalRawKg, BigDecimal totalScrapKg, String message) {}

    @Transactional(readOnly = true)
    public Opening get(int year, String month) {
        var period = periods.period(year);
        if (!period.started()) return missing(period.periodFrom(), "Hãy lưu ngày bắt đầu kỳ trước.");
        LocalDate from = period.periodFrom();
        if (month != null && !month.isBlank()) {
            YearMonth selected;
            try { selected = YearMonth.parse(month); }
            catch (RuntimeException error) { throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tháng không hợp lệ", null); }
            if (selected.atEndOfMonth().isBefore(from) || selected.atDay(1).isAfter(period.periodTo()))
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "Tháng nằm ngoài kỳ đang chọn", null);
            if (selected.atDay(1).isAfter(from)) from = selected.atDay(1);
        }
        LocalDate cutoff = from.minusDays(1);
        var annual = jdbc.query("SELECT year_number, inventory_kg FROM year_end_balances WHERE shop_id = ? AND period_to = ?",
                (rs, i) -> new Annual(rs.getInt(1), rs.getBigDecimal(2)), shops.currentId(), cutoff);
        if (!annual.isEmpty()) {
            var previous = annual.get(0);
            var rows = jdbc.query("SELECT category_id, category_name, raw_kg FROM year_end_inventory_categories WHERE shop_id = ? AND year_number = ? ORDER BY category_name, category_id",
                    (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), null), shops.currentId(), previous.year());
            // Older annual closes did not store a category snapshot. Reuse their recorded counts/history only if totals match.
            if (rows.isEmpty()) rows = legacyAnnualRows(previous.year(), cutoff);
            BigDecimal expected = previous.kg();
            if (!shops.isDistribution()) {
                var raw = jdbc.query("SELECT raw_kg FROM factory_year_counts WHERE year_number = ? AND status = 'CLOSED'",
                        (rs, i) -> rs.getBigDecimal(1), previous.year());
                if (!raw.isEmpty()) expected = raw.get(0);
            }
            if (rows.stream().anyMatch(row -> row.rawKg().signum() < 0) || sumRaw(rows).compareTo(expected) != 0)
                return missing(from, "Bản chốt năm cũ chưa đủ chi tiết từng loại; cần đối chiếu, không tự chia tổng tồn.");
            return result(from, cutoff, "YEAR", rows);
        }
        var monthly = jdbc.query("SELECT snapshot_json FROM monthly_books WHERE shop_id = ? AND month_start = ? ORDER BY period_year DESC",
                (rs, i) -> mapper.readValue(rs.getString(1), MonthlyBookController.Snapshot.class), shops.currentId(), cutoff.withDayOfMonth(1));
        for (var snapshot : monthly) {
            if (snapshot.closed() && cutoff.equals(snapshot.to()) && snapshot.counts() != null) {
                if (snapshot.counts().stream().anyMatch(c -> c.rawKg() == null || c.scrapKg() == null))
                    return missing(from, "Bản chốt tháng chưa đủ chi tiết kiểm kê từng loại.");
                return result(from, cutoff, "MONTH", snapshot.counts().stream()
                        .map(c -> new Row(c.categoryId(), c.category(), c.rawKg(), c.scrapKg())).toList());
            }
        }
        return missing(from, "Chưa có bản kiểm kê đã chốt kết thúc ngay trước kỳ này. Tồn kho đang có vẫn được giữ, không tự nhập lại.");
    }

    private List<Row> legacyAnnualRows(int year, LocalDate to) {
        if (shops.isDistribution()) return jdbc.query("""
                SELECT c.id, c.name, SUM(m.quantity_delta_base)
                FROM stock_movements m JOIN products p ON p.id = m.product_id
                JOIN product_categories c ON c.id = m.category_id
                WHERE p.shop_id = ? AND m.movement_at < ? GROUP BY c.id, c.name
                """, (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), null), shops.currentId(), to.plusDays(1).atStartOfDay());
        return jdbc.query("""
                SELECT c.id, c.name, r.quantity_kg FROM factory_raw_counts r
                JOIN product_categories c ON c.id = r.category_id WHERE r.year_number = ? AND c.shop_id = ?
                """, (rs, i) -> new Row(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), null), year, shops.currentId());
    }

    private static BigDecimal sumRaw(List<Row> rows) { return rows.stream().map(Row::rawKg).reduce(BigDecimal.ZERO, BigDecimal::add); }
    private static Opening result(LocalDate from, LocalDate to, String source, List<Row> rows) {
        return new Opening(from, to, source, true, rows, sumRaw(rows),
                "YEAR".equals(source) ? null : rows.stream().map(Row::scrapKg).reduce(BigDecimal.ZERO, BigDecimal::add),
                "Giữ nguyên tồn cuối kỳ trước theo từng loại; không tạo phiếu nhập hoặc cộng kho thêm lần nữa.");
    }
    private static Opening missing(LocalDate from, String message) {
        return new Opening(from, null, null, false, List.of(), BigDecimal.ZERO, BigDecimal.ZERO, message);
    }
    private record Annual(int year, BigDecimal kg) {}
}
