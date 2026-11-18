package com.aluminum.manager.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import com.aluminum.manager.auth.UserRole;
import com.aluminum.manager.catalog.PurchasePriceService;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.dashboard.YearEndBalanceService;
import com.aluminum.manager.dashboard.DashboardService;
import com.aluminum.manager.dashboard.MonthlyBookController;
import com.aluminum.manager.dashboard.MonthlyInventoryValuation;
import com.aluminum.manager.finance.FinanceService;
import com.aluminum.manager.security.AuthenticatedAdmin;
import com.aluminum.manager.shop.ShopAccess;
import com.aluminum.manager.shop.ShopContext;
import com.aluminum.manager.system.AuditService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;

class StockLifecycleMysqlIT {
    private JdbcTemplate jdbc;
    private TransactionTemplate transactions;
    private StockDocumentService service;
    private long product;
    private long category;
    private long unit;
    private long customer;
    private ShopAccess shops;
    private YearEndBalanceService periods;
    private Clock clock;
    private final LocalDateTime day = LocalDateTime.of(2025, 2, 18, 10, 0);

    @BeforeEach
    void createIsolatedDatabase() {
        String testUrl = System.getenv("ALUMINUM_TEST_DB_URL");
        assumeTrue(testUrl != null && testUrl.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/aluminum_backup_test(?:\\?.*)?"));
        String username = System.getenv("ALUMINUM_TEST_DB_USERNAME");
        String password = System.getenv("ALUMINUM_TEST_DB_PASSWORD");
        assumeTrue(username != null && password != null);
        String database = "aluminum_test_" + UUID.randomUUID().toString().replace("-", "");
        String url = testUrl.replace("/aluminum_backup_test", "/" + database);
        url += url.contains("?") ? "&createDatabaseIfNotExist=true" : "?createDatabaseIfNotExist=true";
        var source = new DriverManagerDataSource(url, username, password);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(source);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(source));
        ShopContext.set(ShopContext.DISTRIBUTION);
        shops = new ShopAccess(jdbc);
        clock = Clock.fixed(Instant.parse("2025-02-20T10:00:00Z"), ZoneOffset.UTC);
        periods = new YearEndBalanceService(jdbc, shops);
        service = new StockDocumentService(jdbc, new DocumentSequenceService(jdbc), mock(AuditService.class),
                new PurchasePriceService(jdbc, clock), clock, shops, periods);
        long shopId = shops.currentId();
        jdbc.update("INSERT INTO year_period_starts (shop_id, year_number, period_from, period_name) VALUES (?, 2025, '2025-02-18', 'Test')", shopId);
        jdbc.update("INSERT INTO shop_month_periods (shop_id, month_start, period_year, opened_at) VALUES (?, '2025-02-01', 2025, NOW())", shopId);
        jdbc.update("INSERT INTO product_categories (shop_id, name) VALUES (?, 'Test aluminium')", shopId);
        category = jdbc.queryForObject("SELECT id FROM product_categories WHERE shop_id = ? AND name = 'Test aluminium'", Long.class, shopId);
        jdbc.update("INSERT INTO products (shop_id, product_code, name, category_id, base_unit_name) VALUES (?, 'TEST-AL', 'Test aluminium', ?, 'kg')", shopId, category);
        product = jdbc.queryForObject("SELECT id FROM products WHERE shop_id = ? AND product_code = 'TEST-AL'", Long.class, shopId);
        // The products insert trigger already creates its base kg unit.
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM product_units WHERE product_id = ? AND unit_name = 'kg'", Integer.class, product));
        jdbc.update("INSERT INTO product_units (product_id, unit_name, factor_to_base) VALUES (?, 'tấn', 1000)", product);
        unit = jdbc.queryForObject("SELECT id FROM product_units WHERE product_id = ? AND unit_name = 'tấn'", Long.class, product);
        jdbc.update("INSERT INTO customers (shop_id, name) VALUES (?, 'Test customer')", shopId);
        customer = jdbc.queryForObject("SELECT id FROM customers WHERE shop_id = ? AND name = 'Test customer'", Long.class, shopId);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedAdmin(1L, "test", false, UserRole.DISTRIBUTION, ShopContext.DISTRIBUTION), null, List.of()));
    }

    @AfterEach
    void clearContext() {
        ShopContext.clear();
        SecurityContextHolder.clearContext();
    }

    private <T> T transaction(Supplier<T> action) {
        return transactions.execute(status -> action.get());
    }

    private StockDtos.DocumentRequest request(String type, String tons, String price) {
        return new StockDtos.DocumentRequest(type, day, "Test customer", customer, "Hải", "Thuấn",
                "Integration test", BigDecimal.ZERO, "CASH", null, UUID.randomUUID().toString(),
                List.of(new StockDtos.ItemRequest(product, category, unit, new BigDecimal(tons),
                        new BigDecimal(price), BigDecimal.ZERO, "kg", null, null, null, null)), false);
    }

    private StockDtos.DocumentDto confirm(String type, String tons, String price) {
        return transaction(() -> service.confirm(service.createDraft(request(type, tons, price)).id()));
    }

    private void createFactoryMirror() {
        long factory = shops.idOf(ShopContext.MANUFACTURING);
        jdbc.update("INSERT INTO year_period_starts (shop_id, year_number, period_from, period_name) VALUES (?, 2025, '2025-02-18', 'Test')", factory);
        jdbc.update("INSERT INTO shop_month_periods (shop_id, month_start, period_year, opened_at) VALUES (?, '2025-02-01', 2025, NOW())", factory);
        jdbc.update("INSERT INTO product_categories (shop_id, name) VALUES (?, 'Test aluminium')", factory);
        long factoryCategory = jdbc.queryForObject("SELECT id FROM product_categories WHERE shop_id = ? AND name = 'Test aluminium'", Long.class, factory);
        jdbc.update("INSERT INTO products (shop_id, product_code, name, category_id, base_unit_name) VALUES (?, 'TEST-AL', 'Test aluminium', ?, 'kg')", factory, factoryCategory);
        long factoryProduct = jdbc.queryForObject("SELECT id FROM products WHERE shop_id = ? AND product_code = 'TEST-AL'", Long.class, factory);
        jdbc.update("INSERT INTO product_units (product_id, unit_name, factor_to_base) VALUES (?, 'tấn', 1000)", factoryProduct);
    }

    private StockDtos.DocumentDto internalTransfer() {
        var request = request("SALE", "2", "75000000");
        return transaction(() -> service.confirm(service.createDraft(new StockDtos.DocumentRequest(request.documentType(), request.documentDate(),
                request.counterpartyName(), request.customerId(), request.issuedBy(), request.receivedBy(), request.reason(), request.discountAmount(),
                request.paymentMethod(), request.notes(), request.idempotencyKey(), request.items(), true)).id()));
    }

    @Test
    void internalTransferHasNoRevenueOrDebtAndDoesNotReduceAnnualProfit() {
        createFactoryMirror();
        confirm("PURCHASE", "10", "75000000");
        var transfer = internalTransfer();
        assertTrue(transfer.internal());
        assertNotNull(transfer.internalPairDocumentId());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM financial_transactions WHERE reference_id = ? AND reference_type = 'STOCK_DOCUMENT'", Integer.class, transfer.id()));
        confirm("SALE", "2", "76000000");
        jdbc.update("INSERT INTO distribution_inventory_count_evidence (shop_id, product_id, category_id, counted_quantity_kg, counted_at) VALUES (?, ?, ?, 6000, NOW(3))", shops.currentId(), product, category);
        var dashboard = new DashboardService(jdbc, clock, shops, periods);
        var closing = transaction(() -> dashboard.closeDistribution(day.toLocalDate(), day.toLocalDate().plusDays(2), false, null));
        assertEquals(0, new BigDecimal("2000000").compareTo(closing.profit()));
    }

    @Test
    void reversingInternalPairRestoresBothInventoriesWithoutCash() {
        createFactoryMirror();
        confirm("PURCHASE", "10", "75000000");
        var transfer = internalTransfer();
        transaction(() -> service.reverse(transfer.id(), "Reverse internal pair"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM stock_documents WHERE reversal_of_document_id IN (?, ?)", Integer.class, transfer.id(), transfer.internalPairDocumentId()));
        assertEquals(0, new BigDecimal("10000").compareTo(jdbc.queryForObject("SELECT quantity_base FROM stock_balances WHERE product_id = ? AND category_id = ?", BigDecimal.class, product, category)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM financial_transactions", Integer.class));
    }

    @Test
    void dailyReportDoesNotInventProfitWhenCostSnapshotIsMissing() {
        confirm("PURCHASE", "10", "75000000");
        confirm("SALE", "2", "76000000");
        var finance = new FinanceService(jdbc, new DocumentSequenceService(jdbc), mock(AuditService.class), clock, shops, periods);
        var report = finance.profitReport(day.toLocalDate(), day.toLocalDate());
        assertEquals(1, report.dailyRows().size());
        assertNull(report.dailyRows().get(0).cogs());
        assertNull(report.dailyRows().get(0).grossProfit());
        assertEquals(0, new BigDecimal("152000000").compareTo(report.dailyRows().get(0).revenue()));
        jdbc.update("UPDATE stock_document_items i JOIN stock_documents d ON d.id = i.document_id SET i.standard_cost_total_snapshot = 0 WHERE d.document_type = 'SALE'");
        var zeroReference = finance.profitReport(day.toLocalDate(), day.toLocalDate());
        assertFalse(zeroReference.standardCostComplete());
        assertNull(zeroReference.standardGrossProfit());
        assertNull(zeroReference.dailyRows().get(0).standardGrossProfit());
    }

    @Test
    void successfulEditPreservesPaymentsAndOriginalConfirmationDeadline() {
        confirm("PURCHASE", "10", "75000000");
        var sale = confirm("SALE", "2", "76000000");
        var confirmedAt = sale.confirmedAt();
        var payment = new StockDtos.PaymentRequest(new BigDecimal("50000000"), "CASH", day,
                "Partial", UUID.randomUUID().toString(), "Hải", "Customer");
        transaction(() -> service.addPayment(sale.id(), payment));
        var edited = transaction(() -> service.editConfirmed(sale.id(), request("SALE", "3", "76000000")));
        assertEquals(confirmedAt, edited.confirmedAt());
        assertEquals("PARTIAL", edited.paymentStatus());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sales_payments WHERE document_id = ?", Integer.class, sale.id()));
        assertEquals(0, new BigDecimal("7000").compareTo(jdbc.queryForObject(
                "SELECT quantity_base FROM stock_balances WHERE product_id = ? AND category_id = ?", BigDecimal.class, product, category)));
    }

    @Test
    void purchaseCanIncreaseAfterSalesButCannotLeaveNegativeFinalInventory() {
        var purchase = confirm("PURCHASE", "10", "75000000");
        confirm("SALE", "2", "76000000");
        transaction(() -> service.editConfirmed(purchase.id(), request("PURCHASE", "12", "75000000")));
        assertEquals(0, new BigDecimal("10000").compareTo(jdbc.queryForObject("SELECT quantity_base FROM stock_balances WHERE product_id = ? AND category_id = ?", BigDecimal.class, product, category)));
        assertThrows(BusinessException.class, () -> transaction(() -> service.editConfirmed(purchase.id(), request("PURCHASE", "1", "75000000"))));
        assertEquals(0, new BigDecimal("10000").compareTo(jdbc.queryForObject("SELECT quantity_base FROM stock_balances WHERE product_id = ? AND category_id = ?", BigDecimal.class, product, category)));
        assertEquals(0, new BigDecimal("900000000").compareTo(service.get(purchase.id()).totalAmount()));
    }

    @Test
    void concurrentPaymentRetriesCreateOneReceipt() throws Exception {
        confirm("PURCHASE", "10", "75000000");
        var sale = confirm("SALE", "2", "76000000");
        var payment = new StockDtos.PaymentRequest(new BigDecimal("50000000"), "CASH", day, "Concurrent retry", UUID.randomUUID().toString(), "Hải", "Customer");
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        var start = new java.util.concurrent.CountDownLatch(1);
        try {
            java.util.concurrent.Callable<Void> action = () -> {
                ShopContext.set(ShopContext.DISTRIBUTION);
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        new AuthenticatedAdmin(1L, "test", false, UserRole.DISTRIBUTION, ShopContext.DISTRIBUTION), null, List.of()));
                try { start.await(); transaction(() -> service.addPayment(sale.id(), payment)); return null; }
                finally { ShopContext.clear(); SecurityContextHolder.clearContext(); }
            };
            var first = executor.submit(action);
            var second = executor.submit(action);
            start.countDown();
            first.get(15, java.util.concurrent.TimeUnit.SECONDS);
            second.get(15, java.util.concurrent.TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sales_payments WHERE document_id = ?", Integer.class, sale.id()));
        assertEquals(0, new BigDecimal("50000000").compareTo(service.get(sale.id()).paidAmount()));
    }

    @Test
    void unpaidSaleReversesExactlyOnceAndRestoresStock() {
        confirm("PURCHASE", "10", "75000000");
        var sale = confirm("SALE", "2", "76000000");
        transaction(() -> service.reverse(sale.id(), "Test reversal"));
        assertThrows(BusinessException.class, () -> transaction(() -> service.reverse(sale.id(), "Retry")));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM stock_documents WHERE reversal_of_document_id = ?", Integer.class, sale.id()));
        assertEquals(0, new BigDecimal("10000").compareTo(jdbc.queryForObject(
                "SELECT quantity_base FROM stock_balances WHERE product_id = ? AND category_id = ?", BigDecimal.class, product, category)));
    }

    @Test
    void paymentInFollowingPeriodDoesNotRewriteClosedYearDebt() {
        confirm("PURCHASE", "10", "75000000");
        var sale = confirm("SALE", "2", "76000000");
        jdbc.update("INSERT INTO year_period_drafts (shop_id, year_number, period_from, period_to) VALUES (?, 2025, '2025-02-18', '2025-02-28')", shops.currentId());
        transactions.executeWithoutResult(status -> periods.save(2025, day.toLocalDate(), java.time.LocalDate.of(2025, 2, 28), new BigDecimal("8000"), new BigDecimal("600000000"), new BigDecimal("2000000")));
        jdbc.update("UPDATE shop_month_periods SET closed_at = NOW(3) WHERE shop_id = ?", shops.currentId());
        assertThrows(BusinessException.class, () -> transaction(() -> service.editConfirmed(sale.id(), request("SALE", "3", "76000000"))));
        jdbc.update("INSERT INTO year_period_starts (shop_id, year_number, period_from, period_name) VALUES (?, 2026, '2025-03-01', 'Next fiscal year')", shops.currentId());
        transaction(() -> periods.openMonth(2026, java.time.YearMonth.of(2025, 3)));
        var payment = new StockDtos.PaymentRequest(new BigDecimal("50000000"), "CASH", LocalDateTime.of(2025, 3, 2, 10, 0),
                "Old debt", UUID.randomUUID().toString(), "Hải", "Customer");
        var paid = transaction(() -> service.addPayment(sale.id(), payment));
        assertEquals(0, new BigDecimal("50000000").compareTo(paid.paidAmount()));
        assertEquals(0, new BigDecimal("152000000").compareTo(jdbc.queryForObject("SELECT customer_debt FROM year_end_balances WHERE shop_id = ? AND year_number = 2025", BigDecimal.class, shops.currentId())));
        assertThrows(BusinessException.class, () -> transactions.executeWithoutResult(status -> periods.save(2025, day.toLocalDate(), java.time.LocalDate.of(2025, 2, 28), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO)));
    }

    @Test
    void optionalMonthlySnapshotDoesNotChangeWhenAnotherInvoiceIsAdded() throws Exception {
        confirm("PURCHASE", "10", "75000000");
        confirm("SALE", "2", "76000000");
        var book = new MonthlyBookController(jdbc, shops, periods, new tools.jackson.databind.ObjectMapper(), new MonthlyInventoryValuation(jdbc, shops));
        var input = new MonthlyBookController.Input(2025, "2025-02", List.of(new MonthlyBookController.Count(category, "Test aluminium", new BigDecimal("8000"), BigDecimal.ZERO, BigDecimal.ZERO, null)), BigDecimal.ZERO);
        var saved = transaction(() -> {
            try { return book.close(input).data(); } catch (Exception e) { throw new RuntimeException(e); }
        });
        confirm("SALE", "1", "76000000");
        assertEquals(saved, book.get(2025, "2025-02").data());
        assertThrows(BusinessException.class, () -> book.close(input));
    }

    @Test
    void paymentIdempotencyAndFailedEditPreserveStockAndDebt() {
        confirm("PURCHASE", "10", "75000000");
        var sale = confirm("SALE", "2", "76000000");
        assertEquals(0, new BigDecimal("8000").compareTo(jdbc.queryForObject(
                "SELECT quantity_base FROM stock_balances WHERE product_id = ? AND category_id = ?",
                BigDecimal.class, product, category)));
        var payment = new StockDtos.PaymentRequest(new BigDecimal("50000000"), "CASH", day,
                "Partial payment", UUID.randomUUID().toString(), "Hải", "Test customer");
        transaction(() -> service.addPayment(sale.id(), payment));
        transaction(() -> service.addPayment(sale.id(), payment));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM sales_payments WHERE document_id = ?", Integer.class, sale.id()));
        assertThrows(BusinessException.class, () -> transaction(
                () -> service.editConfirmed(sale.id(), request("SALE", "0.1", "76000000"))));
        assertEquals(0, new BigDecimal("8000").compareTo(jdbc.queryForObject(
                "SELECT quantity_base FROM stock_balances WHERE product_id = ? AND category_id = ?",
                BigDecimal.class, product, category)));
        assertEquals(0, new BigDecimal("50000000").compareTo(service.get(sale.id()).paidAmount()));
    }

    @Test
    void manufacturingSaleKeepsRawInventoryUnchanged() {
        long factory = jdbc.queryForObject("SELECT id FROM shops WHERE code = 'MANUFACTURING'", Long.class);
        jdbc.update("UPDATE product_categories SET shop_id = ? WHERE id = ?", factory, category);
        jdbc.update("UPDATE products SET shop_id = ? WHERE id = ?", factory, product);
        jdbc.update("UPDATE customers SET shop_id = ? WHERE id = ?", factory, customer);
        jdbc.update("UPDATE year_period_starts SET shop_id = ?", factory);
        jdbc.update("UPDATE shop_month_periods SET shop_id = ?", factory);
        ShopContext.set(ShopContext.MANUFACTURING);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedAdmin(1L, "test", false, UserRole.MANUFACTURING, ShopContext.MANUFACTURING), null, List.of()));
        confirm("PURCHASE", "10", "75000000");
        confirm("SALE", "2", "100000000");
        assertEquals(0, new BigDecimal("10000").compareTo(jdbc.queryForObject(
                "SELECT quantity_base FROM stock_balances WHERE product_id = ? AND category_id = ?",
                BigDecimal.class, product, category)));
    }

    @Test
    void insufficientSaleRollsBackDraftAndMovement() {
        confirm("PURCHASE", "1", "75000000");
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM stock_documents", Integer.class);
        assertThrows(BusinessException.class, () -> confirm("SALE", "2", "76000000"));
        assertEquals(before, jdbc.queryForObject("SELECT COUNT(*) FROM stock_documents", Integer.class));
        assertEquals(0, new BigDecimal("1000").compareTo(jdbc.queryForObject(
                "SELECT quantity_base FROM stock_balances WHERE product_id = ? AND category_id = ?",
                BigDecimal.class, product, category)));
    }
}
