package com.aluminum.manager.dashboard;

import com.aluminum.manager.common.ApiResponse;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.common.ErrorCode;
import com.aluminum.manager.inventory.StockDocumentService;
import com.aluminum.manager.inventory.StockDtos.InventoryCountItemRequest;
import com.aluminum.manager.inventory.InventoryMath;
import com.aluminum.manager.shop.ShopAccess;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.HashMap;
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
    private final StockDocumentService stock;
    private final InventoryCarryoverService carryover;

    public MonthlyBookController(JdbcTemplate jdbc, ShopAccess shops, YearEndBalanceService periods, ObjectMapper mapper, MonthlyInventoryValuation valuation, StockDocumentService stock, InventoryCarryoverService carryover) {
        this.jdbc = jdbc;
        this.shops = shops;
        this.periods = periods;
        this.mapper = mapper;
        this.valuation = valuation;
        this.stock = stock;
        this.carryover = carryover;
    }

    public record Count(long categoryId, String category, @NotNull @DecimalMin("0") BigDecimal rawKg,
                        @DecimalMin("0") BigDecimal scrapKg, @DecimalMin("0") BigDecimal scrapValue, BigDecimal rawValue) {}
    public record Snapshot(int year, String month, LocalDate from, LocalDate to, List<Count> counts,
                           BigDecimal sales, BigDecimal purchases, BigDecimal expenses, boolean closed, BigDecimal openingValue, BigDecimal endingValue,
                           BigDecimal internalOut, BigDecimal profit, List<CategoryFlow> categoryFlows,
                           List<StockAtClose> stockAtClose, String reconciliationBlock) {}
    public record StockAtClose(long productId, long categoryId, String productName, String categoryName, BigDecimal quantityBase) {}
    public record CategoryFlow(long categoryId, BigDecimal openingKg, BigDecimal purchasedKg, BigDecimal deliveredKg) {
        BigDecimal availableKg() { return openingKg.add(purchasedKg).subtract(deliveredKg).max(BigDecimal.ZERO); }
    }
    public record Input(int year, String month, @NotNull List<@NotNull @Valid Count> counts, @DecimalMin("0") BigDecimal openingValue) {}
    public record ReconcileInput(int year, String month, @NotNull List<@NotNull @Valid InventoryCountItemRequest> items) {}
    private record StockKey(long productId, long categoryId) {}

    @PostMapping("/reconcile")
    @Transactional
    public ApiResponse<Snapshot> reconcile(@Valid @RequestBody ReconcileInput input) {
        if (!shops.isDistribution()) throw invalid("Chỉ Phân phối cập nhật tồn kho theo số cân tháng");
        long shopId = shops.currentId();
        jdbc.queryForObject("SELECT id FROM shops WHERE id = ? FOR UPDATE", Long.class, shopId);
        Snapshot current = get(input.year(), input.month()).data();
        if (current.closed()) throw invalid("Tháng đã có bản chốt sổ");
        if (current.to().isAfter(LocalDate.now())) throw invalid("Chỉ chốt sổ tháng đã kết thúc");
        var countDate = current.to().atTime(23, 59, 59);
        periods.requireInventoryCountDate(countDate);
        if (hasLaterMovements(current.to())) throw invalid(laterMovementsMessage());

        var counts = new HashMap<StockKey, InventoryCountItemRequest>();
        for (var item : input.items()) {
            if (counts.put(new StockKey(item.productId(), item.categoryId()), item) != null)
                throw invalid("Dòng kiểm kê bị trùng");
        }
        var adjustments = new java.util.ArrayList<InventoryCountItemRequest>();
        var totals = new HashMap<Long, BigDecimal>();
        var scraps = new HashMap<Long, BigDecimal>();
        for (var balance : stock.balances(null, false)) {
            var item = counts.remove(new StockKey(balance.productId(), balance.categoryId()));
            if (item == null) throw invalid("Nhập số cân cho tất cả dòng tồn kho. Nhập 0 nếu đã cân và kho hết hàng.");
            BigDecimal counted = item.countedQuantityKg().setScale(InventoryMath.QTY_SCALE, InventoryMath.ROUNDING);
            BigDecimal scrap = item.scrapKg() == null ? BigDecimal.ZERO : item.scrapKg();
            boolean unchanged = counted.compareTo(balance.quantityBase()) == 0;
            boolean alreadyCounted = unchanged && Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM distribution_inventory_count_evidence e
                       WHERE e.shop_id = ? AND e.product_id = ? AND e.category_id = ?
                         AND e.counted_quantity_kg = ? AND e.counted_at >= ?
                         AND NOT EXISTS (SELECT 1 FROM stock_movements m
                             WHERE m.product_id = e.product_id AND m.category_id = e.category_id
                               AND (m.created_at > e.counted_at OR m.movement_at > e.counted_at)))
                    """, Boolean.class, shopId, item.productId(), item.categoryId(), counted, current.to().atStartOfDay()));
            if (alreadyCounted) {
                if (item.scrapKg() != null && scrap.compareTo(balance.latestCountScrapKg()) != 0)
                    throw invalid("Dòng đã kiểm kê có số vụn khác. Hãy đối chiếu số vụn đã lưu trước khi chốt.");
                scrap = balance.latestCountScrapKg();
            } else {
                adjustments.add(item);
            }
            totals.merge(item.categoryId(), counted, BigDecimal::add);
            scraps.merge(item.categoryId(), scrap, BigDecimal::add);
        }
        if (!counts.isEmpty()) throw invalid("Danh sách tồn kho đã thay đổi. Hãy tải lại màn hình.");
        stock.applyPhysicalCount(adjustments, countDate);
        var snapshotCounts = current.counts().stream().map(row -> new Count(row.categoryId(), row.category(),
                totals.getOrDefault(row.categoryId(), BigDecimal.ZERO),
                scraps.getOrDefault(row.categoryId(), BigDecimal.ZERO), null, null)).toList();
        return close(new Input(input.year(), input.month(), snapshotCounts, null));
    }

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
        boolean distribution = shops.isDistribution();
        String block = distribution ? reconciliationBlock(period, selected, to) : null;
        return ApiResponse.ok(new Snapshot(year, month, from, to, counts,
                revenue(from, to), documentTotal("PURCHASE", from, to),
                jdbc.queryForObject("""
                    SELECT COALESCE(SUM(amount), 0) FROM financial_transactions
                    WHERE shop_id = ? AND status = 'CONFIRMED' AND transaction_type = 'EXPENSE'
                      AND (category IN ('OPERATING_EXPENSE', 'MAINTENANCE', 'OTHER_EXPENSE')
                           OR (category = 'PAYROLL' AND reference_type = 'MANUAL'))
                      AND transaction_date >= ? AND transaction_date < ?
                    """, BigDecimal.class, shops.currentId(), from.atStartOfDay(), to.plusDays(1).atStartOfDay()).add(payroll(from)), false, openingValue(year, from), null, internalOut(from, to), null,
                distribution ? List.of() : categoryFlows(year, month, period.periodFrom(), from, to, counts),
                distribution ? stockAtClose(to, block == null) : List.of(), block), null);
    }

    private List<StockAtClose> stockAtClose(LocalDate to, boolean includeEmptyBalances) {
        return jdbc.query("""
                SELECT p.id, c.id, p.name, c.name, COALESCE(SUM(m.quantity_delta_base), 0)
                FROM stock_balances b JOIN products p ON p.id = b.product_id
                JOIN product_categories c ON c.id = b.category_id
                LEFT JOIN stock_movements m ON m.product_id = b.product_id AND m.category_id = b.category_id
                  AND m.movement_at < ?
                WHERE p.shop_id = ? AND (? OR m.id IS NOT NULL)
                GROUP BY p.id, c.id, p.name, c.name
                ORDER BY p.name, c.name, p.id, c.id
                """, (rs, i) -> new StockAtClose(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5)),
                to.plusDays(1).atStartOfDay(), shops.currentId(), includeEmptyBalances);
    }

    private String reconciliationBlock(YearEndBalanceService.Period period, YearMonth month, LocalDate to) {
        if (period.closed()) return "Kỳ đã chốt. Chỉ xem số liệu tháng.";
        if (hasLaterMovements(to)) return laterMovementsMessage();
        if (to.isAfter(LocalDate.now())) return "Chỉ chốt sổ khi tháng đã kết thúc.";
        if ("NOT_OPEN".equals(periods.monthStatus(month))) return "Tháng chưa mở. Chỉ xem số liệu tháng.";
        return null;
    }

    private boolean hasLaterMovements(LocalDate to) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM stock_movements m JOIN products p ON p.id = m.product_id
                WHERE p.shop_id = ? AND m.movement_at >= ?)
                """, Boolean.class, shops.currentId(), to.plusDays(1).atStartOfDay()));
    }

    private static String laterMovementsMessage() {
        return "Kho đã biến động sau cuối tháng. Không thể dùng số cân tháng cũ để cập nhật kho hiện tại.";
    }

    private List<CategoryFlow> categoryFlows(int year, String month, LocalDate periodFrom, LocalDate from, LocalDate to, List<Count> counts) {
        var opening = carryover.get(year, month);
        var openingKg = new HashMap<Long, BigDecimal>();
        var previousFlows = new HashMap<Long, CategoryFlow>();
        for (var row : opening.rows()) {
            openingKg.put(row.categoryId(), row.rawKg().add(row.scrapKg() == null ? BigDecimal.ZERO : row.scrapKg()));
        }
        if (!opening.available() && periodFrom.isBefore(from)) {
            var priorBooks = jdbc.query("""
                    SELECT snapshot_json FROM monthly_books
                    WHERE shop_id = ? AND period_year = ? AND month_start < ? ORDER BY month_start DESC
                    """, (rs, i) -> mapper.readValue(rs.getString(1), Snapshot.class), shops.currentId(), year, from);
            var prior = priorBooks.stream()
                    .filter(book -> book.closed() && !book.from().isBefore(periodFrom) && book.to().isBefore(from))
                    .filter(book -> book.counts() != null && book.counts().stream().allMatch(row -> row.rawKg() != null && row.scrapKg() != null))
                    .findFirst().orElse(null);
            if (prior != null) {
                for (var row : prior.counts()) openingKg.put(row.categoryId(), row.rawKg().add(row.scrapKg()));
                previousFlows = documentFlows(prior.to().plusDays(1), from);
            } else {
                for (var row : carryover.get(year, null).rows()) openingKg.put(row.categoryId(), row.rawKg());
                previousFlows = documentFlows(periodFrom, from);
            }
        }
        for (var flow : previousFlows.values()) {
            openingKg.merge(flow.categoryId(), flow.purchasedKg().subtract(flow.deliveredKg()), BigDecimal::add);
        }
        var flows = documentFlows(from, to.plusDays(1));
        return counts.stream().map(count -> {
            var flow = flows.get(count.categoryId());
            return new CategoryFlow(count.categoryId(), openingKg.getOrDefault(count.categoryId(), BigDecimal.ZERO).max(BigDecimal.ZERO),
                    flow == null ? BigDecimal.ZERO : flow.purchasedKg(), flow == null ? BigDecimal.ZERO : flow.deliveredKg());
        }).toList();
    }

    private HashMap<Long, CategoryFlow> documentFlows(LocalDate from, LocalDate until) {
        var flows = new HashMap<Long, CategoryFlow>();
        jdbc.query("""
                SELECT i.category_id,
                       SUM(CASE WHEN d.document_type = 'PURCHASE' THEN i.quantity_base ELSE 0 END) AS purchased,
                       SUM(CASE WHEN d.document_type = 'SALE' THEN i.quantity_base ELSE 0 END) AS delivered
                FROM stock_document_items i JOIN stock_documents d ON d.id = i.document_id
                WHERE d.shop_id = ? AND d.status = 'CONFIRMED' AND d.document_date >= ? AND d.document_date < ?
                  AND (d.document_type = 'PURCHASE' OR (d.document_type = 'SALE' AND d.is_internal = 0
                       AND COALESCE(i.billing_unit, 'kg') <> 'm2'))
                  AND NOT EXISTS (SELECT 1 FROM stock_documents r WHERE r.reversal_of_document_id = d.id)
                GROUP BY i.category_id
                """, rs -> {
            long id = rs.getLong("category_id");
            flows.put(id, new CategoryFlow(id, BigDecimal.ZERO, rs.getBigDecimal("purchased"), rs.getBigDecimal("delivered")));
        }, shops.currentId(), from.atStartOfDay(), until.atStartOfDay());
        return flows;
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
            BigDecimal scrap = count.scrapKg() == null ? BigDecimal.ZERO : count.scrapKg();
            if (!shops.isDistribution()) {
                BigDecimal maxAvailable = current.categoryFlows().stream()
                        .filter(flow -> flow.categoryId() == category.categoryId()).findFirst().orElseThrow().availableKg();
                if (count.rawKg().add(scrap).compareTo(maxAvailable) > 0) {
                    throw invalid("Tổng nguyên liệu còn tồn và vụn nhôm của " + category.category()
                            + " không được lớn hơn tồn đầu tháng cộng nhập trừ giao trong tháng (" + maxAvailable.stripTrailingZeros().toPlainString() + " kg)");
                }
            }
            return new Count(category.categoryId(), category.category(), count.rawKg(), scrap, count.scrapValue() == null ? BigDecimal.ZERO : count.scrapValue(),
                    valuation.rawValue(count.categoryId(), count.rawKg(), current.to()));
        }).toList();
        boolean unvaluedScrap = input.counts().stream().anyMatch(count -> count.scrapKg() != null && count.scrapKg().signum() > 0
                && (count.scrapValue() == null || count.scrapValue().signum() == 0));
        BigDecimal endingValue = unvaluedScrap ? null : counts.stream().map(count -> count.rawValue().add(count.scrapValue())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal profit = current.openingValue() == null || unvaluedScrap ? null
                : MonthlyInventoryValuation.profit(current.sales(), current.openingValue(), current.purchases(), current.internalOut(), endingValue, current.expenses());
        Snapshot snapshot = new Snapshot(current.year(), current.month(), current.from(), current.to(), counts,
                current.sales(), current.purchases(), current.expenses(), true, current.openingValue(), endingValue, current.internalOut(), profit, current.categoryFlows(), null, null);
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
        var annual = jdbc.query("SELECT inventory_value FROM year_end_balances WHERE shop_id = ? AND period_to = ?",
                (rs, i) -> rs.getBigDecimal(1), shops.currentId(), from.minusDays(1));
        if (!annual.isEmpty()) return annual.get(0);
        var previous = jdbc.query("SELECT snapshot_json FROM monthly_books WHERE shop_id = ? AND month_start < ? ORDER BY month_start DESC LIMIT 1",
                (rs, i) -> mapper.readValue(rs.getString(1), Snapshot.class), shops.currentId(), from.withDayOfMonth(1));
        if (!previous.isEmpty() && previous.get(0).to().plusDays(1).equals(from)) return previous.get(0).endingValue();
        return null;
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
