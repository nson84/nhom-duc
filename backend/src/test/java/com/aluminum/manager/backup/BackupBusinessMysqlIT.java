package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

import com.aluminum.manager.system.AuditService;
import com.aluminum.manager.system.SettingsService;
import com.aluminum.manager.system.ShopSettings;
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
    @org.junit.jupiter.api.AfterEach void clearAuthentication() { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }

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
        assertEquals(java.util.List.of("attendance_day_locks.locked_by_admin_id", "customer_leads.contacted_by_admin_id", "payroll_periods.closed_by_admin_id", "payroll_periods.reopened_by_admin_id"),
                jdbc.queryForList("SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME) FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME = 'admin_users' AND TABLE_NAME <> 'audit_logs' ORDER BY TABLE_NAME, COLUMN_NAME", String.class));
        byte[] image = java.util.Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aO7sAAAAASUVORK5CYII=");
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
        long distribution = jdbc.queryForObject("SELECT id FROM shops WHERE code = 'DISTRIBUTION'", Long.class);
        jdbc.update("UPDATE stock_documents SET payment_status = 'PARTIAL' WHERE id IN (?, ?)", factory + 100, distribution + 100);
        jdbc.update("INSERT INTO financial_transactions (id, shop_id, transaction_code, transaction_date, transaction_type, category, amount, reference_type, reference_id) VALUES (995, ?, 'FULL-PAYMENT', '2025-02-20', 'INCOME', 'SALE_RECEIPT', 50000000, 'STOCK_DOCUMENT', ?)", distribution, distribution + 100);
        jdbc.update("INSERT INTO sales_payments (document_id, payment_date, amount, payment_method, financial_transaction_id) VALUES (?, '2025-02-20', 50000000, 'CASH', 995)", distribution + 100);
        jdbc.update("UPDATE stock_documents SET payment_status = 'PAID' WHERE id = ?", distribution + 100);
        jdbc.update("INSERT INTO stock_documents (id, shop_id, document_code, document_type, status, document_date, subtotal, total_amount, payment_status, is_internal) VALUES (801, ?, 'INTERNAL-OUT', 'SALE', 'CONFIRMED', '2025-02-19', 75000000, 75000000, 'PAID', 1)", distribution);
        jdbc.update("INSERT INTO stock_documents (id, shop_id, document_code, document_type, status, document_date, subtotal, total_amount, payment_status, is_internal, internal_pair_document_id) VALUES (802, ?, 'INTERNAL-IN', 'PURCHASE', 'CONFIRMED', '2025-02-19', 75000000, 75000000, 'PAID', 1, 801)", factory);
        jdbc.update("UPDATE stock_documents SET internal_pair_document_id = 802 WHERE id = 801");
        for (long shop : new long[]{distribution, factory}) {
            long id = shop == distribution ? 801 : 802;
            jdbc.update("INSERT INTO stock_document_items (id, document_id, product_id, category_id, product_code_snapshot, product_name_snapshot, category_name_snapshot, unit_name_snapshot, quantity_document, factor_to_base_snapshot, quantity_base, unit_price, line_total) VALUES (?, ?, ?, ?, 'TEST', 'Nhôm thử', 'Nhôm thử', 'kg', 1000, 1, 1000, 75000, 75000000)", id, id, shop + 100, shop + 100);
            jdbc.update("INSERT INTO stock_movements (document_id, document_item_id, product_id, category_id, movement_type, quantity_delta_base, unit_cost, movement_at) VALUES (?, ?, ?, ?, ?, ?, 75000, '2025-02-19')", id, id, shop + 100, shop + 100, shop == distribution ? "OUT" : "IN", shop == distribution ? -1000 : 1000);
            jdbc.update("UPDATE stock_balances SET quantity_base = quantity_base + ? WHERE product_id = ? AND category_id = ?", shop == distribution ? -1000 : 1000, shop + 100, shop + 100);
        }
        jdbc.update("INSERT INTO employees (id, shop_id, employee_code, full_name) VALUES (101, ?, 'TEST-WORKER', 'Thợ thử')", factory);
        jdbc.update("INSERT INTO employee_pay_rates (employee_id, effective_from, daily_rate) VALUES (101, '2025-02-01', 400000)");
        jdbc.update("INSERT INTO attendance_records (id, employee_id, work_date, attendance_status, credited_days, daily_rate_snapshot) VALUES (101, 101, '2025-02-18', 'PRESENT', 1, 400000)");
        jdbc.update("INSERT INTO overtime_records (attendance_id, overtime_type, overtime_hours, hourly_rate_snapshot, amount, is_approved) VALUES (101, 'WEEKDAY', 2, 25000, 50000, 1)");
        jdbc.update("INSERT INTO payroll_periods (id, period_year, period_month) VALUES (101, 2025, 2)");
        jdbc.update("INSERT INTO payroll_details (id, payroll_period_id, employee_id, credited_days, base_pay, overtime_pay, advance_amount, total_pay) VALUES (101, 101, 101, 1, 400000, 50000, 50000, 400000)");
        jdbc.update("INSERT INTO payroll_advance_entries (payroll_detail_id, advance_date, amount, note, idempotency_key) VALUES (101, '2025-02-18', 50000, 'Ứng thử', 'test-advance')");
        jdbc.update("INSERT INTO financial_transactions (shop_id, transaction_code, transaction_date, transaction_type, category, amount, reference_type, reference_id) VALUES (?, 'ADVANCE-TEST', '2025-02-18', 'EXPENSE', 'PAYROLL', 50000, 'PAYROLL_ADVANCE', 1)", factory);
        jdbc.update("INSERT INTO financial_transactions (shop_id, transaction_code, transaction_date, transaction_type, category, amount, reference_type, reference_id) VALUES (?, 'PAYROLL-TEST', '2025-02-28', 'EXPENSE', 'PAYROLL', 400000, 'PAYROLL_PERIOD', 101)", factory);
        jdbc.update("INSERT INTO factory_year_counts (year_number, period_from, period_to, raw_kg, raw_value, status, closed_at, closed_payroll_cost) VALUES (2025, '2025-02-18', '2025-02-28', 9990, 749250000, 'CLOSED', '2025-02-28 18:00:00', 450000)");
        jdbc.update("INSERT INTO factory_raw_counts (year_number, category_id, quantity_kg, value_amount) VALUES (2025, ?, 9990, 749250000)", factory + 100);
        jdbc.update("INSERT INTO factory_raw_count_layers (year_number, category_id, purchase_item_id, quantity_kg, unit_cost_per_kg, value_amount) VALUES (2025, ?, ?, 8990, 75000, 674250000)", factory + 100, factory + 200);
        jdbc.update("INSERT INTO factory_raw_count_layers (year_number, category_id, purchase_item_id, quantity_kg, unit_cost_per_kg, value_amount) VALUES (2025, ?, 802, 1000, 75000, 75000000)", factory + 100);
        jdbc.update("INSERT INTO factory_scrap_sales (id, shop_id, sale_date, buyer_name, seller_name, quantity_kg, unit_price, total_amount) VALUES (901, ?, '2025-02-20', 'Khách phế liệu', 'Hải', 10, 10000, 100000)", factory);
        jdbc.update("INSERT INTO financial_transactions (id, shop_id, transaction_code, transaction_date, transaction_type, category, amount, reference_type, reference_id) VALUES (991, ?, 'SCRAP-TEST', '2025-02-20', 'INCOME', 'OTHER_INCOME', 100000, 'FACTORY_SCRAP_SALE', 901)", factory);
        jdbc.update("INSERT INTO factory_scrap_payments (scrap_sale_id, payment_date, amount, payment_method, collected_by, financial_transaction_id) VALUES (901, '2025-02-20', 100000, 'CASH', 'Thuấn', 991)");
        for (long shop : jdbc.queryForList("SELECT id FROM shops", Long.class)) {
            java.math.BigDecimal raw = new java.math.BigDecimal(shop == factory ? "9990" : "8000");
            java.math.BigDecimal rawValue = raw.multiply(new java.math.BigDecimal("75000"));
            jdbc.update("INSERT INTO product_categories (shop_id, name) VALUES (?, 'Loại thứ hai')", shop);
            jdbc.update("INSERT INTO year_end_balances (shop_id, year_number, period_from, period_to, inventory_kg, inventory_value, customer_debt, supplier_debt, profit, closed_at) VALUES (?, 2025, '2025-02-18', '2025-02-28', ?, ?, ?, 750000000, ?, '2025-02-28 18:00:00')", shop, raw, rawValue, shop == factory ? 50000000 : 0, shop == factory ? 23900000 : 25000000);
            jdbc.update("INSERT INTO year_end_inventory_categories (shop_id, year_number, category_id, category_name, raw_kg) VALUES (?, 2025, ?, 'Nhôm thử', ?)", shop, shop + 100, raw);
            var snapshot = new com.aluminum.manager.dashboard.MonthlyBookController.Snapshot(2025, "2025-02", java.time.LocalDate.of(2025, 2, 18), java.time.LocalDate.of(2025, 2, 28), java.util.List.of(new com.aluminum.manager.dashboard.MonthlyBookController.Count(shop + 100, "Nhôm thử", raw, java.math.BigDecimal.ZERO, null, rawValue)), new java.math.BigDecimal("100000000"), new java.math.BigDecimal(shop == factory ? "825000000" : "750000000"), new java.math.BigDecimal(shop == factory ? "450000" : "0"), true, java.math.BigDecimal.ZERO, rawValue, new java.math.BigDecimal(shop == factory ? "0" : "75000000"), new java.math.BigDecimal(shop == factory ? "23800000" : "25000000"));
            jdbc.update("INSERT INTO monthly_books (shop_id, month_start, period_year, snapshot_json, closed_at) VALUES (?, '2025-02-01', 2025, ?, '2025-02-28 18:00:00')", shop, new tools.jackson.databind.ObjectMapper().writeValueAsString(snapshot));
        }
        byte[] key = new byte[32]; java.util.Arrays.fill(key, (byte) 7);
        var recovery = mock(RecoveryKeyService.class);
        when(recovery.key()).thenReturn(key);
        var settings = mock(SettingsService.class);
        when(settings.get()).thenReturn(new ShopSettings("Test", "", "", "23:00", 30, directory.resolve("backups").toString()));
        var service = new BackupService(jdbc, source, settings, mock(AuditService.class),
                java.time.Clock.systemUTC(), recovery, new EncryptedBackupCodec(), mock(BackupFrequencyService.class), mock(CloudBackupService.class));
        ReflectionTestUtils.setField(service, "imageDirectory", images.toString());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        new com.aluminum.manager.security.AuthenticatedAdmin(1000L, "test", false, com.aluminum.manager.auth.UserRole.ADMIN, null), null, java.util.List.of()));
        var backup = service.createManualBackup();
        assertEquals("SUCCESS", backup.status());
        Path archive = service.verifiedArchive(backup.id());
        assertEquals(backup.checksumSha256(), java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))));
        var plain = new java.io.ByteArrayOutputStream();
        new EncryptedBackupCodec().decrypt(archive, key, plain);
        String dump = plain.toString(java.nio.charset.StandardCharsets.UTF_8);
        byte[] wrongKey = new byte[32]; java.util.Arrays.fill(wrongKey, (byte) 8);
        assertThrows(java.io.IOException.class, () -> new EncryptedBackupCodec().decrypt(archive, wrongKey, java.io.OutputStream.nullOutputStream()));
        Path truncated = directory.resolve("truncated.ndbak");
        byte[] validArchive = Files.readAllBytes(archive);
        Files.write(truncated, java.util.Arrays.copyOf(validArchive, validArchive.length / 2));
        assertThrows(java.io.IOException.class, () -> new EncryptedBackupCodec().decrypt(truncated, key, java.io.OutputStream.nullOutputStream()));
        var result = new StagedRestoreService(new EncryptedBackupCodec(), recovery, url, user, password)
                .prepare(archive, null, user, password);
        var restored = new JdbcTemplate(new DriverManagerDataSource(StagedRestoreService.stagingUrl(url, result.databaseName()), user, password));
        for (String table : java.util.List.of("shops", "products", "product_categories", "product_units", "stock_balances", "customers", "stock_documents", "stock_document_items", "stock_movements", "financial_transactions", "sales_payments", "employees", "employee_pay_rates", "attendance_records", "overtime_records", "payroll_periods", "payroll_details", "payroll_advance_entries")) {
            assertEquals(jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class), restored.queryForObject("SELECT COUNT(*) FROM " + table, Long.class), table);
            assertEquals(jdbc.queryForList("SELECT * FROM " + table).stream().map(Object::toString).sorted().toList(), restored.queryForList("SELECT * FROM " + table).stream().map(Object::toString).sorted().toList(), table + " complete rows");
        }
        assertEquals(jdbc.queryForList("SELECT shop_id, SUM(total_amount) total FROM stock_documents GROUP BY shop_id ORDER BY shop_id"),
                restored.queryForList("SELECT shop_id, SUM(total_amount) total FROM stock_documents GROUP BY shop_id ORDER BY shop_id"));
        assertEquals(jdbc.queryForObject("SELECT SUM(amount) FROM sales_payments", java.math.BigDecimal.class), restored.queryForObject("SELECT SUM(amount) FROM sales_payments", java.math.BigDecimal.class));
        assertEquals(jdbc.queryForList("SELECT product_id, category_id, quantity_base FROM stock_balances ORDER BY product_id, category_id"), restored.queryForList("SELECT product_id, category_id, quantity_base FROM stock_balances ORDER BY product_id, category_id"));
        assertEquals(jdbc.queryForList("SELECT base_pay, overtime_pay, advance_amount, total_pay FROM payroll_details"), restored.queryForList("SELECT base_pay, overtime_pay, advance_amount, total_pay FROM payroll_details"));
        for (String table : java.util.List.of("monthly_books", "year_end_balances", "year_end_inventory_categories", "factory_year_counts", "factory_raw_counts", "factory_raw_count_layers", "factory_scrap_sales", "factory_scrap_payments")) {
            assertEquals(jdbc.queryForList("SELECT * FROM " + table).stream().map(Object::toString).sorted().toList(), restored.queryForList("SELECT * FROM " + table).stream().map(Object::toString).sorted().toList(), table + " snapshots and historical values must remain unchanged");
        }
        assertArrayEquals(image, Files.readAllBytes(Path.of(result.imageDirectory()).resolve(imageName)));
        assertEquals(jdbc.queryForList("SELECT id, created_at, updated_at FROM products ORDER BY id"),
                restored.queryForList("SELECT id, created_at, updated_at FROM products ORDER BY id"), "Restore must preserve historical timestamps, including DEFAULT_GENERATED columns");
        Path missingImage = directory.resolve("missing-image.ndbak");
        new EncryptedBackupCodec().encrypt(dump.replaceAll("(?m)^-- IMAGE .*\\r?\\n", "").getBytes(java.nio.charset.StandardCharsets.UTF_8), key, missingImage);
        assertThrows(java.io.IOException.class, () -> new StagedRestoreService(new EncryptedBackupCodec(), recovery, url, user, password)
                .prepare(missingImage, null, user, password), "An imported product must not silently reference a missing image");
        // Historical actors must survive without restoring login credentials or binding to a new owner.
        jdbc.update("INSERT INTO admin_users (id, username, password_hash) VALUES (101, 'test-actor', 'not-a-login-hash')");
        jdbc.update("UPDATE payroll_periods SET status = 'CLOSED', closed_at = '2025-02-28', closed_by_admin_id = 101 WHERE id = 101");
        jdbc.update("UPDATE payroll_periods SET reopened_by_admin_id = 101, reopened_at = '2025-02-27' WHERE id = 101");
        jdbc.update("INSERT INTO attendance_day_locks (work_date, locked_at, locked_by_admin_id) VALUES ('2025-02-18', '2025-02-18 18:00:00', 101)");
        jdbc.update("INSERT INTO customer_leads (full_name, phone, contacted_by_admin_id) VALUES ('Khách lịch sử', '0900000000', 101)");
        jdbc.update("INSERT INTO audit_logs (admin_user_id, action, entity_type, entity_id, description) VALUES (101, 'CLOSE_PAYROLL', 'payroll_periods', '101', 'Đã chốt tháng 02/2025')");
        String closedDump = ReflectionTestUtils.invokeMethod(service, "buildDatabaseDump", LocalDateTime.now());
        Path closedArchive = directory.resolve("closed-payroll.ndbak");
        new EncryptedBackupCodec().encrypt(closedDump.getBytes(java.nio.charset.StandardCharsets.UTF_8), key, closedArchive);
        assertFalse(closedDump.contains("not-a-login-hash"), "Never archive a password hash");
        var closedResult = new StagedRestoreService(new EncryptedBackupCodec(), recovery, url, user, password)
                .prepare(closedArchive, null, user, password);
        var closedRestored = new JdbcTemplate(new DriverManagerDataSource(StagedRestoreService.stagingUrl(url, closedResult.databaseName()), user, password));
        assertEquals(101L, closedRestored.queryForObject("SELECT closed_by_admin_id FROM payroll_periods WHERE id = 101", Long.class));
        assertEquals("test-actor", closedRestored.queryForObject("SELECT historical_username FROM admin_users WHERE id = 101", String.class));
        assertEquals(false, closedRestored.queryForObject("SELECT is_active FROM admin_users WHERE id = 101", Boolean.class));
        assertNotEquals("test-actor", closedRestored.queryForObject("SELECT username FROM admin_users WHERE id = 101", String.class));
        assertThrows(org.springframework.dao.DataAccessException.class, () -> closedRestored.update("UPDATE admin_users SET is_active = TRUE WHERE id = 101"), "Historical identities can never become login accounts");
        assertEquals(101L, closedRestored.queryForObject("SELECT locked_by_admin_id FROM attendance_day_locks WHERE work_date = '2025-02-18'", Long.class));
        assertEquals(101L, closedRestored.queryForObject("SELECT contacted_by_admin_id FROM customer_leads WHERE phone = '0900000000'", Long.class));
        assertEquals(jdbc.queryForList("SELECT * FROM audit_logs"), closedRestored.queryForList("SELECT * FROM audit_logs"));
        String newMachineUrl = sourceUrl.replace(sourceName, "aluminum_test_" + UUID.randomUUID().toString().replace("-", ""));
        var newMachineSource = new DriverManagerDataSource(newMachineUrl, user, password);
        Flyway.configure().dataSource(newMachineSource).locations("classpath:db/migration").load().migrate();
        var newMachine = new JdbcTemplate(newMachineSource);
        newMachine.update("INSERT INTO admin_users (id, username, password_hash) VALUES (101, 'different-machine-owner', 'not-the-old-password')");
        var machineUsers = newMachine.queryForList("SELECT * FROM admin_users");
        var newMachineResult = new StagedRestoreService(new EncryptedBackupCodec(), recovery, newMachineUrl, user, password)
                .prepare(closedArchive, null, user, password);
        assertNotEquals(sourceName, newMachineResult.databaseName());
        assertEquals(machineUsers, newMachine.queryForList("SELECT * FROM admin_users"));
        String fixtureOutput = System.getenv("ALUMINUM_TEST_RECOVERY_PROFILE_OUT");
        if (fixtureOutput != null && !fixtureOutput.isBlank()) Files.writeString(Path.of(fixtureOutput), newMachineResult.profilePath());
        assertTrue(Files.readString(Path.of(newMachineResult.profilePath())).contains(newMachineResult.databaseName()));
        assertFalse(Files.readString(Path.of(newMachineResult.profilePath())).contains(password), "Verified recovery profiles do not store MySQL credentials");
    }
}


