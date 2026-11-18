package com.aluminum.manager.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;

import com.aluminum.manager.auth.UserRole;
import com.aluminum.manager.catalog.PurchasePriceService;
import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.dashboard.YearEndBalanceService;
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
        var shops = new ShopAccess(jdbc);
        var clock = Clock.fixed(Instant.parse("2025-02-20T10:00:00Z"), ZoneOffset.UTC);
        var periods = new YearEndBalanceService(jdbc, shops);
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
