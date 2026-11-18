package com.aluminum.manager.dashboard;

import com.aluminum.manager.common.ApiResponse;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.shop.ShopAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/v1/dashboard/monthly-book")
public class MonthlyBookController {
    private final JdbcTemplate jdbc;
    private final ShopAccess shops;
    private final YearEndBalanceService periods;
    private final ObjectMapper mapper;
    private final MonthlyInventoryValuation valuation;

    public MonthlyBookController(JdbcTemplate jdbc, ShopAccess shops, YearEndBalanceService periods, ObjectMapper mapper, MonthlyInventoryValuation valuation) {
        this.jdbc = jdbc;
        this.shops = shops;
        this.periods = periods;
        this.mapper = mapper;
        this.valuation = valuation;
    }

    public record Count(long categoryId, String category, @NotNull @DecimalMin("0") BigDecimal rawKg,
                        @NotNull @DecimalMin("0") BigDecimal scrapKg, @DecimalMin("0") BigDecimal scrapValue, BigDecimal rawValue) {}
    public record Snapshot(int year, String month, LocalDate from, LocalDate to, List<Count> counts,
                           BigDecimal sales, BigDecimal purchases, BigDecimal expenses, boolean closed, BigDecimal openingValue, BigDecimal endingValue,
                           BigDecimal internalOut, BigDecimal profit) {}
    public record Input(int year, String month, @NotNull List<@NotNull @Valid Count> counts, @NotNull @DecimalMin("0") BigDecimal openingValue) {}

    @GetMapping
    @Transactional(readOnly = true)
    public ApiResponse<Snapshot> get(@RequestParam int year, @RequestParam String month) {
        YearMonth selected = parse(month);
        var period = periods.period(year);
        if (!period.started()) throw invalid("Hãy lưu ngày bắt đầu kỳ trước");
        LocalDate from = selected.atDay(1).isBefore(period.periodFrom()) ? period.periodFrom() : selected.atDay(1);
        if (from.isAfter(selected.atEndOfMonth()) || (period.closed() && from.isAfter(period.periodTo())))
            throw invalid("Tháng nằm ngoài kỳ đang chọn");
        if (!period.closed() && selected.atDay(1).isAfter(period.periodTo()))
            throw invalid("Tháng nằm ngoài phạm vi làm việc của kỳ đang chọn");
        LocalDate to = period.closed() && period.periodTo().isBefore(selected.atEndOfMonth())
                ? period.periodTo() : selected.atEndOfMonth();
        var saved = jdbc.query("SELECT snapshot_json FROM monthly_books WHERE shop_id = ? AND month_start = ? AND period_year = ?",
                (rs, i) -> mapper.readValue(rs.getString(1), Snapshot.class), shops.currentId(), selected.atDay(1), year);
        if (!saved.isEmpty()) return ApiResponse.ok(saved.get(0), null);
        var counts = jdbc.query("SELECT id, name FROM product_categories WHERE shop_id = ? ORDER BY id",
                (rs, i) -> new Count(rs.getLong(1), rs.getString(2), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, null), shops.currentId());
        return ApiResponse.ok(new Snapshot(year, month, from, to, counts,
                revenue(from, to), documentTotal("PURCHASE", from, to),
                jdbc.queryForObject("""
                    SELECT COALESCE(SUM(amount), 0) FROM financial_transactions
                    WHERE shop_id = ? AND status = 'CONFIRMED' AND transaction_type = 'EXPENSE'
                      AND (category IN ('OPERATING_EXPENSE', 'MAINTENANCE', 'OTHER_EXPENSE')
                           OR (category = 'PAYROLL' AND reference_type = 'MANUAL'))
                      AND transaction_date >= ? AND transaction_date < ?
                    """, BigDecimal.class, shops.currentId(), from.atStartOfDay(), to.plusDays(1).atStartOfDay()).add(payroll(from)), false, openingValue(year, from), null, internalOut(from, to), null), null);
    }

    @PostMapping
    @Transactional
    public ApiResponse<Snapshot> close(@Valid @RequestBody Input input) {
        // Serialize closing attempts for this shop while the snapshot is created.
        jdbc.queryForObject("SELECT id FROM shops WHERE id = ? FOR UPDATE", Long.class, shops.currentId());
        Snapshot current = get(input.year(), input.month()).data();
        if (current.closed()) throw invalid("Tháng đã có bản chốt sổ");
        if (current.to().isAfter(LocalDate.now())) throw invalid("Chỉ chốt sổ tháng đã kết thúc");
        if (!shops.isDistribution()) {
            Integer closedPayroll = jdbc.queryForObject("SELECT COUNT(*) FROM payroll_periods WHERE period_year = ? AND period_month = ? AND status = 'CLOSED'",
                    Integer.class, current.from().getYear(), current.from().getMonthValue());
            if (closedPayroll == null || closedPayroll == 0)
                throw invalid("Hãy chốt tiền công tháng trước khi xác nhận sổ tháng Xưởng");
        }
        Integer drafts = jdbc.queryForObject("""
                SELECT COUNT(*) FROM stock_documents WHERE shop_id = ? AND status = 'DRAFT'
                  AND document_date >= ? AND document_date < ?
                """, Integer.class, shops.currentId(), current.from().atStartOfDay(), current.to().plusDays(1).atStartOfDay());
        if (drafts != null && drafts > 0) throw invalid("Còn phiếu nháp trong tháng; hãy xử lý trước khi chốt sổ");
        var ids = new HashSet<Long>();
        for (Count count : input.counts()) {
            if (!ids.add(count.categoryId()) || current.counts().stream().noneMatch(c -> c.categoryId() == count.categoryId()))
                throw invalid("Loại nhôm kiểm kê không hợp lệ");
        }
        if (ids.size() != current.counts().size()) throw invalid("Cần gửi đủ các loại nhôm; ô trống tính là 0");
        var counts = current.counts().stream().map(category -> {
            Count count = input.counts().stream().filter(c -> c.categoryId() == category.categoryId()).findFirst().orElseThrow();
            return new Count(category.categoryId(), category.category(), count.rawKg(), count.scrapKg(), count.scrapValue() == null ? BigDecimal.ZERO : count.scrapValue(),
                    valuation.rawValue(count.categoryId(), count.rawKg(), current.to()));
        }).toList();
        BigDecimal endingValue = counts.stream().map(count -> count.rawValue().add(count.scrapValue())).reduce(BigDecimal.ZERO, BigDecimal::add);
        Snapshot snapshot = new Snapshot(current.year(), current.month(), current.from(), current.to(), counts,
                current.sales(), current.purchases(), current.expenses(), true, input.openingValue(), endingValue, current.internalOut(),
                MonthlyInventoryValuation.profit(current.sales(), input.openingValue(), current.purchases(), current.internalOut(), endingValue, current.expenses()));
        jdbc.update("INSERT INTO monthly_books (shop_id, month_start, period_year, snapshot_json) VALUES (?, ?, ?, ?)",
                shops.currentId(), parse(input.month()).atDay(1), input.year(), mapper.writeValueAsString(snapshot));
        return ApiResponse.ok(snapshot, "Đã lưu sổ tháng và kiểm kê");
    }

    private BigDecimal revenue(LocalDate from, LocalDate to) {
        BigDecimal sales = documentTotal("SALE", from, to);
        if (shops.isDistribution()) return sales;
        return sales.add(jdbc.queryForObject("SELECT COALESCE(SUM(total_amount), 0) FROM factory_scrap_sales WHERE shop_id = ? AND status = 'CONFIRMED' AND sale_date >= ? AND sale_date < ?",
                BigDecimal.class, shops.currentId(), from.atStartOfDay(), to.plusDays(1).atStartOfDay()));
    }

    private BigDecimal payroll(LocalDate from) {
        if (shops.isDistribution()) return BigDecimal.ZERO;
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(pd.base_pay + pd.overtime_pay + pd.bonus_pay + pd.allowances - pd.deductions
                    + COALESCE(pd.holiday_bonus_received, 0)), 0)
                FROM payroll_details pd JOIN payroll_periods pp ON pp.id = pd.payroll_period_id
                JOIN employees e ON e.id = pd.employee_id
                WHERE e.shop_id = ? AND pp.period_year = ? AND pp.period_month = ?
                """, BigDecimal.class, shops.currentId(), from.getYear(), from.getMonthValue());
    }

    private BigDecimal openingValue(int year, LocalDate from) {
        var previous = jdbc.query("SELECT snapshot_json FROM monthly_books WHERE shop_id = ? AND month_start < ? ORDER BY month_start DESC LIMIT 1",
                (rs, i) -> mapper.readValue(rs.getString(1), Snapshot.class), shops.currentId(), from.withDayOfMonth(1));
        if (!previous.isEmpty() && previous.get(0).to().plusDays(1).equals(from)) return previous.get(0).endingValue();
        var annual = jdbc.query("SELECT inventory_value FROM year_end_balances WHERE shop_id = ? AND period_to = ?",
                (rs, i) -> rs.getBigDecimal(1), shops.currentId(), from.minusDays(1));
        return annual.isEmpty() ? null : annual.get(0);
    }

    private BigDecimal internalOut(LocalDate from, LocalDate to) {
        if (!shops.isDistribution()) return BigDecimal.ZERO;
        return jdbc.queryForObject("SELECT COALESCE(SUM(total_amount), 0) FROM stock_documents WHERE shop_id = ? AND document_type = 'SALE' AND is_internal = 1 AND status = 'CONFIRMED' AND document_date >= ? AND document_date < ?",
                BigDecimal.class, shops.currentId(), from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    private BigDecimal documentTotal(String type, LocalDate from, LocalDate to) {
        return jdbc.queryForObject("""
                SELECT COALESCE(SUM(total_amount), 0) FROM stock_documents
                WHERE shop_id = ? AND document_type = ? AND status = 'CONFIRMED'
                  AND (? = 'PURCHASE' OR is_internal = 0) AND document_date >= ? AND document_date < ?
                """, BigDecimal.class, shops.currentId(), type, type, from.atStartOfDay(), to.plusDays(1).atStartOfDay());
    }

    private static YearMonth parse(String value) {
        try { return YearMonth.parse(value); }
        catch (RuntimeException e) { throw invalid("Tháng không hợp lệ"); }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message, null);
    }
}
