package com.aluminum.manager.backup;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.*;

import com.aluminum.manager.common.BusinessException;
import com.aluminum.manager.system.*;
import java.nio.file.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class BackupCapacityMysqlIT {
    @TempDir Path directory;
    @Test void nearLimitRestoresAndOverLimitRetainsLastValidArchiveWithoutUploadingFailure() throws Exception {
        String url = System.getenv("ALUMINUM_TEST_DB_URL");
        assumeTrue(url != null && url.matches("jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/aluminum_backup_test(?:\\?.*)?"));
        String user = System.getenv("ALUMINUM_TEST_DB_USERNAME"), password = System.getenv("ALUMINUM_TEST_DB_PASSWORD");
        assumeTrue(user != null && password != null);
        String sourceUrl = url.replace("/aluminum_backup_test", "/aluminum_test_" + UUID.randomUUID().toString().replace("-", ""));
        sourceUrl += sourceUrl.contains("?") ? "&createDatabaseIfNotExist=true" : "?createDatabaseIfNotExist=true";
        var source = new DriverManagerDataSource(sourceUrl, user, password);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        var jdbc = new JdbcTemplate(source);
        String payload = "x".repeat(64000);
        var batch = new ArrayList<Object[]>();
        for (int i = 0; i < 520; i++) batch.add(new Object[]{"capacity." + i, payload});
        jdbc.batchUpdate("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?)", batch);
        var settings = mock(SettingsService.class);
        when(settings.get()).thenReturn(new ShopSettings("Test", "", "", "23:00", 30, directory.resolve("backups").toString()));
        byte[] key = new byte[32]; Arrays.fill(key, (byte) 9);
        var recovery = mock(RecoveryKeyService.class); when(recovery.key()).thenReturn(key);
        var cloud = mock(CloudBackupService.class);
        var service = new BackupService(jdbc, source, settings, mock(AuditService.class), java.time.Clock.systemUTC(), recovery, new EncryptedBackupCodec(), mock(BackupFrequencyService.class), cloud);
        var pools = java.lang.management.ManagementFactory.getMemoryPoolMXBeans();
        pools.forEach(p -> p.resetPeakUsage());
        long started = System.nanoTime();
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
                new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(new com.aluminum.manager.security.AuthenticatedAdmin(1L, "test", false, com.aluminum.manager.auth.UserRole.ADMIN, null), null, List.of()));
        try {
            var backup = service.createManualBackup();
            assertEquals("SUCCESS", backup.status());
            Path archive = service.verifiedArchive(backup.id());
            byte[] original = Files.readAllBytes(archive);
            var staged = new StagedRestoreService(new EncryptedBackupCodec(), recovery, url, user, password).prepare(archive, null, user, password);
            var restored = new JdbcTemplate(new DriverManagerDataSource(StagedRestoreService.stagingUrl(url, staged.databaseName()), user, password));
            assertEquals(520, restored.queryForObject("SELECT COUNT(*) FROM app_settings WHERE setting_key LIKE 'capacity.%'", Integer.class));
            assertEquals(520L * 64000, restored.queryForObject("SELECT SUM(CHAR_LENGTH(setting_value)) FROM app_settings WHERE setting_key LIKE 'capacity.%'", Long.class));
            batch.clear();
            for (int i = 520; i < 540; i++) batch.add(new Object[]{"capacity." + i, payload});
            jdbc.batchUpdate("INSERT INTO app_settings (setting_key, setting_value) VALUES (?, ?)", batch);
            var failure = assertThrows(BusinessException.class, service::createManualBackup);
            assertTrue(failure.getMessage().contains("giới hạn"));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM backup_logs WHERE status = 'FAILED'", Integer.class));
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM backup_logs WHERE status = 'SUCCESS'", Integer.class));
            assertArrayEquals(original, Files.readAllBytes(archive));
            verify(cloud, times(1)).syncAfterBackup();
            try (var files = Files.list(directory.resolve("backups"))) { assertEquals(1, files.count()); }
            long heapPeak = pools.stream().filter(p -> p.getType() == java.lang.management.MemoryType.HEAP).mapToLong(p -> p.getPeakUsage().getUsed()).sum();
            System.out.println("CAPACITY: payload=33280000 chars; restored=520 rows; over-limit=34560000 chars; elapsedMs=" + ((System.nanoTime() - started) / 1_000_000) + "; sumHeapPoolPeaksBytes=" + heapPeak);
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }
}
