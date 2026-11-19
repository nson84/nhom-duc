package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

import com.aluminum.manager.system.AuditService;
import com.aluminum.manager.system.SettingsService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

/** Creates only UUID-named databases on the explicitly configured loopback test server. */
class BackupBusinessMysqlIT {
    @TempDir Path directory;

    @Test void roundTripPreservesBothLedgersRelationsImagesAndHistoricalTimestamps() throws Exception {
        String url = System.getenv("ALUMINUM_TEST_DB_URL");
        assumeTrue(url != null && url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/aluminum_backup_test(?:\\?.*)?"));
        String user = System.getenv("ALUMINUM_TEST_DB_USERNAME");
        String password = System.getenv("ALUMINUM_TEST_DB_PASSWORD");
        assumeTrue(user != null && password != null);
        String sourceName = "aluminum_test_" + UUID.randomUUID().toString().replace("-", "");
        String sourceUrl = url.replace("/aluminum_backup_test", "/" + sourceName);
        sourceUrl += sourceUrl.contains("?") ? "&createDatabaseIfNotExist=true" : "?createDatabaseIfNotExist=true";
        var source = new DriverManagerDataSource(sourceUrl, user, password);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(source);
        byte[] image = new byte[] {1, 2, 3, 4};
        String imageName = UUID.nameUUIDFromBytes(image) + ".png";
        Path images = Files.createDirectory(directory.resolve("images"));
        Files.write(images.resolve(imageName), image);
        for (long shop : jdbc.queryForList("SELECT id FROM shops", Long.class)) {
            jdbc.update("INSERT INTO product_categories (id, shop_id, name) VALUES (?, ?, 'Nhôm thử')", shop + 100, shop);
            jdbc.update("INSERT INTO products (id, shop_id, product_code, name, category_id, base_unit_name, image_path, created_at, updated_at) VALUES (?, ?, ?, 'Nhôm có ảnh', ?, 'kg', ?, '2025-02-18 09:00:00.123', '2025-02-19 10:00:00.456')",
                    shop + 100, shop, "TEST-" + shop, shop + 100, "product-image:" + imageName);
            jdbc.update("INSERT INTO customers (id, shop_id, name) VALUES (?, ?, 'Khách thử')", shop + 100, shop);
            jdbc.update("INSERT INTO stock_documents (id, shop_id, customer_id, document_code, document_type, status, document_date, subtotal, total_amount, created_at) VALUES (?, ?, ?, ?, 'SALE', 'CONFIRMED', '2025-02-18', 100000000, 100000000, '2025-02-18 09:00:00.123')",
                    shop + 100, shop, shop + 100, "SALE-" + shop);
            jdbc.update("INSERT INTO stock_document_items (id, document_id, product_id, category_id, product_code_snapshot, product_name_snapshot, category_name_snapshot, unit_name_snapshot, quantity_document, factor_to_base_snapshot, quantity_base, unit_price, line_total) VALUES (?, ?, ?, ?, 'TEST', 'Nhôm thử', 'Loại thử', 'kg', 1000, 1, 1000, 100000, 100000000)",
                    shop + 100, shop + 100, shop + 100, shop + 100);
            jdbc.update("INSERT INTO financial_transactions (id, shop_id, transaction_code, transaction_date, transaction_type, category, amount, reference_type, reference_id) VALUES (?, ?, ?, '2025-02-18', 'INCOME', 'SALE_RECEIPT', 50000000, 'STOCK_DOCUMENT', ?)", shop + 100, shop, "RECEIPT-" + shop, shop + 100);
            jdbc.update("INSERT INTO sales_payments (document_id, payment_date, amount, payment_method, financial_transaction_id) VALUES (?, '2025-02-18', 50000000, 'CASH', ?)", shop + 100, shop + 100);
            jdbc.update("INSERT INTO stock_documents (id, shop_id, document_code, document_type, status, document_date, subtotal, total_amount) VALUES (?, ?, ?, 'PURCHASE', 'CONFIRMED', '2025-02-18', 750000000, 750000000)", shop + 200, shop, "PURCHASE-" + shop);
            jdbc.update("INSERT INTO stock_document_items (id, document_id, product_id, category_id, product_code_snapshot, product_name_snapshot, category_name_snapshot, unit_name_snapshot, quantity_document, factor_to_base_snapshot, quantity_base, unit_price, line_total) VALUES (?, ?, ?, ?, 'TEST', 'Nhôm thử', 'Loại thử', 'kg', 10000, 1, 10000, 75000, 750000000)", shop + 200, shop + 200, shop + 100, shop + 100);
            jdbc.update("INSERT INTO stock_movements (document_id, document_item_id, product_id, category_id, movement_type, quantity_delta_base, unit_cost, movement_at) VALUES (?, ?, ?, ?, 'IN', 10000, 75000, '2025-02-18')", shop + 200, shop + 200, shop + 100, shop + 100);
            boolean distribution = "DISTRIBUTION".equals(jdbc.queryForObject("SELECT code FROM shops WHERE id = ?", String.class, shop));
            if (distribution) {
                long saleItem = jdbc.queryForObject("SELECT id FROM stock_document_items WHERE document_id = ?", Long.class, shop + 100);
                jdbc.update("INSERT INTO stock_movements (document_id, document_item_id, product_id, category_id, movement_type, quantity_delta_base, unit_cost, movement_at) VALUES (?, ?, ?, ?, 'OUT', -1000, 75000, '2025-02-18')", shop + 100, saleItem, shop + 100, shop + 100);
            }
            jdbc.update("UPDATE stock_balances SET quantity_base = ? WHERE product_id = ? AND category_id = ?", distribution ? 9000 : 10000, shop + 100, shop + 100);
        }
        long factory = jdbc.queryForObject("SELECT id FROM shops WHERE code = 'MANUFACTURING'", Long.class);
        jdbc.update("INSERT INTO employees (id, shop_id, employee_code, full_name) VALUES (101, ?, 'TEST-WORKER', 'Thợ thử')", factory);
        jdbc.update("INSERT INTO employee_pay_rates (employee_id, effective_from, daily_rate) VALUES (101, '2025-02-01', 400000)");
        jdbc.update("INSERT INTO attendance_records (id, employee_id, work_date, attendance_status, credited_days, daily_rate_snapshot) VALUES (101, 101, '2025-02-18', 'PRESENT', 1, 400000)");
        jdbc.update("INSERT INTO overtime_records (attendance_id, overtime_type, overtime_hours, hourly_rate_snapshot, amount, is_approved) VALUES (101, 'WEEKDAY', 2, 25000, 50000, 1)");
        jdbc.update("INSERT INTO payroll_periods (id, period_year, period_month) VALUES (101, 2025, 2)");
        jdbc.update("INSERT INTO payroll_details (id, payroll_period_id, employee_id, credited_days, base_pay, overtime_pay, advance_amount, total_pay) VALUES (101, 101, 101, 1, 400000, 50000, 50000, 400000)");
        jdbc.update("INSERT INTO payroll_advance_entries (payroll_detail_id, advance_date, amount, note, idempotency_key) VALUES (101, '2025-02-18', 50000, 'Ứng thử', 'test-advance')");
        jdbc.update("INSERT INTO financial_transactions (shop_id, transaction_code, transaction_date, transaction_type, category, amount, reference_type, reference_id) VALUES (?, 'ADVANCE-TEST', '2025-02-18', 'EXPENSE', 'PAYROLL', 50000, 'PAYROLL_ADVANCE', 1)", factory);
        byte[] key = new byte[32]; java.util.Arrays.fill(key, (byte) 7);
        var recovery = mock(RecoveryKeyService.class);
        when(recovery.key()).thenReturn(key);
        var service = new BackupService(jdbc, source, mock(SettingsService.class), mock(AuditService.class),
                java.time.Clock.systemUTC(), recovery, new EncryptedBackupCodec(), mock(BackupFrequencyService.class), mock(CloudBackupService.class));
        ReflectionTestUtils.setField(service, "imageDirectory", images.toString());
        // Exercise the real serializer and encrypted container without scheduler or cloud side effects.
        String dump = ReflectionTestUtils.invokeMethod(service, "buildDatabaseDump", LocalDateTime.now());
        Path archive = directory.resolve("business.ndbak");
        new EncryptedBackupCodec().encrypt(dump.getBytes(java.nio.charset.StandardCharsets.UTF_8), key, archive);
        var result = new StagedRestoreService(new EncryptedBackupCodec(), recovery, url, user, password)
                .prepare(archive, null, user, password);
        var restored = new JdbcTemplate(new DriverManagerDataSource(StagedRestoreService.stagingUrl(url, result.databaseName()), user, password));
        for (String table : java.util.List.of("shops", "products", "product_categories", "product_units", "stock_balances", "customers", "stock_documents", "stock_document_items", "stock_movements", "financial_transactions", "sales_payments", "employees", "employee_pay_rates", "attendance_records", "overtime_records", "payroll_periods", "payroll_details", "payroll_advance_entries")) {
            assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class), restored.queryForObject("SELECT COUNT(*) FROM " + table, Long.class), table);
        }
        assertEquals(jdbc.queryForList("SELECT shop_id, SUM(total_amount) total FROM stock_documents GROUP BY shop_id ORDER BY shop_id"),
                restored.queryForList("SELECT shop_id, SUM(total_amount) total FROM stock_documents GROUP BY shop_id ORDER BY shop_id"));
        assertEquals(jdbc.queryForObject("SELECT SUM(amount) FROM sales_payments", java.math.BigDecimal.class), restored.queryForObject("SELECT SUM(amount) FROM sales_payments", java.math.BigDecimal.class));
        assertEquals(jdbc.queryForList("SELECT product_id, category_id, quantity_base FROM stock_balances ORDER BY product_id, category_id"), restored.queryForList("SELECT product_id, category_id, quantity_base FROM stock_balances ORDER BY product_id, category_id"));
        assertEquals(jdbc.queryForList("SELECT base_pay, overtime_pay, advance_amount, total_pay FROM payroll_details"), restored.queryForList("SELECT base_pay, overtime_pay, advance_amount, total_pay FROM payroll_details"));
        assertArrayEquals(image, Files.readAllBytes(Path.of(result.imageDirectory()).resolve(imageName)));
        assertEquals(jdbc.queryForList("SELECT id, created_at, updated_at FROM products ORDER BY id"),
                restored.queryForList("SELECT id, created_at, updated_at FROM products ORDER BY id"), "Restore must preserve historical timestamps, including DEFAULT_GENERATED columns");
        Path missingImage = directory.resolve("missing-image.ndbak");
        new EncryptedBackupCodec().encrypt(dump.replaceAll("(?m)^-- IMAGE .*\\r?\\n", "").getBytes(java.nio.charset.StandardCharsets.UTF_8), key, missingImage);
        assertThrows(java.io.IOException.class, () -> new StagedRestoreService(new EncryptedBackupCodec(), recovery, url, user, password)
                .prepare(missingImage, null, user, password), "An imported product must not silently reference a missing image");
        // Known recovery limitation: installation-local users are excluded, but closed payroll retains its actor FK.
        // Keep rejection explicit; do not silently null authors or restore authentication accounts.
        jdbc.update("INSERT INTO admin_users (id, username, password_hash) VALUES (101, 'test-actor', 'not-a-login-hash')");
        jdbc.update("UPDATE payroll_periods SET status = 'CLOSED', closed_at = '2025-02-28', closed_by_admin_id = 101 WHERE id = 101");
        String closedDump = ReflectionTestUtils.invokeMethod(service, "buildDatabaseDump", LocalDateTime.now());
        Path closedArchive = directory.resolve("closed-payroll.ndbak");
        new EncryptedBackupCodec().encrypt(closedDump.getBytes(java.nio.charset.StandardCharsets.UTF_8), key, closedArchive);
        var failure = assertThrows(java.io.IOException.class, () -> new StagedRestoreService(new EncryptedBackupCodec(), recovery, url, user, password)
                .prepare(closedArchive, null, user, password));
        assertTrue(failure.getCause().getMessage().contains("fk_payroll_closed_by"), "Recovery policy for local author references remains unresolved");
    }
}


