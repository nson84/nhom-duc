package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Opt-in upgrade validation on a pre-imported private copy; refuses production ports/names. */
class ProductionCopyUpgradeIT {
    @Test void migratesAndValidatesCopiedHistoryWithoutChangingExistingBusinessValues() throws Exception {
        String url = System.getenv("ALUMINUM_UPGRADE_COPY_URL");
        assumeTrue(url != null);
        assertTrue(url.matches("jdbc:mysql://127\\.0\\.0\\.1:13308/aluminum_test_upgrade_[a-f0-9]{12}(?:\\?.*)?"), "Only an explicitly isolated upgrade copy is permitted");
        var source = new DriverManagerDataSource(url, System.getenv("ALUMINUM_UPGRADE_COPY_USERNAME"), System.getenv("ALUMINUM_UPGRADE_COPY_PASSWORD"));
        var jdbc = new JdbcTemplate(source);
        String beforeVersion = jdbc.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1", String.class);
        var tables = jdbc.queryForList("SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE' AND TABLE_NAME <> 'flyway_schema_history' ORDER BY TABLE_NAME", String.class);
        var columns = new LinkedHashMap<String, List<String>>();
        var before = new LinkedHashMap<String, List<String>>();
        for (String table : tables) {
            var names = jdbc.query("SHOW COLUMNS FROM " + quote(table), (rs, i) -> rs.getString("Field"));
            columns.put(table, names);
            before.put(table, rows(jdbc, table, names));
        }
        Integer inconsistentPay = jdbc.queryForObject("""
                SELECT COUNT(*) FROM payroll_details
                WHERE total_pay <> GREATEST(base_pay + overtime_pay + bonus_pay + allowances
                                            - deductions - advance_amount, 0)
                """, Integer.class);
        assertEquals(0, inconsistentPay, "Stop and reconcile historical pay; never rewrite it automatically");
        var flyway = Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
        int migrated = flyway.migrate().migrationsExecuted;
        assertTrue(flyway.validateWithResult().validationSuccessful);
        for (String table : tables) assertEquals(before.get(table), rows(jdbc, table, columns.get(table)), "Business history changed in " + table);
        assertEquals(0, flyway.migrate().migrationsExecuted, "A second upgrade is idempotent");
        Map<String, Object> report = Map.of("sourceSchema", beforeVersion,
                "targetSchema", flyway.info().current().getVersion().toString(),
                "migrationsAppliedToCopy", migrated, "tablesPreserved", tables.size(),
                "rowsPreserved", before.values().stream().mapToInt(List::size).sum(),
                "payrollPreflightMismatches", inconsistentPay, "flywayValid", true,
                "businessValuesUnchanged", true);
        String output = System.getenv("ALUMINUM_UPGRADE_COPY_REPORT");
        if (output != null) Files.writeString(Path.of(output), new tools.jackson.databind.ObjectMapper().writeValueAsString(report));
        System.out.println("UPGRADE COPY: schema " + beforeVersion + " -> " + flyway.info().current().getVersion()
                + ", tables preserved=" + tables.size() + ", all historical column values unchanged");
    }

    private static String quote(String name) { return "`" + name.replace("`", "``") + "`"; }

    private static List<String> rows(JdbcTemplate jdbc, String table, List<String> columns) {
        String select = columns.stream().map(ProductionCopyUpgradeIT::quote).collect(java.util.stream.Collectors.joining(","));
        return jdbc.query("SELECT " + select + " FROM " + quote(table), (ResultSetExtractor<List<String>>) rs -> {
            var hashes = new ArrayList<String>();
            while (rs.next()) {
                var row = new StringBuilder();
                for (int i = 1; i <= columns.size(); i++) {
                    Object value = rs.getObject(i);
                    if (value == null) { row.append("N;"); continue; }
                    String text = value instanceof byte[] bytes ? HexFormat.of().formatHex(bytes)
                            : value instanceof BigDecimal number ? number.toPlainString() : value.toString();
                    row.append(text.length()).append(':').append(text).append(';');
                }
                try { hashes.add(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(row.toString().getBytes(StandardCharsets.UTF_8)))); }
                catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
            }
            Collections.sort(hashes);
            return hashes;
        });
    }
}
