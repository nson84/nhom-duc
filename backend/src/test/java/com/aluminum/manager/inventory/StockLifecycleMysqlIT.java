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
import com.aluminum.manager.dashboard.InventoryCarryoverService;
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

    @Test
    void monthlyCountNeedsOnlyRawKgAndAnnualScrapCanStillBeRecordedLater() {
        confirm("PURCHASE", "1", "75000000");
        var book = new MonthlyBookController(jdbc, shops, periods, new tools.jackson.databind.ObjectMapper(), new MonthlyInventoryValuation(jdbc, shops));
        var input = new MonthlyBookController.Input(2025, "2025-02", List.of(
                new MonthlyBookController.Count(category, "Test aluminium", new BigDecimal("600"), null, null, null)), null);
        var saved = transaction(() -> book.close(input)).data();
        assertTrue(saved.closed());
        assertEquals(0, new BigDecimal("600").compareTo(saved.counts().get(0).rawKg()));
        assertEquals(0, BigDecimal.ZERO.compareTo(saved.counts().get(0).scrapKg()));
        assertNull(saved.profit(), "Do not invent profit when opening valuation is unknown");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM year_end_balances", Integer.class), "Monthly close must not close or lock annual inventory");
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM distribution_inventory_count_evidence", Integer.class), "Monthly snapshot must not claim annual count evidence");
        var scrapInput = new MonthlyBookController.Input(2025, "2025-03", List.of(
                new MonthlyBookController.Count(category, "Test aluminium", new BigDecimal("600"), new BigDecimal("10"), null, null)), null);
        var unvalued = transaction(() -> book.close(scrapInput)).data();
        assertEquals(0, new BigDecimal("10").compareTo(unvalued.counts().get(0).scrapKg()));
        assertNull(unvalued.endingValue(), "Do not carry unknown scrap value into next month as zero");
        assertNull(unvalued.profit());
        assertNull(book.get(2025, "2025-04").data().openingValue());
    }

    private long addSecondCategoryWithReceipt() {
        long originalProduct = product, originalCategory = category, originalUnit = unit;
        try {
            jdbc.update("INSERT INTO product_categories (shop_id, name) VALUES (?, 'Second aluminium')", shops.currentId());
            category = jdbc.queryForObject("SELECT id FROM product_categories WHERE shop_id = ? AND name = 'Second aluminium'", Long.class, shops.currentId());
            jdbc.update("INSERT INTO products (shop_id, product_code, name, category_id, base_unit_name) VALUES (?, 'TEST-SECOND', 'Second aluminium', ?, 'kg')", shops.currentId(), category);
            product = jdbc.queryForObject("SELECT id FROM products WHERE shop_id = ? AND product_code = 'TEST-SECOND'", Long.class, shops.currentId());
            jdbc.update("INSERT INTO product_units (product_id, unit_name, factor_to_base) VALUES (?, 'tấn', 1000)", product);
            unit = jdbc.queryForObject("SELECT id FROM product_units WHERE product_id = ? AND unit_name = 'tấn'", Long.class, product);
            confirm("PURCHASE", "0.4", "70000000");
            return category;
        } finally { product = originalProduct; category = originalCategory; unit = originalUnit; }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {ShopContext.DISTRIBUTION, ShopContext.MANUFACTURING})
    void carriesSixHundredKgByCategoryIntoNextMonthWithoutCreatingReceipts(String shop) {
        useFixtureShop(shop);
        confirm("PURCHASE", "0.2", "75000000");
        long second = addSecondCategoryWithReceipt();
        if (!shops.isDistribution()) jdbc.update("INSERT INTO payroll_periods (period_year, period_month, status) VALUES (2025, 2, 'CLOSED')");
        var book = new MonthlyBookController(jdbc, shops, periods, new tools.jackson.databind.ObjectMapper(), new MonthlyInventoryValuation(jdbc, shops));
        var counts = List.of(new MonthlyBookController.Count(category, "Test aluminium", new BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO, null),
                new MonthlyBookController.Count(second, "Second aluminium", new BigDecimal("400"), BigDecimal.ZERO, BigDecimal.ZERO, null));
        transaction(() -> book.close(new MonthlyBookController.Input(2025, "2025-02", counts, BigDecimal.ZERO)));
        int documents = jdbc.queryForObject("SELECT COUNT(*) FROM stock_documents", Integer.class);
        var carry = new InventoryCarryoverService(jdbc, shops, periods, new tools.jackson.databind.ObjectMapper());
        for (int i = 0; i < 2; i++) {
            var opening = carry.get(2025, "2025-03");
            assertTrue(opening.available());
            assertEquals(0, new BigDecimal("600").compareTo(opening.totalRawKg()));
            assertEquals(0, new BigDecimal("200").compareTo(opening.rows().stream().filter(r -> r.categoryId() == category).findFirst().orElseThrow().rawKg()));
            assertEquals(0, new BigDecimal("400").compareTo(opening.rows().stream().filter(r -> r.categoryId() == second).findFirst().orElseThrow().rawKg()));
        }
        assertEquals(documents, jdbc.queryForObject("SELECT COUNT(*) FROM stock_documents", Integer.class));
        assertFalse(carry.get(2025, "2025-04").available(), "Do not silently reuse February counts as April opening when March was not closed");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {ShopContext.DISTRIBUTION, ShopContext.MANUFACTURING})
    void carriesSixHundredKgByCategoryIntoNextFiscalYear(String shop) {
        useFixtureShop(shop);
        confirm("PURCHASE", "0.2", "75000000");
        long second = addSecondCategoryWithReceipt();
        if (!shops.isDistribution()) {
            jdbc.update("INSERT INTO factory_raw_counts (year_number, category_id, quantity_kg, value_amount) VALUES (2025, ?, 200, 15000000), (2025, ?, 400, 28000000)", category, second);
            jdbc.update("INSERT INTO payroll_periods (period_year, period_month, status) VALUES (2025, 2, 'CLOSED')");
        }
        var book = new MonthlyBookController(jdbc, shops, periods, new tools.jackson.databind.ObjectMapper(), new MonthlyInventoryValuation(jdbc, shops));
        var counts = List.of(new MonthlyBookController.Count(category, "Test aluminium", new BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO, null),
                new MonthlyBookController.Count(second, "Second aluminium", new BigDecimal("400"), BigDecimal.ZERO, BigDecimal.ZERO, null));
        transaction(() -> book.close(new MonthlyBookController.Input(2025, "2025-02", counts, BigDecimal.ZERO)));
        jdbc.update("INSERT INTO year_period_drafts (shop_id, year_number, period_from, period_to) VALUES (?, 2025, '2025-02-18', '2025-02-28')", shops.currentId());
        transactions.executeWithoutResult(status -> periods.save(2025, day.toLocalDate(), java.time.LocalDate.of(2025, 2, 28), new BigDecimal("600"), new BigDecimal("43000000"), BigDecimal.ZERO));
        jdbc.update("INSERT INTO year_period_starts (shop_id, year_number, period_from, period_name) VALUES (?, 2026, '2025-03-01', 'Next fiscal year')", shops.currentId());
        var carry = new InventoryCarryoverService(jdbc, shops, periods, new tools.jackson.databind.ObjectMapper());
        var opening = carry.get(2026, null);
        assertTrue(opening.available());
        assertEquals("YEAR", opening.source());
        assertEquals(2, opening.rows().size());
        assertEquals(0, new BigDecimal("600").compareTo(opening.totalRawKg()));
        assertEquals(0, new BigDecimal("200").compareTo(opening.rows().stream().filter(r -> r.categoryId() == category).findFirst().orElseThrow().rawKg()));
        assertEquals(0, new BigDecimal("400").compareTo(opening.rows().stream().filter(r -> r.categoryId() == second).findFirst().orElseThrow().rawKg()));
        assertEquals(0, new BigDecimal("43000000").compareTo(book.get(2026, "2025-03").data().openingValue()));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM stock_documents", Integer.class), "Annual and monthly closes must not create duplicate opening receipts");
    }

    private void useFixtureShop(String shop) {
        if (ShopContext.DISTRIBUTION.equals(shop)) return;
        long factory = shops.idOf(shop);
        jdbc.update("UPDATE product_categories SET shop_id = ? WHERE id = ?", factory, category);
        jdbc.update("UPDATE products SET shop_id = ? WHERE id = ?", factory, product);
        jdbc.update("UPDATE customers SET shop_id = ? WHERE id = ?", factory, customer);
        jdbc.update("UPDATE year_period_starts SET shop_id = ?", factory);
        jdbc.update("UPDATE shop_month_periods SET shop_id = ?", factory);
        ShopContext.set(shop);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedAdmin(1L, "test", false, UserRole.MANUFACTURING, shop), null, List.of()));
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

    @Test
    void fullPaymentRetryDoesNotCreateCashOrMovementsTwice() {
        confirm("PURCHASE", "10", "75000000");
        var sale = confirm("SALE", "2", "76000000");
        var first = new StockDtos.PaymentRequest(new BigDecimal("50000000"), "CASH", day,
                "Partial", UUID.randomUUID().toString(), "Hải", "Customer");
        var rest = new StockDtos.PaymentRequest(new BigDecimal("102000000"), "CASH", day,
                "Balance", UUID.randomUUID().toString(), "Thuấn", "Customer");
        transaction(() -> service.addPayment(sale.id(), first));
        transaction(() -> service.addPayment(sale.id(), rest));
        transaction(() -> service.addPayment(sale.id(), rest));
        assertEquals("PAID", service.get(sale.id()).paymentStatus());
        assertEquals(0, new BigDecimal("152000000").compareTo(service.get(sale.id()).paidAmount()));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM sales_payments WHERE document_id = ?", Integer.class, sale.id()));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM financial_transactions WHERE reference_id = ? AND category = 'SALE_RECEIPT'", Integer.class, sale.id()));
    }

    @Test
    void decemberCarryoverKeepsSnapshotNamesInactiveCategoriesAndShopIsolation() {
        confirm("PURCHASE", "1", "75000000");
        long second = addSecondCategoryWithReceipt();
        var book = new MonthlyBookController(jdbc, shops, periods, new tools.jackson.databind.ObjectMapper(), new MonthlyInventoryValuation(jdbc, shops));
        var input = new MonthlyBookController.Input(2025, "2025-12", List.of(
                new MonthlyBookController.Count(category, "Original name", new BigDecimal("200"), null, null, null),
                new MonthlyBookController.Count(second, "Second name", new BigDecimal("400"), null, null, null)), null);
        transaction(() -> book.close(input));
        jdbc.update("UPDATE product_categories SET name = 'Renamed inactive', is_active = 0 WHERE id = ?", category);
        var carryover = new InventoryCarryoverService(jdbc, shops, periods, new tools.jackson.databind.ObjectMapper());
        var opening = carryover.get(2025, "2026-01");
        assertTrue(opening.available());
        assertEquals("MONTH", opening.source());
        assertEquals(0, new BigDecimal("600").compareTo(opening.totalRawKg()));
        assertTrue(opening.rows().stream().anyMatch(r -> r.categoryId() == category && r.category().equals("Test aluminium")), "Snapshot uses the saved catalogue name, not the client-supplied label");
        assertFalse(carryover.get(2025, "2026-02").available(), "No adjacent close means no inferred opening");
        ShopContext.set(ShopContext.MANUFACTURING);
        try { assertFalse(carryover.get(2025, "2026-01").available(), "Never reuse another shop's snapshot"); }
        finally { ShopContext.set(ShopContext.DISTRIBUTION); }
    }

    @Test
    void multilineSaleCanReorderChangeQuantityAndRejectUnavailableCategoryAtomically() {
        confirm("PURCHASE", "10", "75000000");
        long second = addSecondCategoryWithReceipt();
        long otherProduct = jdbc.queryForObject("SELECT id FROM products WHERE product_code = 'TEST-SECOND'", Long.class);
        long otherUnit = jdbc.queryForObject("SELECT id FROM product_units WHERE product_id = ? AND unit_name = 'tấn'", Long.class, otherProduct);
        var a = request("SALE", "2", "76000000").items().get(0);
        var b = new StockDtos.ItemRequest(otherProduct, second, otherUnit, new BigDecimal("0.1"), new BigDecimal("71000000"), BigDecimal.ZERO, "kg", null, null, null, null);
        var input = new StockDtos.DocumentRequest("SALE", day, "Customer", customer, "Hải", "Customer", "Two lines", BigDecimal.ZERO, "CASH", null, UUID.randomUUID().toString(), List.of(a, b), false);
        var sale = transaction(() -> service.confirm(service.createDraft(input).id()));
        var larger = request("SALE", "3", "76000000").items().get(0);
        var reordered = new StockDtos.DocumentRequest("SALE", day, "Customer", customer, "Hải", "Customer", "Reordered", BigDecimal.ZERO, "CASH", null, UUID.randomUUID().toString(), List.of(b, larger), false);
        transaction(() -> service.editConfirmed(sale.id(), reordered));
        assertEquals(0, new BigDecimal("7000").compareTo(jdbc.queryForObject("SELECT quantity_base FROM stock_balances WHERE product_id = ? AND category_id = ?", BigDecimal.class, product, category)));
        assertEquals(0, new BigDecimal("300").compareTo(jdbc.queryForObject("SELECT quantity_base FROM stock_balances WHERE product_id = ? AND category_id = ?", BigDecimal.class, otherProduct, second)));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE document_id = ?", Integer.class, sale.id()));
        var before = jdbc.queryForList("SELECT * FROM stock_balances ORDER BY product_id, category_id");
        var unavailable = new StockDtos.ItemRequest(product, second, unit, new BigDecimal("3"), new BigDecimal("76000000"), BigDecimal.ZERO, "kg", null, null, null, null);
        var changed = new StockDtos.DocumentRequest("SALE", day, "Customer", customer, "Hải", "Customer", "Unavailable", BigDecimal.ZERO, "CASH", null, UUID.randomUUID().toString(), List.of(unavailable, b), false);
        assertThrows(BusinessException.class, () -> transaction(() -> service.editConfirmed(sale.id(), changed)));
        assertEquals(before, jdbc.queryForList("SELECT * FROM stock_balances ORDER BY product_id, category_id"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM stock_movements WHERE document_id = ?", Integer.class, sale.id()));
    }

    @Test
    void lockedMonthRejectsCreateEditCancelConfirmAndReverseWithoutChangingData() {
        confirm("PURCHASE", "10", "75000000");
        var sale = confirm("SALE", "2", "76000000");
        var draft = transaction(() -> service.createDraft(request("SALE", "1", "76000000")));
        var before = jdbc.queryForList("SELECT * FROM stock_documents ORDER BY id");
        var stock = jdbc.queryForList("SELECT * FROM stock_balances ORDER BY product_id, category_id");
        jdbc.update("UPDATE shop_month_periods SET closed_at = NOW(3) WHERE shop_id = ?", shops.currentId());
        assertThrows(BusinessException.class, () -> transaction(() -> service.createDraft(request("SALE", "1", "76000000"))));
        assertThrows(BusinessException.class, () -> transaction(() -> service.editConfirmed(sale.id(), request("SALE", "1", "76000000"))));
        assertThrows(BusinessException.class, () -> transaction(() -> service.cancelDraft(draft.id())));
        assertThrows(BusinessException.class, () -> transaction(() -> service.confirm(draft.id())));
        assertThrows(BusinessException.class, () -> transaction(() -> service.reverse(sale.id(), "Locked month")));
        assertEquals(before, jdbc.queryForList("SELECT * FROM stock_documents ORDER BY id"));
        assertEquals(stock, jdbc.queryForList("SELECT * FROM stock_balances ORDER BY product_id, category_id"));
    }

    @Test
    void factoryPayrollCountsApprovedOvertimeAndAdvanceOnceThenLocks() {
        useFixtureShop(ShopContext.MANUFACTURING);
        jdbc.update("INSERT INTO admin_users (id, username, password_hash) VALUES (1, 'test', 'not-a-login-hash')");
        jdbc.update("INSERT INTO employees (id, shop_id, employee_code, full_name) VALUES (101, ?, 'TEST-EMP', 'Thợ thử')", shops.currentId());
        jdbc.update("INSERT INTO employee_pay_rates (employee_id, effective_from, daily_rate) VALUES (101, '2025-02-01', 400000)");
        jdbc.update("INSERT INTO attendance_records (id, employee_id, work_date, attendance_status, credited_days, daily_rate_snapshot) VALUES (101, 101, '2025-02-18', 'PRESENT', 1, 400000)");
        jdbc.update("INSERT INTO overtime_records (attendance_id, overtime_type, overtime_hours, hourly_rate_snapshot, amount, is_approved) VALUES (101, 'WEEKDAY', 2, 25000, 50000, 1), (101, 'WEEKDAY', 1, 25000, 25000, 0)");
        var attendance = new com.aluminum.manager.attendance.AttendanceService(jdbc, mock(AuditService.class), new DocumentSequenceService(jdbc), clock, shops, periods);
        var payroll = transaction(() -> attendance.createOrGetPeriod(new com.aluminum.manager.attendance.AttendanceDtos.PayrollPeriodRequest(2025, 2, "Test")));
        long periodId = payroll.id();
        var calculated = transaction(() -> attendance.calculatePayroll(periodId));
        assertEquals(0, new BigDecimal("450000").compareTo(calculated.grandTotalPay()));
        assertEquals(0, new BigDecimal("50000").compareTo(calculated.totalOvertimePay()));
        long detail = calculated.details().get(0).id();
        var advance = new com.aluminum.manager.attendance.AttendanceDtos.PayrollAdvanceRequest(day.toLocalDate(), new BigDecimal("50000"), "Ứng thử", UUID.randomUUID().toString());
        transaction(() -> attendance.addPayrollAdvance(periodId, detail, advance));
        transaction(() -> attendance.addPayrollAdvance(periodId, detail, advance));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM payroll_advance_entries", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM financial_transactions WHERE reference_type = 'PAYROLL_ADVANCE'", Integer.class));
        assertThrows(BusinessException.class, () -> transaction(() -> attendance.addPayrollAdvance(periodId, detail,
                new com.aluminum.manager.attendance.AttendanceDtos.PayrollAdvanceRequest(day.toLocalDate(), new BigDecimal("500000"), "Too much", UUID.randomUUID().toString()))));
        var closed = transaction(() -> attendance.closePeriod(periodId));
        assertEquals("CLOSED", closed.status());
        assertEquals(0, new BigDecimal("400000").compareTo(closed.grandTotalPay()));
        assertThrows(BusinessException.class, () -> transaction(() -> attendance.calculatePayroll(periodId)));
        assertThrows(BusinessException.class, () -> transaction(() -> attendance.addPayrollAdvance(periodId, detail, advance)));
        var payout = new com.aluminum.manager.attendance.AttendanceDtos.PayPayrollRequest(new BigDecimal("400000"), "Trả phần còn lại", UUID.randomUUID().toString());
        transaction(() -> attendance.payPeriod(periodId, payout));
        transaction(() -> attendance.payPeriod(periodId, payout));
        assertEquals(0, new BigDecimal("400000").compareTo(attendance.getPayrollPeriod(periodId).paidAmount()));
        assertEquals(0, new BigDecimal("450000").compareTo(jdbc.queryForObject("SELECT SUM(amount) FROM financial_transactions WHERE category = 'PAYROLL' AND status = 'CONFIRMED'", BigDecimal.class)));
        var book = new MonthlyBookController(jdbc, shops, periods, new tools.jackson.databind.ObjectMapper(), new MonthlyInventoryValuation(jdbc, shops));
        assertEquals(0, new BigDecimal("450000").compareTo(book.get(2025, "2025-02").data().expenses()), "Advance and payout must not be added again to accrued payroll cost");
    }

    @Test
    void monthCannotCloseBetweenWriteValidationAndDraftInsert() throws Exception {
        var checked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var guarded = org.mockito.Mockito.spy(periods);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.callRealMethod();
            checked.countDown();
            assertTrue(release.await(5, java.util.concurrent.TimeUnit.SECONDS));
            return null;
        }).when(guarded).requireWorkingDate(org.mockito.ArgumentMatchers.any());
        var writer = new StockDocumentService(jdbc, new DocumentSequenceService(jdbc), mock(AuditService.class),
                new PurchasePriceService(jdbc, clock), clock, shops, guarded);
        var executor = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var writing = executor.submit(() -> withDistributionContext(() -> transaction(() -> writer.createDraft(request("PURCHASE", "1", "75000000")))));
            assertTrue(checked.await(5, java.util.concurrent.TimeUnit.SECONDS));
            var closing = executor.submit(() -> withDistributionContext(() -> transaction(() -> periods.closeMonth(2025, java.time.YearMonth.of(2025, 2)))));
            // A close must wait for the in-flight writer; otherwise both can commit using stale validation.
            assertThrows(java.util.concurrent.TimeoutException.class, () -> closing.get(500, java.util.concurrent.TimeUnit.MILLISECONDS));
            release.countDown();
            writing.get(5, java.util.concurrent.TimeUnit.SECONDS);
            var failed = assertThrows(java.util.concurrent.ExecutionException.class, () -> closing.get(5, java.util.concurrent.TimeUnit.SECONDS));
            assertInstanceOf(BusinessException.class, failed.getCause());
            assertEquals("OPEN", periods.monthStatus(java.time.YearMonth.of(2025, 2)));
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void octoberInvoiceNovemberCollectionKeepsCashDatesAndClosedDebtDistinct() {
        clock = Clock.fixed(Instant.parse("2025-11-11T10:00:00Z"), ZoneOffset.UTC);
        service = new StockDocumentService(jdbc, new DocumentSequenceService(jdbc), mock(AuditService.class), new PurchasePriceService(jdbc, clock), clock, shops, periods);
        confirm("PURCHASE", "10", "75000000");
        transaction(() -> periods.closeMonth(2025, java.time.YearMonth.of(2025, 2)));
        for (int month = 3; month < 10; month++) {
            var next = java.time.YearMonth.of(2025, month);
            transaction(() -> periods.openMonth(2025, next));
            transaction(() -> periods.closeMonth(2025, next));
        }
        transaction(() -> periods.openMonth(2025, java.time.YearMonth.of(2025, 10)));
        var october = LocalDateTime.of(2025, 10, 30, 10, 0);
        var base = request("SALE", "2", "76000000");
        var input = new StockDtos.DocumentRequest(base.documentType(), october, base.counterpartyName(), customer, "Hải", "Customer", null, BigDecimal.ZERO, "CASH", null, UUID.randomUUID().toString(), base.items(), false);
        var sale = transaction(() -> service.confirm(service.createDraft(input).id()));
        transaction(() -> service.addPayment(sale.id(), new StockDtos.PaymentRequest(new BigDecimal("50000000"), "CASH", october, null, UUID.randomUUID().toString(), "Hải", "Customer")));
        transaction(() -> periods.closeMonth(2025, java.time.YearMonth.of(2025, 10)));
        jdbc.update("INSERT INTO year_period_drafts (shop_id, year_number, period_from, period_to) VALUES (?, 2025, '2025-02-18', '2025-10-31')", shops.currentId());
        transactions.executeWithoutResult(status -> periods.save(2025, day.toLocalDate(), java.time.LocalDate.of(2025, 10, 31), new BigDecimal("8000"), new BigDecimal("600000000"), BigDecimal.ZERO));
        jdbc.update("INSERT INTO year_period_starts (shop_id, year_number, period_from, period_name) VALUES (?, 2026, '2025-11-01', 'Next')", shops.currentId());
        transaction(() -> periods.openMonth(2026, java.time.YearMonth.of(2025, 11)));
        transaction(() -> service.addPayment(sale.id(), new StockDtos.PaymentRequest(new BigDecimal("102000000"), "CASH", LocalDateTime.of(2025, 11, 10, 10, 0), null, UUID.randomUUID().toString(), "Thuấn", "Customer")));
        var finance = new FinanceService(jdbc, new DocumentSequenceService(jdbc), mock(AuditService.class), clock, shops, periods);
        var report = finance.profitReport(java.time.LocalDate.of(2025, 10, 1), java.time.LocalDate.of(2025, 10, 31));
        assertEquals(0, new BigDecimal("152000000").compareTo(report.revenue()));
        assertEquals(0, new BigDecimal("152000000").compareTo(report.cashCollected()), "This metric collects payments from invoices in the selected period, through today");
        assertEquals(0, new BigDecimal("50000000").compareTo(jdbc.queryForObject("SELECT SUM(amount) FROM financial_transactions WHERE category = 'SALE_RECEIPT' AND transaction_date >= '2025-10-01' AND transaction_date < '2025-11-01'", BigDecimal.class)));
        assertEquals(0, new BigDecimal("102000000").compareTo(jdbc.queryForObject("SELECT SUM(amount) FROM financial_transactions WHERE category = 'SALE_RECEIPT' AND transaction_date >= '2025-11-01' AND transaction_date < '2025-12-01'", BigDecimal.class)));
        assertEquals(0, new BigDecimal("102000000").compareTo(periods.get(2025).customerDebt()));
        assertEquals("PAID", service.get(sale.id()).paymentStatus());
    }

    private <T> T withDistributionContext(Supplier<T> action) {
        ShopContext.set(ShopContext.DISTRIBUTION);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new AuthenticatedAdmin(1L, "test", false, UserRole.DISTRIBUTION, ShopContext.DISTRIBUTION), null, List.of()));
        try { return action.get(); }
        finally { ShopContext.clear(); SecurityContextHolder.clearContext(); }
    }
}
