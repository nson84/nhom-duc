package com.aluminum.manager.attendance;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.math.BigDecimal;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Upgrade real V42 fixtures without rewriting any historical amount. */
class PayrollMigrationMysqlIT {
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "open,       OPEN,   400000, 0,     0,     0,     0,    0,     400000, true",
        "components, OPEN,   400000, 50000, 25000, 10000, 5000, 0,     480000, true",
        "closed,     CLOSED, 400000, 0,     0,     0,     0,    0,     400000, true",
        "paid,       CLOSED, 400000, 0,     0,     0,     0,    0,     400000, true",
        "advance,    OPEN,   400000, 50000, 0,     0,     0,    50000, 450000, false",
        "closedPaidAdvance, CLOSED, 400000, 50000, 25000, 10000, 5000, 50000, 480000, false"
    })
    void upgradesOrRejectsAtomicallyWithoutChangingHistory(String scenario, String status,
            BigDecimal base, BigDecimal overtime, BigDecimal bonus, BigDecimal allowance,
            BigDecimal deduction, BigDecimal advance, BigDecimal total, boolean compatible) {
        String testUrl = System.getenv("ALUMINUM_TEST_DB_URL");
        assumeTrue(testUrl != null && testUrl.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/aluminum_backup_test(?:\\?.*)?"));
        String user = System.getenv("ALUMINUM_TEST_DB_USERNAME"), password = System.getenv("ALUMINUM_TEST_DB_PASSWORD");
        assumeTrue(user != null && password != null);
        String url = testUrl.replace("/aluminum_backup_test", "/aluminum_test_" + UUID.randomUUID().toString().replace("-", ""));
        url += url.contains("?") ? "&createDatabaseIfNotExist=true" : "?createDatabaseIfNotExist=true";
        var source = new DriverManagerDataSource(url, user, password);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").target("42").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("INSERT INTO admin_users (id, username, password_hash) VALUES (101, 'fixture-author', 'not-a-login-hash')");
        jdbc.update("INSERT INTO employees (id, shop_id, employee_code, full_name) VALUES (101, 1, 'FIXTURE', 'Thợ thử')");
        jdbc.update("INSERT INTO payroll_periods (id, period_year, period_month, status, closed_at, closed_by_admin_id) VALUES (101, 2025, 2, ?, ?, ?)",
                status, "CLOSED".equals(status) ? "2025-02-28 18:00:00" : null, "CLOSED".equals(status) ? 101 : null);
        jdbc.update("INSERT INTO payroll_details (id, payroll_period_id, employee_id, base_pay, overtime_pay, bonus_pay, allowances, deductions, advance_amount, total_pay, created_at) VALUES (101, 101, 101, ?, ?, ?, ?, ?, ?, ?, '2025-02-28 17:00:00')",
                base, overtime, bonus, allowance, deduction, advance, total);
        if (scenario.contains("aid")) jdbc.update("INSERT INTO financial_transactions (transaction_code, transaction_date, transaction_type, category, amount, reference_type, reference_id) VALUES ('PAY-FIXTURE', '2025-02-28', 'EXPENSE', 'PAYROLL', ?, 'PAYROLL_PERIOD', 101)", total);
        var detailBefore = jdbc.queryForList("SELECT * FROM payroll_details");
        var periodBefore = jdbc.queryForList("SELECT * FROM payroll_periods");
        var paymentsBefore = jdbc.queryForList("SELECT * FROM financial_transactions");
        String checkBefore = jdbc.queryForObject("SELECT CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'chk_payroll_total'", String.class);
        var upgrade = Flyway.configure().dataSource(source).locations("classpath:db/migration").target("43").load();
        if (compatible) {
            upgrade.migrate();
            upgrade.validate();
            assertEquals("43", jdbc.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success = 1", String.class));
        } else {
            assertThrows(FlywayException.class, upgrade::migrate);
            assertEquals(checkBefore, jdbc.queryForObject("SELECT CHECK_CLAUSE FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'chk_payroll_total'", String.class), "Failed atomic ALTER must retain the old constraint");
            BigDecimal expected = base.add(overtime).add(bonus).add(allowance).subtract(deduction).subtract(advance).max(BigDecimal.ZERO);
            System.out.println("V43 PREFLIGHT " + scenario + ": payroll_detail_id=101, stored=" + total + ", formula=" + expected + ", difference=" + total.subtract(expected) + "; no historical row changed");
        }
        assertEquals(detailBefore, jdbc.queryForList("SELECT * FROM payroll_details"));
        assertEquals(periodBefore, jdbc.queryForList("SELECT * FROM payroll_periods"));
        assertEquals(paymentsBefore, jdbc.queryForList("SELECT * FROM financial_transactions"));
    }
}
