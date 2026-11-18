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

    public MonthlyBookController(JdbcTemplate jdbc, ShopAccess shops, YearEndBalanceService periods, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.shops = shops;
        this.periods = periods;
        this.mapper = mapper;
    }

    public record Count(long categoryId, String category, @NotNull @DecimalMin("0") BigDecimal rawKg,
                        @NotNull @DecimalMin("0") BigDecimal scrapKg) {}
    public record Snapshot(int year, String month, LocalDate from, LocalDate to, List<Count> counts,
                           BigDecimal sales, BigDecimal purchases, BigDecimal expenses, boolean closed) {}
    public record Input(int year, String month, @NotNull List<@NotNull @Valid Count> counts) {}

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
                (rs, i) -> new Count(rs.getLong(1), rs.getString(2), BigDecimal.ZERO, BigDecimal.ZERO), shops.currentId());
        return ApiResponse.ok(new Snapshot(year, month, from, to, counts,
                documentTotal("SALE", from, to), documentTotal("PURCHASE", from, to),
                jdbc.queryForObject("""
                    SELECT COALESCE(SUM(amount), 0) FROM financial_transactions
                    WHERE shop_id = ? AND status = 'CONFIRMED' AND transaction_type = 'EXPENSE'
                      AND category IN ('OPERATING_EXPENSE', 'MAINTENANCE', 'OTHER_EXPENSE', 'PAYROLL')
                      AND transaction_date >= ? AND transaction_date < ?
                    """, BigDecimal.class, shops.currentId(), from.atStartOfDay(), to.plusDays(1).atStartOfDay()), false), null);
    }

    @PostMapping
    @Transactional
    public ApiResponse<Snapshot> close(@Valid @RequestBody Input input) {
        // Serialize closing attempts for this shop while the snapshot is created.
        jdbc.queryForObject("SELECT id FROM shops WHERE id = ? FOR UPDATE", Long.class, shops.currentId());
        Snapshot current = get(input.year(), input.month()).data();
        if (current.closed()) throw invalid("Tháng đã có bản chốt sổ");
        if (current.to().isAfter(LocalDate.now())) throw invalid("Chỉ chốt sổ tháng đã kết thúc");
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
            return new Count(category.categoryId(), category.category(), count.rawKg(), count.scrapKg());
        }).toList();
        Snapshot snapshot = new Snapshot(current.year(), current.month(), current.from(), current.to(), counts,
                current.sales(), current.purchases(), current.expenses(), true);
        jdbc.update("INSERT INTO monthly_books (shop_id, month_start, period_year, snapshot_json) VALUES (?, ?, ?, ?)",
                shops.currentId(), parse(input.month()).atDay(1), input.year(), mapper.writeValueAsString(snapshot));
        return ApiResponse.ok(snapshot, "Đã lưu sổ tháng và kiểm kê");
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
